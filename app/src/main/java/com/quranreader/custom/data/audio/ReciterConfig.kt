package com.quranreader.custom.data.audio

/**
 * Static reciter descriptor — name + audio URL pattern. URLs follow
 * the [everyayah.com] convention `{baseUrl}{surah3}{ayah3}.mp3` so a
 * single template covers every ayah in the Quran.
 *
 * The list intentionally stays small and hand-curated. The full
 * quran.com catalogue is mirrored separately via `RecitationRepository`
 * — that catalogue is for **discovery** (the picker can show every
 * reciter quran.com hosts) while this list is for **playback** (every
 * reciter here has a verified everyayah URL pattern that the
 * `AudioService` can hand straight to ExoPlayer with no extra round
 * trip). Adding a reciter here means committing to keeping its audio
 * URLs alive; that's why the legacy v1 list of three has only grown
 * to twelve, not three hundred.
 */
data class ReciterConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val formatPattern: String // e.g., "{surah3}{ayah3}.mp3" where surah3 = zero-padded to 3 digits
)

object Reciters {
    /**
     * Curated set of reciters known to ship verse-by-verse 64 kbps mp3
     * audio on everyayah.com. Order roughly follows global popularity
     * — Murattal recitations first, Mujawwad variants grouped after
     * their Murattal counterparts.
     */
    val DEFAULT_RECITERS = listOf(
        ReciterConfig(
            "abdul_basit_murattal",
            "Abdul Basit (Murattal)",
            "https://everyayah.com/data/Abdul_Basit_Murattal_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "abdul_basit_mujawwad",
            "Abdul Basit (Mujawwad)",
            "https://everyayah.com/data/Abdul_Basit_Mujawwad_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "mishary_alafasy",
            "Mishary Rashid Alafasy",
            "https://everyayah.com/data/Alafasy_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "abdurrahman_as_sudais",
            "Abdurrahman As-Sudais",
            "https://everyayah.com/data/Abdurrahmaan_As-Sudais_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "saud_al_shuraim",
            "Saud Al-Shuraim",
            "https://everyayah.com/data/Saood_ash-Shuraym_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "mahmoud_husary",
            "Mahmoud Al-Husary",
            "https://everyayah.com/data/Husary_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "minshawi_murattal",
            "Muhammad Siddiq Al-Minshawi (Murattal)",
            "https://everyayah.com/data/Minshawy_Murattal_128kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "maher_al_muaiqly",
            "Maher Al-Muaiqly",
            "https://everyayah.com/data/MaherAlMuaiqly128kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "saad_al_ghamdi",
            "Saad Al-Ghamdi",
            "https://everyayah.com/data/Ghamadi_40kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "ahmed_al_ajamy",
            "Ahmed Al-Ajamy",
            "https://everyayah.com/data/Ahmed_ibn_Ali_al-Ajamy_64kbps_QuranExplorer.Com/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "ali_al_hudhaify",
            "Ali Al-Hudhaify",
            "https://everyayah.com/data/Hudhaify_64kbps/",
            "{surah3}{ayah3}.mp3",
        ),
        ReciterConfig(
            "salah_bukhatir",
            "Salah Bukhatir",
            "https://everyayah.com/data/Salaah_AbdulRahman_Bukhatir_128kbps/",
            "{surah3}{ayah3}.mp3",
        ),
    )

    fun getReciter(id: String): ReciterConfig =
        DEFAULT_RECITERS.find { it.id == id } ?: DEFAULT_RECITERS[0]
}
