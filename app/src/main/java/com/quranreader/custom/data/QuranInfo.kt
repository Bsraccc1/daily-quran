package com.quranreader.custom.data

/**
 * Quran Information - Surah metadata
 * Complete data for all 114 surahs
 */
object QuranInfo {

    /**
     * Get surah name by number (1-114)
     */
    fun getSurahName(surahNumber: Int): String {
        return when (surahNumber) {
            1 -> "الفاتحة"
            2 -> "البقرة"
            3 -> "آل عمران"
            4 -> "النساء"
            5 -> "المائدة"
            6 -> "الأنعام"
            7 -> "الأعراف"
            8 -> "الأنفال"
            9 -> "التوبة"
            10 -> "يونس"
            11 -> "هود"
            12 -> "يوسف"
            13 -> "الرعد"
            14 -> "ابراهيم"
            15 -> "الحجر"
            16 -> "النحل"
            17 -> "الإسراء"
            18 -> "الكهف"
            19 -> "مريم"
            20 -> "طه"
            21 -> "الأنبياء"
            22 -> "الحج"
            23 -> "المؤمنون"
            24 -> "النور"
            25 -> "الفرقان"
            26 -> "الشعراء"
            27 -> "النمل"
            28 -> "القصص"
            29 -> "العنكبوت"
            30 -> "الروم"
            31 -> "لقمان"
            32 -> "السجدة"
            33 -> "الأحزاب"
            34 -> "سبإ"
            35 -> "فاطر"
            36 -> "يس"
            37 -> "الصافات"
            38 -> "ص"
            39 -> "الزمر"
            40 -> "غافر"
            41 -> "فصلت"
            42 -> "الشورى"
            43 -> "الزخرف"
            44 -> "الدخان"
            45 -> "الجاثية"
            46 -> "الأحقاف"
            47 -> "محمد"
            48 -> "الفتح"
            49 -> "الحجرات"
            50 -> "ق"
            51 -> "الذاريات"
            52 -> "الطور"
            53 -> "النجم"
            54 -> "القمر"
            55 -> "الرحمن"
            56 -> "الواقعة"
            57 -> "الحديد"
            58 -> "المجادلة"
            59 -> "الحشر"
            60 -> "الممتحنة"
            61 -> "الصف"
            62 -> "الجمعة"
            63 -> "المنافقون"
            64 -> "التغابن"
            65 -> "الطلاق"
            66 -> "التحريم"
            67 -> "الملك"
            68 -> "القلم"
            69 -> "الحاقة"
            70 -> "المعارج"
            71 -> "نوح"
            72 -> "الجن"
            73 -> "المزمل"
            74 -> "المدثر"
            75 -> "القيامة"
            76 -> "الانسان"
            77 -> "المرسلات"
            78 -> "النبإ"
            79 -> "النازعات"
            80 -> "عبس"
            81 -> "التكوير"
            82 -> "الإنفطار"
            83 -> "المطففين"
            84 -> "الإنشقاق"
            85 -> "البروج"
            86 -> "الطارق"
            87 -> "الأعلى"
            88 -> "الغاشية"
            89 -> "الفجر"
            90 -> "البلد"
            91 -> "الشمس"
            92 -> "الليل"
            93 -> "الضحى"
            94 -> "الشرح"
            95 -> "التين"
            96 -> "العلق"
            97 -> "القدر"
            98 -> "البينة"
            99 -> "الزلزلة"
            100 -> "العاديات"
            101 -> "القارعة"
            102 -> "التكاثر"
            103 -> "العصر"
            104 -> "الهمزة"
            105 -> "الفيل"
            106 -> "قريش"
            107 -> "الماعون"
            108 -> "الكوثر"
            109 -> "الكافرون"
            110 -> "النصر"
            111 -> "المسد"
            112 -> "الإخلاص"
            113 -> "الفلق"
            114 -> "الناس"
            else -> ""
        }
    }

