package com.quranreader.custom.data.audio.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.audio.AudioUrlResolver
import com.quranreader.custom.data.audio.Reciters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads all ayah-level MP3 files for a single (surah, reciter) pair to local disk
 * and writes a [DownloadedSurah] row on success.
 *
 * Layout on disk: `filesDir/audio/{reciterId}/{surahPadded}/{ayahPadded}.mp3`
 *
 * Resume support: each file uses an HTTP `Range` header when a partial file already exists.
 * Cancellation: WorkManager will cancel the coroutine; partial files for the canceled run
 * are deleted before returning.
 *
 * Progress: emitted as `Data` containing `KEY_BYTES_DOWNLOADED` and `KEY_TOTAL_BYTES`
 * (when the total is known) plus `KEY_AYAHS_DONE` and `KEY_AYAHS_TOTAL`.
 *
 * Input data:
 * - [KEY_RECITER_ID] (String, required)
 * - [KEY_SURAH] (Int, required, 1..114)
 *
 * Enqueue from app code via the unique-work-name pattern (one job per (surah, reciter)):
 * ```kotlin
 * val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
 *     .setInputData(workDataOf(
 *         AudioDownloadWorker.KEY_RECITER_ID to reciterId,
 *         AudioDownloadWorker.KEY_SURAH to surah
 *     ))
 *     .build()
 * WorkManager.getInstance(context)
 *     .enqueueUniqueWork(
 *         AudioDownloadWorker.uniqueName(reciterId, surah),
 *         ExistingWorkPolicy.KEEP,
 *         request
 *     )
 * ```
 */
@HiltWorker
class AudioDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val httpClient: OkHttpClient,
    private val downloadedDao: DownloadedSurahDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reciterId = inputData.getString(KEY_RECITER_ID)
            ?: return@withContext Result.failure(errorData("missing reciterId"))
        val surahNumber = inputData.getInt(KEY_SURAH, -1)
        if (surahNumber !in 1..114) {
            return@withContext Result.failure(errorData("invalid surah=$surahNumber"))
        }

        val reciter = Reciters.getReciter(reciterId)
        val ayahCount = QuranInfo.getAyahCount(surahNumber)
        if (ayahCount <= 0) {
            return@withContext Result.failure(errorData("surah=$surahNumber has zero ayahs in QuranInfo"))
        }

        val surahDir = surahDir(applicationContext, reciterId, surahNumber)
        if (!surahDir.exists() && !surahDir.mkdirs()) {
            return@withContext Result.failure(errorData("failed to create surah dir: ${surahDir.absolutePath}"))
        }

        var totalDownloaded = 0L
        var ayahsDone = 0

        try {
            for (ayah in 1..ayahCount) {
                ensureActive()
                val url = AudioUrlResolver.getAudioUrl(reciter, surahNumber, ayah)
                val outFile = ayahFile(surahDir, ayah)
                val sizeAfter = downloadOne(url, outFile)
                totalDownloaded += sizeAfter
                ayahsDone++
                setProgress(workDataOf(
                    KEY_BYTES_DOWNLOADED to totalDownloaded,
                    KEY_AYAHS_DONE to ayahsDone,
                    KEY_AYAHS_TOTAL to ayahCount
                ))
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // User canceled — clean up partial files and rethrow so WorkManager records cancellation
            cleanupSurahDir(surahDir)
            throw cancellation
        } catch (io: IOException) {
            Log.w(TAG, "Download failed for surah=$surahNumber reciter=$reciterId", io)
            cleanupSurahDir(surahDir)
            return@withContext Result.retry()
        }

        // Persist record
        downloadedDao.insert(
            DownloadedSurah(
                id = DownloadedSurah.idFor(reciterId, surahNumber),
                reciterId = reciterId,
                surahNumber = surahNumber,
                ayahCount = ayahCount,
                totalBytes = totalDownloaded,
                downloadedAt = System.currentTimeMillis()
            )
        )

        Result.success(workDataOf(
            KEY_BYTES_DOWNLOADED to totalDownloaded,
            KEY_AYAHS_DONE to ayahsDone,
            KEY_AYAHS_TOTAL to ayahCount
        ))
    }

    /**
     * Download a single MP3 to [outFile], using a `Range` header to resume from any
     * existing partial size. Returns the final on-disk size.
     */
    private suspend fun downloadOne(url: String, outFile: File): Long = coroutineScope {
        val partial = if (outFile.exists()) outFile.length() else 0L
        val requestBuilder = Request.Builder().url(url).get()
        if (partial > 0L) {
            requestBuilder.header("Range", "bytes=$partial-")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        try {
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Unexpected response ${response.code} for $url")
            }
            val body = response.body
                ?: throw IOException("Empty response body for $url")

            // 206 partial -> append; 200 OK -> overwrite from scratch
            val append = response.code == 206
            outFile.outputStream().use { sink ->
                if (append) {
                    // Re-open in append mode
                    sink.close()
                    java.io.FileOutputStream(outFile, true).use { appendSink ->
                        body.byteStream().use { it.copyTo(appendSink) }
                    }
                } else {
                    body.byteStream().use { it.copyTo(sink) }
                }
            }
        } finally {
            response.close()
        }

        outFile.length()
    }

    private fun cleanupSurahDir(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    private fun errorData(message: String): Data =
        workDataOf(KEY_ERROR to message)

    companion object {
        private const val TAG = "AudioDownloadWorker"

        const val KEY_RECITER_ID = "reciterId"
        const val KEY_SURAH = "surah"
        const val KEY_BYTES_DOWNLOADED = "bytesDownloaded"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_AYAHS_DONE = "ayahsDone"
        const val KEY_AYAHS_TOTAL = "ayahsTotal"
        const val KEY_ERROR = "error"

        /** WorkManager unique work name for one (reciter, surah) job. */
        fun uniqueName(reciterId: String, surahNumber: Int): String =
            "audio-download-$reciterId-$surahNumber"

        /** Directory holding all ayah files for a (reciter, surah) tuple. */
        fun surahDir(context: Context, reciterId: String, surahNumber: Int): File =
            File(context.filesDir, "audio/$reciterId/${pad3(surahNumber)}")

        /** File path for a specific ayah audio (3-digit padded ayah number). */
        fun ayahFile(surahDir: File, ayah: Int): File =
            File(surahDir, "${pad3(ayah)}.mp3")

        private fun pad3(n: Int): String = n.toString().padStart(3, '0')
    }
}
