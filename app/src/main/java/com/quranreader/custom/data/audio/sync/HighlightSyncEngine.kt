package com.quranreader.custom.data.audio.sync

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quranreader.custom.data.audio.timing.AyahTiming
import com.quranreader.custom.data.audio.timing.AyahTimingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes ExoPlayer playback position + AyahTiming list, emits the currently-active [AyahKey].
 *
 * REQ-005: drives the visual highlight overlay on the mushaf page.
 *
 * Two modes:
 * 1. **Timing mode** (preferred): when [AyahTimingRepository] returns a non-empty list for
 *    the (reciter, surah) tuple, the engine polls the player position at 100ms intervals and
 *    matches the offset to a timing entry to produce per-millisecond accurate ayah highlight.
 * 2. **MediaItem mode** (fallback): when timing data is unavailable (API failure, unmapped
 *    reciter), the engine listens to [Player.Listener.onMediaItemTransition] and emits one
 *    [AyahKey] per MediaItem. The audio service must store the mapping
 *    `MediaItem index -> AyahKey` via [setMediaItemMapping].
 *
 * Lifecycle:
 * - Call [attach] when audio playback starts (e.g. in AudioService.playPage / playRange).
 * - Call [detach] when playback stops (or service tears down).
 *
 * Thread safety: all listeners run on the main thread (ExoPlayer's default looper);
 * [currentAyahFlow] is a [StateFlow] safe to collect from any coroutine context.
 */
@Singleton
class HighlightSyncEngine @Inject constructor(
    private val timingRepo: AyahTimingRepository
) {
    private val _currentAyahFlow = MutableStateFlow<AyahKey?>(null)
    val currentAyahFlow: StateFlow<AyahKey?> = _currentAyahFlow.asStateFlow()

    private var player: ExoPlayer? = null
    private var pollJob: Job? = null
    private var listener: Player.Listener? = null
    private var timings: List<AyahTiming> = emptyList()
    private var mediaItemMapping: List<AyahKey> = emptyList()

    /**
     * Begin tracking. Loads timing data for (reciter, surah); if present, uses timing-mode polling.
     * Otherwise falls back to MediaItem-mode listener.
     *
     * @param scope coroutine scope owning the polling job (should outlive playback session)
     * @param player active [ExoPlayer]
     * @param reciterId for timing lookup
     * @param surahNumber for timing lookup
     */
    suspend fun attach(
        scope: CoroutineScope,
        player: ExoPlayer,
        reciterId: String,
        surahNumber: Int
    ) {
        detach() // clean previous attachment

        this.player = player
        this.timings = timingRepo.getTimings(reciterId, surahNumber)

        if (timings.isNotEmpty()) {
            startTimingModePolling(scope, player)
        } else {
            installMediaItemListener(player)
        }
    }

    /** Mapping used in MediaItem-mode (fallback). Set by AudioService when populating playlist. */
    fun setMediaItemMapping(mapping: List<AyahKey>) {
        this.mediaItemMapping = mapping
    }

    /** Stop tracking and reset internal state. */
    fun detach() {
        pollJob?.cancel()
        pollJob = null
        listener?.let { player?.removeListener(it) }
        listener = null
        player = null
        timings = emptyList()
        mediaItemMapping = emptyList()
        _currentAyahFlow.value = null
    }

    /**
     * Polls player position every 100ms (paused when player is paused) and
     * emits the matching [AyahKey] from [timings].
     */
    private fun startTimingModePolling(scope: CoroutineScope, player: ExoPlayer) {
        pollJob = scope.launch {
            while (true) {
                if (player.isPlaying) {
                    val positionMs = player.currentPosition
                    val current = timings.firstOrNull { positionMs in it.startMs..it.endMs }
                    if (current != null) {
                        val newKey = AyahKey(current.surah, current.ayah)
                        if (_currentAyahFlow.value != newKey) {
                            _currentAyahFlow.value = newKey
                        }
                    }
                }
                delay(100L)
            }
        }
    }

    /**
     * MediaItem-mode fallback: listen for transitions and emit [mediaItemMapping] entry.
     */
    private fun installMediaItemListener(player: ExoPlayer) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in mediaItemMapping.indices) {
                    _currentAyahFlow.value = mediaItemMapping[index]
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_IDLE) {
                    _currentAyahFlow.value = null
                }
            }
        }
        listener = l
        player.addListener(l)

        // Set initial value if player already has a current media item
        val initialIndex = player.currentMediaItemIndex
        if (initialIndex in mediaItemMapping.indices) {
            _currentAyahFlow.value = mediaItemMapping[initialIndex]
        }
    }
}
