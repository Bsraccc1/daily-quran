package com.quranreader.custom.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.quranreader.custom.data.audio.download.AudioDownloadWorker
import com.quranreader.custom.data.audio.download.DownloadedSurah
import com.quranreader.custom.data.audio.download.DownloadedSurahDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map

/** Hilt EntryPoint to grab DownloadedSurahDao from a Composable scope. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SurahDownloadButtonEntryPoint {
    fun downloadedSurahDao(): DownloadedSurahDao
}

/**
 * Per-surah audio download button. Three visual states:
 *  - Idle (no DB row, no active work): outlined Download icon — tap enqueues
 *  - In-progress (active WorkInfo): CircularProgressIndicator (filled by ayah-done count) + Cancel overlay
 *  - Complete (DownloadedSurah row exists): filled CheckCircle (no-op tap)
 */
@Composable
fun SurahDownloadButton(
    reciterId: String,
    surahNumber: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, SurahDownloadButtonEntryPoint::class.java)
            .downloadedSurahDao()
    }
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val uniqueName = remember(reciterId, surahNumber) {
        AudioDownloadWorker.uniqueName(reciterId, surahNumber)
    }
    val rowId = remember(reciterId, surahNumber) {
        DownloadedSurah.idFor(reciterId, surahNumber)
    }

    // 1) Reactive: is the surah present in the downloads table?
    val isDownloaded by remember(rowId) {
        dao.observeAll().map { rows -> rows.any { it.id == rowId } }
    }.collectAsStateWithLifecycle(initialValue = false)

    // 2) Reactive: active work info for this unique-name
    val workInfos by workManager
        .getWorkInfosForUniqueWorkFlow(uniqueName)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeInfo = workInfos.firstOrNull { !it.state.isFinished }

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isDownloaded -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            activeInfo != null -> {
                val progress = activeInfo.progress
                val ayahsDone = progress.getInt(AudioDownloadWorker.KEY_AYAHS_DONE, 0)
                val ayahsTotal = progress.getInt(AudioDownloadWorker.KEY_AYAHS_TOTAL, 0)
                val fraction = if (ayahsTotal > 0) ayahsDone.toFloat() / ayahsTotal else null

                if (fraction != null) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
                IconButton(
                    onClick = { workManager.cancelUniqueWork(uniqueName) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel download"
                    )
                }
            }
            else -> {
                IconButton(onClick = {
                    val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                        .setInputData(workDataOf(
                            AudioDownloadWorker.KEY_RECITER_ID to reciterId,
                            AudioDownloadWorker.KEY_SURAH to surahNumber
                        ))
                        .build()
                    workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
                }) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download surah"
                    )
                }
            }
        }
    }
}
