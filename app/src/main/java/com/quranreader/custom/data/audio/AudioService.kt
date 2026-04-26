package com.quranreader.custom.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.quranreader.custom.R
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.download.AudioCacheManager
import com.quranreader.custom.data.audio.sync.AyahKey
import com.quranreader.custom.data.audio.sync.HighlightSyncEngine
import com.quranreader.custom.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F-03, F-04 + v3.0 REQ-002: Audio Service for playing Quran recitation.
 *
 * Uses Media3 ExoPlayer with [AudioCacheManager]'s cache-aware MediaSource factory:
 * - Cache hit (already-downloaded ayah) -> plays from disk, zero network
 * - Cache miss -> streams from everyayah.com and populates cache concurrently
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class AudioService : LifecycleService() {

    @Inject
    lateinit var cacheManager: AudioCacheManager

    @Inject
    lateinit var highlightSyncEngine: HighlightSyncEngine

    private val syncScope = MainScope()


    private val binder = AudioBinder()
    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentAyah = MutableStateFlow<AyahInfo?>(null)
    val currentAyah: StateFlow<AyahInfo?> = _currentAyah.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private var currentPageAyahs: List<Pair<Int, Int>> = emptyList()
    private var currentReciter: ReciterConfig = Reciters.DEFAULT_RECITERS[0]
    private var _currentPage = 0

    // Callback for page advance
    var onPageComplete: ((Int) -> Unit)? = null

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initPlayer()
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(cacheManager.cacheAwareSourceFactory)
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> _isBuffering.value = true
                        Player.STATE_READY -> _isBuffering.value = false
                        Player.STATE_ENDED -> {
                            _isBuffering.value = false
                            onPlaylistEnded()
                        }
                        Player.STATE_IDLE -> _isBuffering.value = false
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playbackState.value = if (isPlaying) PlaybackState.Playing else {
                        if (exoPlayer?.playbackState == Player.STATE_ENDED) PlaybackState.Idle
                        else PlaybackState.Paused
                    }
                    if (isPlaying) {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val index = exoPlayer?.currentMediaItemIndex ?: return
                    if (index in currentPageAyahs.indices) {
                        val (surah, ayah) = currentPageAyahs[index]
                        _currentAyah.value = AyahInfo(surah, ayah)
                    }
                }
            })
        }
    }

    fun setReciter(reciter: ReciterConfig) {
        currentReciter = reciter
    }

    fun playPage(page: Int) {
        _currentPage = page
        currentPageAyahs = AudioUrlResolver.getAyahsForPage(page)
        if (currentPageAyahs.isEmpty()) return

        val urls = currentPageAyahs.map { (surah, ayah) ->
            AudioUrlResolver.getAudioUrl(currentReciter, surah, ayah)
        }

        exoPlayer?.let { player ->
            player.clearMediaItems()
            urls.forEach { url ->
                player.addMediaItem(MediaItem.fromUri(url))
            }
            player.prepare()
            player.play()

            // v3.0 REQ-005: attach HighlightSyncEngine for ayah-level visual sync
            val firstSurah = currentPageAyahs.firstOrNull()?.first
            if (firstSurah != null) {
                highlightSyncEngine.setMediaItemMapping(
                    currentPageAyahs.map { (s, a) -> AyahKey(s, a) }
                )
                syncScope.launch {
                    highlightSyncEngine.attach(
                        scope = syncScope,
                        player = player,
                        reciterId = currentReciter.id,
                        surahNumber = firstSurah
                    )
                }
            }
        }

        if (currentPageAyahs.isNotEmpty()) {
            val (s, a) = currentPageAyahs[0]
            _currentAyah.value = AyahInfo(s, a)
        }
        _playbackState.value = PlaybackState.Playing
    }

    fun playRange(surah: Int, fromAyah: Int, toAyah: Int, repeat: Int) {
        currentPageAyahs = (fromAyah..toAyah).map { Pair(surah, it) }

        val urls = currentPageAyahs.map { (s, a) ->
            AudioUrlResolver.getAudioUrl(currentReciter, s, a)
        }

        exoPlayer?.let { player ->
            player.clearMediaItems()
            urls.forEach { url ->
                player.addMediaItem(MediaItem.fromUri(url))
            }
            if (repeat == 0) player.repeatMode = Player.REPEAT_MODE_ALL
            else player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.play()
        }

        _playbackState.value = PlaybackState.Playing
    }

    private fun onPlaylistEnded() {
        _playbackState.value = PlaybackState.Idle
        _currentAyah.value = null
        // Notify page complete for auto-advance
        if (_currentPage > 0 && _currentPage < 604) {
            onPageComplete?.invoke(_currentPage + 1)
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _playbackState.value = PlaybackState.Idle
        _currentAyah.value = null
        _isBuffering.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quran Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quran audio recitation playback"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ayahInfo = _currentAyah.value
        val title = if (ayahInfo != null) {
            "${QuranInfo.getSurahEnglishName(ayahInfo.surah)} : ${ayahInfo.ayah}"
        } else "Quran Reader"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(currentReciter.name)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        highlightSyncEngine.detach()
        syncScope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "quran_audio_channel"
        const val NOTIFICATION_ID = 1001
    }
}

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    object Buffering : PlaybackState()
}

data class AyahInfo(
    val surah: Int,
    val ayah: Int
)
