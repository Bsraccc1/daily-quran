package com.quranreader.custom.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.io.File
import java.util.Locale

object LocaleManager {
    
    /**
     * Apply the saved language preference to the context
     */
    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
    
    /**
     * Read language preference from DataStore file directly (to avoid multiple DataStore instances)
     */
    fun readLanguagePreference(context: Context): String {
        return try {
            // Read directly from the preferences file to avoid DataStore singleton issue
            val prefsFile = File(context.filesDir, "datastore/user_preferences.preferences_pb")
            if (!prefsFile.exists()) {
                android.util.Log.d("LocaleManager", "Preferences file not found, using default")
                return "en"
            }
            
            // For now, use SharedPreferences as fallback
            // We'll read from a separate SharedPreferences file
            val sharedPrefs = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            val result = sharedPrefs.getString("language_code", "en") ?: "en"
            android.util.Log.d("LocaleManager", "readLanguagePreference from SharedPrefs: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("LocaleManager", "Error reading language preference", e)
            "en" // Default to English if reading fails
        }
    }
    
    /**
     * Save language preference to SharedPreferences (for quick access on app start)
     */
    fun saveLanguagePreference(context: Context, languageCode: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("language_code", languageCode).apply()
            android.util.Log.d("LocaleManager", "saveLanguagePreference: $languageCode")
        } catch (e: Exception) {
            android.util.Log.e("LocaleManager", "Error saving language preference", e)
        }
    }
    
    /**
     * Get the current locale from context
     */
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
    
    /**
     * Check if the current locale matches the saved preference
     */
    fun isLocaleApplied(context: Context, languageCode: String): Boolean {
        val currentLocale = getCurrentLocale(context)
        return currentLocale.language == languageCode
    }
}
