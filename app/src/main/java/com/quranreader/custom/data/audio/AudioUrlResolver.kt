package com.quranreader.custom.data.audio

import com.quranreader.custom.data.QuranInfo

object AudioUrlResolver {

    /**
     * Get list of (surah, ayah) pairs for a given page.
     * Uses surah start pages to estimate which ayahs are on the page.
     */
    fun getAyahsForPage(page: Int): List<Pair<Int, Int>> {
        // Find which surah(s) this page belongs to
        var currentSurah = 1
        for (s in 1..114) {
            if (QuranInfo.getStartPage(s) <= page) currentSurah = s
            else break
        }

        // Check if next surah starts on this page too
        val surahsOnPage = mutableListOf(currentSurah)
        if (currentSurah < 114 && QuranInfo.getStartPage(currentSurah + 1) == page) {
            surahsOnPage.add(currentSurah + 1)
        }

        val ayahs = mutableListOf<Pair<Int, Int>>()

        for (surah in surahsOnPage) {
            val surahStartPage = QuranInfo.getStartPage(surah)
            val nextSurahStartPage = if (surah < 114) QuranInfo.getStartPage(surah + 1) else 605
            val totalAyahs = QuranInfo.getAyahCount(surah)
            val pagesInSurah = nextSurahStartPage - surahStartPage

            if (pagesInSurah <= 0) {
                // Surah fits on one page, include all ayahs
                for (a in 1..totalAyahs) ayahs.add(Pair(surah, a))
            } else {
                // Estimate which ayahs are on this page
                val pageOffset = page - surahStartPage
                val ayahsPerPage = (totalAyahs.toFloat() / pagesInSurah).toInt().coerceAtLeast(1)
                val startAyah = (pageOffset * ayahsPerPage + 1).coerceIn(1, totalAyahs)
                val endAyah = ((pageOffset + 1) * ayahsPerPage).coerceIn(startAyah, totalAyahs)

                for (a in startAyah..endAyah) ayahs.add(Pair(surah, a))
            }
        }

        return ayahs
    }

    /**
     * Generate audio URL for a specific ayah using reciter config
     */
    fun getAudioUrl(reciter: ReciterConfig, surah: Int, ayah: Int): String {
        val filename = reciter.formatPattern
            .replace("{surah3}", surah.toString().padStart(3, '0'))
            .replace("{ayah3}", ayah.toString().padStart(3, '0'))
        return reciter.baseUrl + filename
    }

    /**
     * Get all audio URLs for a page
     */
    fun getAudioUrlsForPage(reciter: ReciterConfig, page: Int): List<String> {
        return getAyahsForPage(page).map { (surah, ayah) ->
            getAudioUrl(reciter, surah, ayah)
        }
    }
}
