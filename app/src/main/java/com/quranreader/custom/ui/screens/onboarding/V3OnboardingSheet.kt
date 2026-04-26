package com.quranreader.custom.ui.screens.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.quranreader.custom.data.audio.Reciters
import com.quranreader.custom.data.audio.download.AudioDownloadWorker
import com.quranreader.custom.data.preferences.UserPreferences
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * One-time prompt shown after v3.0 update offering to pre-download common short surahs
 * for offline listening (REQ-016 / Task 28).
 *
 * Persists `v3_onboarding_shown` flag in DataStore so the prompt never re-appears.
 *
 * Surahs offered: Al-Fatihah (1) + last 10 short surahs (105-114).
 *
 * Place this composable at the root of MainActivity above NavGraph; it'll show itself
 * exactly once based on DataStore state.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface V3OnboardingEntryPoint {
    fun userPreferences(): UserPreferences
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3OnboardingSheet() {
    val context = LocalContext.current
    val prefs = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, V3OnboardingEntryPoint::class.java)
            .userPreferences()
    }
    val shown by prefs.v3OnboardingShown.collectAsStateWithLifecycle(initialValue = true)

    if (shown) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { prefs.setV3OnboardingShown(true) }
            dismissed = true
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Welcome to v3.0",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Download Al-Fatihah and the last 10 short surahs (105–114) so you can listen offline anytime — no internet needed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Total size: roughly 25 MB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ElevatedButton(
                onClick = {
                    enqueueRecommendedDownloads(context)
                    scope.launch { prefs.setV3OnboardingShown(true) }
                    dismissed = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download recommended surahs")
            }
            TextButton(
                onClick = {
                    scope.launch { prefs.setV3OnboardingShown(true) }
                    dismissed = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}

/**
 * Public helper to trigger recommended-surah pre-download from anywhere
 * (e.g. Settings → Downloads "Download recommended surahs" button).
 */
fun enqueueRecommendedDownloads(context: Context) {
    val reciterId = Reciters.DEFAULT_RECITERS[0].id
    val surahs = listOf(1) + (105..114)
    val workManager = WorkManager.getInstance(context)
    surahs.forEach { surah ->
        val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
            .setInputData(workDataOf(
                AudioDownloadWorker.KEY_RECITER_ID to reciterId,
                AudioDownloadWorker.KEY_SURAH to surah
            ))
            .build()
        workManager.enqueueUniqueWork(
            AudioDownloadWorker.uniqueName(reciterId, surah),
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
