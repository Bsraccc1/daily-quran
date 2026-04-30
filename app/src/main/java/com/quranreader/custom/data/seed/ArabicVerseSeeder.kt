package com.quranreader.custom.data.seed

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quranreader.custom.data.local.ArabicVerseDao
import com.quranreader.custom.data.model.ArabicVerse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** DTO for one entry in the bundled `quran_uthmani.json` array. */
private data class UthmaniEntry(val s: Int, val a: Int, val t: String)

/**
 * One-shot import of bundled Uthmani Arabic text from
 * `assets/quran_data/quran_uthmani.json` into the `arabic_verses` Room
 * table. Idempotent: a non-zero row count short-circuits the read so
 * subsequent launches don't re-parse the JSON.
 *
 * The asset-reading entry point hops to [Dispatchers.IO] internally
 * — the parser ([seedFromString]) is dispatcher-agnostic and is what
 * the unit tests cover, so the IO dispatcher does not need to be
 * injected (and adding a qualifier-tagged dispatcher just for one
 * call site would be over-engineering).
 */
@Singleton
class ArabicVerseSeeder @Inject constructor(
    private val dao: ArabicVerseDao,
) {

    /** App-launch entry point. Reads the bundled asset and seeds Room. */
    suspend fun seedFromAssets(context: Context) = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        seedFromString(json)
    }

    /**
     * Test entry point and shared parser. Idempotent vs row count —
     * exits early if Room already has any verse cached.
     */
    suspend fun seedFromString(json: String) {
        if (dao.count() > 0) return
        val type = object : TypeToken<List<UthmaniEntry>>() {}.type
        val entries: List<UthmaniEntry> = Gson().fromJson(json, type)
        val batch = ArrayList<ArabicVerse>(BATCH_SIZE)
        for (entry in entries) {
            batch += ArabicVerse(
                surahNumber = entry.s,
                ayahNumber = entry.a,
                textUthmani = entry.t,
            )
            if (batch.size >= BATCH_SIZE) {
                dao.insertAll(batch); batch.clear()
            }
        }
        if (batch.isNotEmpty()) dao.insertAll(batch)
    }

    companion object {
        private const val ASSET_PATH = "quran_data/quran_uthmani.json"
        private const val BATCH_SIZE = 200
    }
}
