package com.quranreader.custom.data.audio.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton managing the on-disk audio cache used by [androidx.media3.exoplayer.ExoPlayer]
 * for offline playback.
 *
 * The cache shares a directory with [AudioDownloadWorker] outputs so that downloaded surahs
 * are immediately available to playback without re-fetching from the network.
 *
 * Layout:
 * ```
 * filesDir/audio/                        <- root (download files)
 *   abdul_basit_murattal/
 *     001/001.mp3, 001/002.mp3, ...      <- ayah files written by AudioDownloadWorker
 *   ...
 * filesDir/audio_cache/                  <- separate directory for ExoPlayer SimpleCache
 *                                           (it manages its own metadata + spans)
 * ```
 *
 * The cache uses [NoOpCacheEvictor] because we manage retention manually via the
 * Manage Downloads UI ([com.quranreader.custom.data.audio.download.DownloadedSurahDao]).
 *
 * Two factories are exposed:
 * - [cacheAwareSourceFactory]: the [MediaSource.Factory] to plug into `ExoPlayer.Builder.setMediaSourceFactory(...)`
 * - [cacheKeyFactory]: maps remote URLs to disk-friendly cache keys; used implicitly via [CacheDataSource.Factory.setCacheKeyFactory]
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheDir: File = File(context.filesDir, "audio_cache").apply { mkdirs() }

    /**
     * Lazy-initialized SimpleCache. NoOp evictor: Manage Downloads UI deletes manually.
     */
    val cache: SimpleCache by lazy {
        SimpleCache(
            cacheDir,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context)
        )
    }

    /**
     * Cache key factory mapping remote URLs (e.g. everyayah.com/.../001001.mp3) to a stable
     * key based on the URL path. Files written by the worker live elsewhere on disk
     * (see [AudioDownloadWorker.surahDir]), so the cache layer is for HTTP responses only —
     * the player will populate the cache as it streams un-downloaded ayahs.
     */
    val cacheKeyFactory = QuranCacheKeyFactory()

    /**
     * MediaSource.Factory wired to [cache] for transparent offline-first playback.
     *
     * Behavior:
     * - On cache hit: serves bytes from disk (zero network)
     * - On cache miss: streams from upstream HTTP, populates cache concurrently
     */
    val cacheAwareSourceFactory: MediaSource.Factory by lazy {
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("QuranReader/3.0")
            .setAllowCrossProtocolRedirects(true)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        DefaultMediaSourceFactory(context).setDataSourceFactory(cacheFactory)
    }

    /**
     * Release the underlying SimpleCache. Call from Application.onTerminate() or a lifecycle hook
     * if required; the cache is process-scoped and survives configuration changes.
     */
    fun release() {
        cache.release()
    }
}