    /**
     * Get surah English name by number (1-114)
     */
    fun getSurahEnglishName(surahNumber: Int): String {
        return when (surahNumber) {
            1 -> "Al-Fatihah"
            2 -> "Al-Baqarah"
            3 -> "Ali 'Imran"
            4 -> "An-Nisa"
            5 -> "Al-Ma'idah"
            6 -> "Al-An'am"
            7 -> "Al-A'raf"
            8 -> "Al-Anfal"
            9 -> "At-Tawbah"
            10 -> "Yunus"
            11 -> "Hud"
            12 -> "Yusuf"
            13 -> "Ar-Ra'd"
            14 -> "Ibrahim"
            15 -> "Al-Hijr"
            16 -> "An-Nahl"
            17 -> "Al-Isra"
            18 -> "Al-Kahf"
            19 -> "Maryam"
            20 -> "Taha"
            21 -> "Al-Anbya"
            22 -> "Al-Hajj"
            23 -> "Al-Mu'minun"
            24 -> "An-Nur"
            25 -> "Al-Furqan"
            26 -> "Ash-Shu'ara"
            27 -> "An-Naml"
            28 -> "Al-Qasas"
            29 -> "Al-'Ankabut"
            30 -> "Ar-Rum"
            31 -> "Luqman"
            32 -> "As-Sajdah"
            33 -> "Al-Ahzab"
            34 -> "Saba"
            35 -> "Fatir"
            36 -> "Ya-Sin"
            37 -> "As-Saffat"
            38 -> "Sad"
            39 -> "Az-Zumar"
            40 -> "Ghafir"
            41 -> "Fussilat"
            42 -> "Ash-Shuraa"
            43 -> "Az-Zukhruf"
            44 -> "Ad-Dukhan"
            45 -> "Al-Jathiyah"
            46 -> "Al-Ahqaf"
            47 -> "Muhammad"
            48 -> "Al-Fath"
            49 -> "Al-Hujurat"
            50 -> "Qaf"
            51 -> "Adh-Dhariyat"
            52 -> "At-Tur"
            53 -> "An-Najm"
            54 -> "Al-Qamar"
            55 -> "Ar-Rahman"
            56 -> "Al-Waqi'ah"
            57 -> "Al-Hadid"
            58 -> "Al-Mujadila"
            59 -> "Al-Hashr"
            60 -> "Al-Mumtahanah"
            61 -> "As-Saf"
            62 -> "Al-Jumu'ah"
            63 -> "Al-Munafiqun"
            64 -> "At-Taghabun"
            65 -> "At-Talaq"
            66 -> "At-Tahrim"
            67 -> "Al-Mulk"
            68 -> "Al-Qalam"
            69 -> "Al-Haqqah"
            70 -> "Al-Ma'arij"
            71 -> "Nuh"
            72 -> "Al-Jinn"
            73 -> "Al-Muzzammil"
            74 -> "Al-Muddaththir"
            75 -> "Al-Qiyamah"
            76 -> "Al-Insan"
            77 -> "Al-Mursalat"
            78 -> "An-Naba"
            79 -> "An-Nazi'at"
            80 -> "'Abasa"
            81 -> "At-Takwir"
            82 -> "Al-Infitar"
            83 -> "Al-Mutaffifin"
            84 -> "Al-Inshiqaq"
            85 -> "Al-Buruj"
            86 -> "At-Tariq"
            87 -> "Al-A'la"
            88 -> "Al-Ghashiyah"
            89 -> "Al-Fajr"
            90 -> "Al-Balad"
            91 -> "Ash-Shams"
            92 -> "Al-Layl"
            93 -> "Ad-Duhaa"
            94 -> "Ash-Sharh"
            95 -> "At-Tin"
            96 -> "Al-'Alaq"
            97 -> "Al-Qadr"
            98 -> "Al-Bayyinah"
            99 -> "Az-Zalzalah"
            100 -> "Al-'Adiyat"
            101 -> "Al-Qari'ah"
            102 -> "At-Takathur"
            103 -> "Al-'Asr"
            104 -> "Al-Humazah"
            105 -> "Al-Fil"
            106 -> "Quraysh"
            107 -> "Al-Ma'un"
            108 -> "Al-Kawthar"
            109 -> "Al-Kafirun"
            110 -> "An-Nasr"
            111 -> "Al-Masad"
            112 -> "Al-Ikhlas"
            113 -> "Al-Falaq"
            114 -> "An-Nas"
            else -> ""
        }
    }

