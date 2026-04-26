package com.quranreader.custom

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.utils.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class QuranReaderApp : Application(), WorkConfiguration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate() {
        super.onCreate()
        // Apply saved language on app start
        applyLocale()
    }
    
    private fun applyLocale() {
        try {
            val languageCode = runBlocking { userPreferences.appLanguage.first() }
            LocaleManager.applyLocale(this, languageCode)
        } catch (e: Exception) {
            // Ignore if preferences not available yet
        }
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
