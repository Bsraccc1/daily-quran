package com.quranreader.custom.data

import com.quranreader.custom.data.model.HizbInfo
import com.quranreader.custom.data.model.JuzInfo
import com.quranreader.custom.data.model.SurahInfo

/**
 * Quran Navigation Data Provider
 * Contains all Juz, Surah, and Hizb information
 */
object QuranNavigationData {

    /**
     * All 30 Juz with page ranges
     */
    val juzList = listOf(
        JuzInfo(1, 1, 21),
        JuzInfo(2, 22, 41),
        JuzInfo(3, 42, 61),
        JuzInfo(4, 62, 81),
        JuzInfo(5, 82, 101),
        JuzInfo(6, 102, 121),
        JuzInfo(7, 122, 141),
        JuzInfo(8, 142, 161),
        JuzInfo(9, 162, 181),
        JuzInfo(10, 182, 201),
        JuzInfo(11, 202, 221),
        JuzInfo(12, 222, 241),
        JuzInfo(13, 242, 261),
        JuzInfo(14, 262, 281),
        JuzInfo(15, 282, 301),
        JuzInfo(16, 302, 321),
        JuzInfo(17, 322, 341),
        JuzInfo(18, 342, 361),
        JuzInfo(19, 362, 381),
        JuzInfo(20, 382, 401),
        JuzInfo(21, 402, 421),
        JuzInfo(22, 422, 441),
        JuzInfo(23, 442, 461),
        JuzInfo(24, 462, 481),
        JuzInfo(25, 482, 501),
        JuzInfo(26, 502, 521),
        JuzInfo(27, 522, 541),
        JuzInfo(28, 542, 561),
        JuzInfo(29, 562, 581),
        JuzInfo(30, 582, 604)
    )

    /**
     * All 114 Surahs with complete information
     */
    val surahList = (1..114).map { number ->
        SurahInfo(
            number = number,
            arabicName = QuranInfo.getSurahName(number),
            englishName = QuranInfo.getSurahEnglishName(number),
            ayahCount = QuranInfo.getAyahCount(number),
            startPage = QuranInfo.getStartPage(number),
            isMakki = QuranInfo.isMakki(number)
        )
    }

    /**
     * All 60 Hizb (240 quarters total)
     * Each Juz has 2 Hizb, each Hizb has 4 quarters
     * 30 Juz × 2 Hizb = 60 Hizb
     * 60 Hizb × 4 quarters = 240 quarters
     */
    val hizbList: List<HizbInfo> by lazy {
        buildHizbList()
    }

    private fun buildHizbList(): List<HizbInfo> {
        val hizbs = mutableListOf<HizbInfo>()
        
        // Hizb start pages (60 hizbs, 2 per juz)
        // Each hizb is approximately 10 pages
        val hizbStartPages = intArrayOf(
            // Juz 1
            1, 11,
            // Juz 2
            22, 32,
            // Juz 3
            42, 52,
            // Juz 4
            62, 72,
            // Juz 5
            82, 92,
            // Juz 6
            102, 112,
            // Juz 7
            122, 132,
            // Juz 8
            142, 152,
            // Juz 9
            162, 172,
            // Juz 10
            182, 192,
            // Juz 11
            202, 212,
            // Juz 12
            222, 232,
            // Juz 13
            242, 252,
            // Juz 14
            262, 272,
            // Juz 15
            282, 292,
            // Juz 16
            302, 312,
            // Juz 17
            322, 332,
            // Juz 18
            342, 352,
            // Juz 19
            362, 372,
            // Juz 20
            382, 392,
            // Juz 21
            402, 412,
            // Juz 22
            422, 432,
            // Juz 23
            442, 452,
            // Juz 24
            462, 472,
            // Juz 25
            482, 492,
            // Juz 26
            502, 512,
            // Juz 27
            522, 532,
            // Juz 28
            542, 552,
            // Juz 29
            562, 572,
            // Juz 30
            582, 593
        )

        for (i in hizbStartPages.indices) {
            val hizbNumber = i + 1
            val juzNumber = (i / 2) + 1
            val startPage = hizbStartPages[i]
            
            // Each hizb has 4 quarters
            // Approximate quarter pages (each quarter is ~2.5 pages)
            for (quarter in 1..4) {
                val quarterStartPage = when (quarter) {
                    1 -> startPage
                    2 -> startPage + 2
                    3 -> startPage + 5
                    4 -> startPage + 7
                    else -> startPage
                }.coerceAtMost(604)
                
                hizbs.add(
                    HizbInfo(
                        number = hizbNumber,
                        quarter = quarter,
                        startPage = quarterStartPage,
                        juzNumber = juzNumber
                    )
                )
            }
        }
        
        return hizbs
    }

    /**
     * Get Hizb number for a given page
     */
    fun getHizbForPage(page: Int): Int {
        return hizbList.indexOfLast { it.startPage <= page } / 4 + 1
    }

    /**
     * Get Quarter number within current Hizb for a given page
     */
    fun getQuarterForPage(page: Int): Int {
        val index = hizbList.indexOfLast { it.startPage <= page }
        return if (index >= 0) (index % 4) + 1 else 1
    }
}
