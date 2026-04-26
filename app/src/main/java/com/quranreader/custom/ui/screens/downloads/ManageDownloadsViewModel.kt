package com.quranreader.custom.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.Reciters
import com.quranreader.custom.data.audio.download.AudioDownloadWorker
import com.quranreader.custom.data.audio.download.DownloadedSurah
import com.quranreader.custom.data.audio.download.DownloadedSurahDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state shown in [ManageDownloadsScreen].
 *
 * @property totalBytes sum of all downloaded surahs' bytes
 * @property byReciter rows grouped by reciter ID, value = display rows for that reciter
 */
data class ManageDownloadsUiState(
    val totalBytes: Long = 0L,
    val byReciter: Map<String, List<DownloadedSurahRow>> = emptyMap()
)

/** A single row in the Manage Downloads list. */
data class DownloadedSurahRow(
    val item: DownloadedSurah,
    val surahName: String,
    val reciterName: String
)

/**
 * View-model exposing manage-downloads state and providing delete actions.
 */
@HiltViewModel
class ManageDownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadedSurahDao
) : ViewModel() {

    val uiState: StateFlow<ManageDownloadsUiState> =
        combine(
            dao.observeAll(),
            dao.observeTotalBytes()
        ) { rows, totalBytes ->
            val grouped = rows.groupBy { it.reciterId }
                .mapValues { (_, items) ->
                    items.map { item ->
                        DownloadedSurahRow(
                            item = item,
                            surahName = QuranInfo.getSurahEnglishName(item.surahNumber),
                            reciterName = Reciters.getReciter(item.reciterId).name
                        )
                    }
                }
            ManageDownloadsUiState(totalBytes = totalBytes, byReciter = grouped)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ManageDownloadsUiState()
        )

    /**
     * Delete files on disk + DB row for a single surah.
     */
    fun deleteSurah(item: DownloadedSurah) {
        viewModelScope.launch {
            // Delete files
            val dir = AudioDownloadWorker.surahDir(context, item.reciterId, item.surahNumber)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
            // Delete DB row
            dao.delete(item)
        }
    }

    /**
     * Delete all downloaded surahs (files + DB rows).
     */
    fun deleteAll() {
        viewModelScope.launch {
            val all = dao.observeAll() // snapshot via current state
            // We use the cached uiState rows for accurate file deletion
            uiState.value.byReciter.values.flatten().forEach { row ->
                val dir = AudioDownloadWorker.surahDir(context, row.item.reciterId, row.item.surahNumber)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                    dir.delete()
                }
            }
            dao.deleteAll()
        }
    }
}
