package com.quranreader.custom

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.seed.ArabicVerseSeeder
import com.quranreader.custom.utils.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class QuranReaderApp : Application(), WorkConfiguration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var arabicVerseSeeder: ArabicVerseSeeder

    /**
     * App-lifetime scope for fire-and-forget background work that
     * outlives any individual screen — currently the v10 Arabic
     * verse seeder. SupervisorJob keeps a seeder failure from
     * cascading. Per `android-coroutines` skill: tied to Application
     * onCreate, not GlobalScope.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Apply saved language on app start
        applyLocale()
        // Seed bundled Arabic Uthmani text into Room (v10). Idempotent:
        // a non-empty arabic_verses table short-circuits the read.
        seedArabicVerses()
    }

    private fun applyLocale() {
        try {
            val languageCode = runBlocking { userPreferences.appLanguage.first() }
            LocaleManager.applyLocale(this, languageCode)
        } catch (e: Exception) {
            // Ignore if preferences not available yet
        }
    }

    private fun seedArabicVerses() {
        applicationScope.launch {
            try {
                arabicVerseSeeder.seedFromAssets(this@QuranReaderApp)
            } catch (e: Exception) {
                // Translation reader will surface an empty state if
                // the seed fails — Mushaf reader keeps working
                // regardless. Swallowing is intentional.
            }
        }
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
