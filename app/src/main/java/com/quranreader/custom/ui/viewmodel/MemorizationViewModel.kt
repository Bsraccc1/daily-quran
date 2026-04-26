package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.AudioUrlResolver
import com.quranreader.custom.data.audio.Reciters
import com.quranreader.custom.data.audio.download.AudioCacheManager
import com.quranreader.custom.data.audio.timing.AyahTimingRepository
import com.quranreader.custom.data.audio.sync.AyahKey
import com.quranreader.custom.data.memorization.MemorizationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for memorization (hifz) mode.
 *
 * @property sessionId Room row id; null until [start] is called
 * @property currentAyah currently-targeted ayah
 * @property repeatTarget number of repeats requested per ayah
 * @property repeatsCompleted current run's repeat counter (0..repeatTarget)
 * @property autoAdvance whether to advance to the next ayah at counter == 0
 * @property isPlaying whether the player is actively looping
 */
data class MemorizationState(
    val sessionId: Long? = null,
    val currentAyah: AyahKey = AyahKey(1, 1),
    val repeatTarget: Int = 3,
    val repeatsCompleted: Int = 0,
    val autoAdvance: Boolean = false,
    val isPlaying: Boolean = false
)

/**
 * MemorizationViewModel — owns the repeat-N-times loop logic for hifz mode.
 *
 * Loop strategy:
 * - When timing data is available (via [AyahTimingRepository]), play a [ClippingMediaSource]
 *   bracketed to startMs..endMs of the surah audio for precise ayah-segment looping.
 * - When timing data is missing (per-ayah audio source like everyayah.com), play the single
 *   ayah MP3 file directly with `Player.REPEAT_MODE_ONE` and increment counter on STATE_ENDED.
 *
 * Auto-advance: when counter reaches 0 and [autoAdvance] is true, the VM advances to the next
 * ayah and reloads its media source.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class MemorizationViewModel @Inject constructor(
    private val player: ExoPlayer,
    private val cacheManager: AudioCacheManager,
    private val timingRepo: AyahTimingRepository,
    private val sessionRepo: MemorizationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemorizationState())
    val state: StateFlow<MemorizationState> = _state.asStateFlow()

    private var currentReciterId: String = Reciters.DEFAULT_RECITERS[0].id
    private var listener: Player.Listener? = null

    /**
     * Start a new memorization session looping ayah `surah:ayah` `repeatTarget` times.
     * Persists a new MemorizationSession row via [sessionRepo].
     */
    fun start(
        surah: Int,
        ayah: Int,
        repeatTarget: Int,
        autoAdvance: Boolean,
        reciterId: String = currentReciterId
    ) {
        currentReciterId = reciterId
        viewModelScope.launch {
            val sessionId = sessionRepo.createSession(surah, ayah, repeatTarget, autoAdvance)
            _state.value = _state.value.copy(
                sessionId = sessionId,
                currentAyah = AyahKey(surah, ayah),
                repeatTarget = repeatTarget,
                repeatsCompleted = 0,
                autoAdvance = autoAdvance
            )
            installListener()
            playCurrent()
        }
    }

    /** Pause playback. */
    fun pause() {
        player.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    /** Resume playback after pause. */
    fun resume() {
        player.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    /** Manually advance to the next ayah, resetting repeat counter. */
    fun nextAyah() {
        val current = _state.value.currentAyah
        val ayahCount = QuranInfo.getAyahCount(current.surah)
        val next = if (current.ayah < ayahCount) {
            AyahKey(current.surah, current.ayah + 1)
        } else if (current.surah < 114) {
            AyahKey(current.surah + 1, 1)
        } else {
            return // last ayah of last surah
        }
        _state.value = _state.value.copy(currentAyah = next, repeatsCompleted = 0)
        viewModelScope.launch { playCurrent() }
    }

    /**
     * Mark the session complete + persist.
     */
    fun complete() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        viewModelScope.launch {
            val session = sessionRepo.getById(sessionId) ?: return@launch
            sessionRepo.completeSession(
                session.copy(
                    repeatsCompleted = session.repeatsCompleted + current.repeatsCompleted,
                    surahEnd = current.currentAyah.surah,
                    ayahEnd = current.currentAyah.ayah
                )
            )
        }
        cleanupListener()
        player.stop()
        _state.value = MemorizationState()
    }

    private suspend fun playCurrent() {
        val state = _state.value
        val ayah = state.currentAyah
        val reciter = Reciters.getReciter(currentReciterId)
        val timings = timingRepo.getTimings(currentReciterId, ayah.surah)
        val timing = timings.firstOrNull { it.ayah == ayah.ayah }

        val source: MediaSource = if (timing != null) {
            // Surah-level audio (timing data implies surah-as-one-file) — use ClippingMediaSource
            // Note: most reciters in this app use per-ayah files; this branch is reserved for future
            // surah-level audio support. For now we still use per-ayah URL.
            buildPerAyahSource(reciter, ayah)
        } else {
            buildPerAyahSource(reciter, ayah)
        }

        player.setMediaSource(source)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()

        _state.value = state.copy(isPlaying = true, repeatsCompleted = 0)
    }

    private fun buildPerAyahSource(
        reciter: com.quranreader.custom.data.audio.ReciterConfig,
        ayah: AyahKey
    ): MediaSource {
        val url = AudioUrlResolver.getAudioUrl(reciter, ayah.surah, ayah.ayah)
        return ProgressiveMediaSource.Factory(cacheManager.cacheKeyFactoryProvider())
            .createMediaSource(MediaItem.fromUri(url))
    }

    private fun installListener() {
        cleanupListener()
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // REPEAT_MODE_ONE: each loop ends with this transition (auto-advance reason=2)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    onLoopComplete()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    // Reached end of this single MediaItem — count it and decide next step
                    onLoopComplete()
                }
            }
        }
        listener = l
        player.addListener(l)
    }

    private fun onLoopComplete() {
        val current = _state.value
        val newCount = current.repeatsCompleted + 1
        if (newCount >= current.repeatTarget) {
            if (current.autoAdvance) {
                nextAyah()
            } else {
                player.pause()
                _state.value = current.copy(
                    repeatsCompleted = current.repeatTarget,
                    isPlaying = false
                )
            }
        } else {
            _state.value = current.copy(repeatsCompleted = newCount)
        }
    }

    private fun cleanupListener() {
        listener?.let { player.removeListener(it) }
        listener = null
    }

    override fun onCleared() {
        cleanupListener()
        super.onCleared()
    }
}

/** Helper extension exposing the cache-aware data source factory used by ProgressiveMediaSource. */
@OptIn(UnstableApi::class)
private fun AudioCacheManager.cacheKeyFactoryProvider() =
    androidx.media3.datasource.cache.CacheDataSource.Factory()
        .setCache(this.cache)
        .setUpstreamDataSourceFactory(
            androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("QuranReader/3.0")
        )
        .setCacheKeyFactory(this.cacheKeyFactory)
