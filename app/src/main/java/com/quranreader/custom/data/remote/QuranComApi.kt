package com.quranreader.custom.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * quran.com REST surface for translations + recitations.
 *
 * Translations
 *  - `GET /api/v4/resources/translations` — full catalogue of editions
 *    (id, language, author, slug). Used to populate the in-app picker
 *    so the user can choose any of the ~140 community translations.
 *  - `GET /api/v4/quran/translations/{translation_id}` — every ayah of
 *    the Quran translated by the given edition. Returned as flat
 *    list keyed by `verse_key` (e.g. "1:7"); we explode that back
 *    into (surah, ayah) on the persistence side.
 *
 * Recitations
 *  - `GET /api/v4/resources/recitations` — full catalogue of reciters
 *    (id, name, style). Like translations, lets the picker stay
 *    dynamic instead of pinning to a hand-curated short list.
 *
 * Audio URLs are not modelled here because they're already returned
 * inline by [AyahTimingApi] — extending its DTO with a [String] `url`
 * field is enough to drive the player off quran.com, no extra round-
 * trip needed.
 */
interface QuranComApi {

    /**
     * List every translation edition quran.com hosts. The response is
     * stable enough (low single-digit churn per year) that we cache
     * it locally and only refresh on user request.
     */
    @GET("api/v4/resources/translations")
    suspend fun listTranslations(): TranslationListResponse

    /**
     * Fetch every translated verse for [translationId]. The endpoint
     * doesn't paginate, so a single call returns ~6,236 records.
     * `fields` lets us request only `verse_key` + `text` to keep the
     * payload as small as possible.
     */
    @GET("api/v4/quran/translations/{translationId}")
    suspend fun fetchTranslation(
        @Path("translationId") translationId: Int,
        @Query("fields") fields: String = "verse_key,text"
    ): TranslationFetchResponse

    /**
     * List every recitation quran.com hosts. Powers the reciter
     * picker in Settings (and the bottom-sheet shortcut in the
     * reader).
     */
    @GET("api/v4/resources/recitations")
    suspend fun listRecitations(): RecitationListResponse
}

// ── Translations DTOs ────────────────────────────────────────────────────────

data class TranslationListResponse(
    @SerializedName("translations") val translations: List<TranslationEditionDto> = emptyList()
)

data class TranslationEditionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("language_name") val languageName: String? = null
)

data class TranslationFetchResponse(
    @SerializedName("translations") val translations: List<TranslationVerseDto> = emptyList(),
    @SerializedName("meta") val meta: TranslationFetchMeta? = null
)

data class TranslationVerseDto(
    @SerializedName("resource_id") val resourceId: Int? = null,
    @SerializedName("verse_key") val verseKey: String? = null,
    @SerializedName("text") val text: String
) {
    /** Parse "{surah}:{ayah}" → Pair<Int, Int>; null if malformed or missing. */
    fun parseSurahAyah(): Pair<Int, Int>? {
        val key = verseKey ?: return null
        val parts = key.split(':')
        if (parts.size != 2) return null
        val surah = parts[0].toIntOrNull() ?: return null
        val ayah = parts[1].toIntOrNull() ?: return null
        return surah to ayah
    }
}

data class TranslationFetchMeta(
    @SerializedName("translation_name") val translationName: String? = null,
    @SerializedName("author_name") val authorName: String? = null
)

// ── Recitations DTOs ─────────────────────────────────────────────────────────

data class RecitationListResponse(
    @SerializedName("recitations") val recitations: List<RecitationDto> = emptyList()
)

data class RecitationDto(
    @SerializedName("id") val id: Int,
    @SerializedName("reciter_name") val reciterName: String,
    @SerializedName("style") val style: String? = null,
    @SerializedName("translated_name") val translatedName: TranslatedNameDto? = null
) {
    /** English-friendly display name with optional style suffix ("Mishary Alafasy · Murattal"). */
    val displayName: String
        get() {
            val base = translatedName?.name ?: reciterName
            return if (style.isNullOrBlank()) base else "$base · $style"
        }
}

data class TranslatedNameDto(
    @SerializedName("name") val name: String,
    @SerializedName("language_name") val languageName: String? = null
)