    /**
     * Get ayah count for each surah
     */
    fun getAyahCount(surahNumber: Int): Int {
        return when (surahNumber) {
            1 -> 7
            2 -> 286
            3 -> 200
            4 -> 176
            5 -> 120
            6 -> 165
            7 -> 206
            8 -> 75
            9 -> 129
            10 -> 109
            11 -> 123
            12 -> 111
            13 -> 43
            14 -> 52
            15 -> 99
            16 -> 128
            17 -> 111
            18 -> 110
            19 -> 98
            20 -> 135
            21 -> 112
            22 -> 78
            23 -> 118
            24 -> 64
            25 -> 77
            26 -> 227
            27 -> 93
            28 -> 88
            29 -> 69
            30 -> 60
            31 -> 34
            32 -> 30
            33 -> 73
            34 -> 54
            35 -> 45
            36 -> 83
            37 -> 182
            38 -> 88
            39 -> 75
            40 -> 85
            41 -> 54
            42 -> 53
            43 -> 89
            44 -> 59
            45 -> 37
            46 -> 35
            47 -> 38
            48 -> 29
            49 -> 18
            50 -> 45
            51 -> 60
            52 -> 49
            53 -> 62
            54 -> 55
            55 -> 78
            56 -> 96
            57 -> 29
            58 -> 22
            59 -> 24
            60 -> 13
            61 -> 14
            62 -> 11
            63 -> 11
            64 -> 18
            65 -> 12
            66 -> 12
            67 -> 30
            68 -> 52
            69 -> 52
            70 -> 44
            71 -> 28
            72 -> 28
            73 -> 20
            74 -> 56
            75 -> 40
            76 -> 31
            77 -> 50
            78 -> 40
            79 -> 46
            80 -> 42
            81 -> 29
            82 -> 19
            83 -> 36
            84 -> 25
            85 -> 22
            86 -> 17
            87 -> 19
            88 -> 26
            89 -> 30
            90 -> 20
            91 -> 15
            92 -> 21
            93 -> 11
            94 -> 8
            95 -> 8
            96 -> 19
            97 -> 5
            98 -> 8
            99 -> 8
            100 -> 11
            101 -> 11
            102 -> 8
            103 -> 3
            104 -> 9
            105 -> 5
            106 -> 4
            107 -> 7
            108 -> 3
            109 -> 6
            110 -> 3
            111 -> 5
            112 -> 4
            113 -> 5
            114 -> 6
            else -> 0
        }
    }

    /**
     * Check if surah is Makki (true) or Madani (false)
     */
    fun isMakki(surahNumber: Int): Boolean {
        val madaniSurahs = setOf(2, 3, 4, 5, 8, 9, 13, 22, 24, 33, 47, 48, 49, 55, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 76, 98, 110)
        return !madaniSurahs.contains(surahNumber)
    }

    /**
     * Get start page for each surah
     */
    fun getStartPage(surahNumber: Int): Int {
        return when (surahNumber) {
            1 -> 1
            2 -> 2
            3 -> 50
            4 -> 77
            5 -> 106
            6 -> 128
            7 -> 151
            8 -> 177
            9 -> 187
            10 -> 208
            11 -> 221
            12 -> 235
            13 -> 249
            14 -> 255
            15 -> 262
            16 -> 267
            17 -> 282
            18 -> 293
            19 -> 305
            20 -> 312
            21 -> 322
            22 -> 332
            23 -> 342
            24 -> 350
            25 -> 359
            26 -> 367
            27 -> 377
            28 -> 385
            29 -> 396
            30 -> 404
            31 -> 411
            32 -> 415
            33 -> 418
            34 -> 428
            35 -> 434
            36 -> 440
            37 -> 446
            38 -> 453
            39 -> 458
            40 -> 467
            41 -> 477
            42 -> 483
            43 -> 489
            44 -> 496
            45 -> 499
            46 -> 502
            47 -> 507
            48 -> 511
            49 -> 515
            50 -> 518
            51 -> 520
            52 -> 523
            53 -> 526
            54 -> 528
            55 -> 531
            56 -> 534
            57 -> 537
            58 -> 542
            59 -> 545
            60 -> 549
            61 -> 551
            62 -> 553
            63 -> 554
            64 -> 556
            65 -> 558
            66 -> 560
            67 -> 562
            68 -> 564
            69 -> 566
            70 -> 568
            71 -> 570
            72 -> 572
            73 -> 574
            74 -> 575
            75 -> 577
            76 -> 578
            77 -> 580
            78 -> 582
            79 -> 583
            80 -> 585
            81 -> 586
            82 -> 587
            83 -> 587
            84 -> 589
            85 -> 590
            86 -> 591
            87 -> 591
            88 -> 592
            89 -> 593
            90 -> 594
            91 -> 595
            92 -> 595
            93 -> 596
            94 -> 596
            95 -> 597
            96 -> 597
            97 -> 598
            98 -> 598
            99 -> 599
            100 -> 599
            101 -> 600
            102 -> 600
            103 -> 601
            104 -> 601
            105 -> 601
            106 -> 602
            107 -> 602
            108 -> 602
            109 -> 603
            110 -> 603
            111 -> 603
            112 -> 604
            113 -> 604
            114 -> 604
            else -> 1
        }
    }

