package com.quranreader.custom.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.quranreader.custom.ui.navigation.QuranNavGraph
import com.quranreader.custom.ui.screens.onboarding.V3OnboardingSheet
import com.quranreader.custom.ui.theme.QuranReaderTheme
import com.quranreader.custom.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        // Read and apply language preference
        val languageCode = com.quranreader.custom.utils.LocaleManager.readLanguagePreference(newBase)
        android.util.Log.d("MainActivity", "attachBaseContext: languageCode = $languageCode")
        val context = com.quranreader.custom.utils.LocaleManager.applyLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeId by settingsViewModel.themeId.collectAsState()

            QuranReaderTheme(themeId = themeId) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuranNavGraph()
                    // v3.0 onboarding overlay (auto-dismisses if already shown)
                    V3OnboardingSheet()
                }
            }
        }
    }
    
    companion object {
        /**
         * Restart the app to apply language changes
         */
        fun restart(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (context is ComponentActivity) {
                context.finish()
            }
            Runtime.getRuntime().exit(0)
        }
    }
}
