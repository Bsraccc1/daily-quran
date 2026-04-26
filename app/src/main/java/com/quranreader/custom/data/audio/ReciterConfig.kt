package com.quranreader.custom.data.audio

data class ReciterConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val formatPattern: String // e.g., "{surah3}{ayah3}.mp3" where surah3 = zero-padded to 3 digits
)

object Reciters {
    val DEFAULT_RECITERS = listOf(
        ReciterConfig(
            "abdul_basit_murattal",
            "Abdul Basit (Murattal)",
            "https://everyayah.com/data/Abdul_Basit_Murattal_64kbps/",
            "{surah3}{ayah3}.mp3"
        ),
        ReciterConfig(
            "mishary_alafasy",
            "Mishary Rashid Alafasy",
            "https://everyayah.com/data/Alafasy_64kbps/",
            "{surah3}{ayah3}.mp3"
        ),
        ReciterConfig(
            "abdurrahman_as_sudais",
            "Abdurrahman As-Sudais",
            "https://everyayah.com/data/Abdurrahmaan_As-Sudais_64kbps/",
            "{surah3}{ayah3}.mp3"
        )
    )

    fun getReciter(id: String): ReciterConfig =
        DEFAULT_RECITERS.find { it.id == id } ?: DEFAULT_RECITERS[0]
}