    /**
     * Returns the surah name (Arabic) that starts on or before the given page.
     */
    fun getSurahNameForPage(page: Int): String {
        var surahNum = 1
        for (i in 1..114) {
            if (getStartPage(i) <= page) surahNum = i
            else break
        }
        return getSurahName(surahNum)
    }

    /**
     * Returns the Juz number (1-30) for a given page.
     * Juz start pages sourced from standard Mushaf Madinah.
     */
    fun getJuzForPage(page: Int): Int {
        val juzStartPages = intArrayOf(
            1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
            201, 221, 241, 261, 281, 301, 321, 341, 361, 381,
            401, 421, 441, 461, 481, 501, 521, 542, 562, 582
        )
        var juz = 1
        for (i in juzStartPages.indices) {
            if (juzStartPages[i] <= page) juz = i + 1
            else break
        }
        return juz
    }

    // ── Juz boundaries by (surah, ayah) ─────────────────────────────────────
    // Standard Tanzil / Quran.com juz table. Index [j-1] holds the
    // first (surahNumber, ayahNumber) of juz `j`. Used by [juzOf] and
    // [juzAyahRanges] for the Translation reader (per-juz pager).
    private val juzStartSurahAyah: Array<Pair<Int, Int>> = arrayOf(
        1 to 1,    // Juz 1
        2 to 142,  // Juz 2
        2 to 253,  // Juz 3
        3 to 93,   // Juz 4
        4 to 24,   // Juz 5
        4 to 148,  // Juz 6
        5 to 82,   // Juz 7
        6 to 111,  // Juz 8
        7 to 88,   // Juz 9
        8 to 41,   // Juz 10
        9 to 93,   // Juz 11
        11 to 6,   // Juz 12
        12 to 53,  // Juz 13
        15 to 1,   // Juz 14
        17 to 1,   // Juz 15
        18 to 75,  // Juz 16
        21 to 1,   // Juz 17
        23 to 1,   // Juz 18
        25 to 21,  // Juz 19
        27 to 56,  // Juz 20
        29 to 46,  // Juz 21
        33 to 31,  // Juz 22
        36 to 28,  // Juz 23
        39 to 32,  // Juz 24
        41 to 47,  // Juz 25
        46 to 1,   // Juz 26
        51 to 31,  // Juz 27
        58 to 1,   // Juz 28
        67 to 1,   // Juz 29
        78 to 1,   // Juz 30
    )

    /**
     * Returns the Juz number (1..30) containing `(surah, ayah)`.
     * Linear scan — `juzStartSurahAyah` is only 30 entries, no need for
     * binary search.
     */
    fun juzOf(surah: Int, ayah: Int): Int {
        var juz = 1
        for (j in juzStartSurahAyah.indices) {
            val (s, a) = juzStartSurahAyah[j]
            // Juz j contains everything from juzStart[j] up to (and
            // excluding) juzStart[j+1]. Comparator: `(s, a) <= (surah,
            // ayah)` lexicographically by surah then ayah.
            if (s < surah || (s == surah && a <= ayah)) juz = j + 1
            else break
        }
        return juz
    }

    /**
     * Returns the contiguous (surah, fromAyah, toAyah) ranges that
     * make up the given juz. A juz can span multiple surahs — this
     * splits the juz into per-surah slices the Translation reader
     * iterates over to fetch ArabicVerse rows from Room.
     *
     * Example: juz 1 = `[(1, 1, 7), (2, 1, 141)]` (full Al-Fatihah +
     * Al-Baqarah verses 1..141).
     */
    fun juzAyahRanges(juz: Int): List<Triple<Int, Int, Int>> {
        if (juz < 1 || juz > 30) return emptyList()
        val (startSurah, startAyah) = juzStartSurahAyah[juz - 1]
        val (endSurah, endAyahExclusive) = if (juz == 30) {
            // Last juz ends at 114:6 (last verse of An-Nas). Encode as
            // exclusive end at (115, 1).
            115 to 1
        } else {
            juzStartSurahAyah[juz]
        }
        val out = ArrayList<Triple<Int, Int, Int>>()
        var s = startSurah
        var fromAyah = startAyah
        while (s < endSurah || (s == endSurah && fromAyah < endAyahExclusive)) {
            val toAyah = if (s == endSurah) endAyahExclusive - 1 else getAyahCount(s)
            out += Triple(s, fromAyah, toAyah)
            s += 1
            fromAyah = 1
        }
        return out
    }
}
