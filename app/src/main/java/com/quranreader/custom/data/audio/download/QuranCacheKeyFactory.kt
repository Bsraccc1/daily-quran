package com.quranreader.custom.data.audio.download

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory

/**
 * Cache key factory for Quran audio files.
 *
 * Strategy: use the URL's last two path segments to build a stable, disk-friendly key.
 * Example: `https://everyayah.com/data/Abdul_Basit_Murattal_64kbps/001001.mp3`
 *  -> key: `Abdul_Basit_Murattal_64kbps/001001.mp3`
 *
 * This:
 *  - keeps keys stable across HTTP redirects within the same reciter
 *  - groups files by reciter for human-readable cache inspection
 *  - avoids collisions between same-named files across reciters (juz_3.mp3 etc.)
 */
@OptIn(UnstableApi::class)
class QuranCacheKeyFactory : CacheKeyFactory {

    override fun buildCacheKey(dataSpec: DataSpec): String {
        val uri = dataSpec.uri
        val pathSegments = uri.pathSegments
        return when {
            pathSegments.size >= 2 ->
                "${pathSegments[pathSegments.size - 2]}/${pathSegments.last()}"
            pathSegments.size == 1 -> pathSegments[0]
            else -> uri.toString()
        }
    }
}
