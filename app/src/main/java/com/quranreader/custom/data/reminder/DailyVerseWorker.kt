package com.quranreader.custom.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quranreader.custom.R
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.QuranInfo
import com.quranreader.custom.data.repository.TranslationRepository
import com.quranreader.custom.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DailyVerseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userPreferences: UserPreferences,
    private val translationRepository: TranslationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            // Pick a random ayah from the user's first downloaded
            // translation (falls back to the bundled English/Indonesian
            // edition IDs when nothing is downloaded yet).
            val lang = userPreferences.translationLanguage.first()
            val activeIds = userPreferences.activeTranslationIds.first()
            val downloadedIds = translationRepository.getDownloadedTranslationIds()
            val candidateIds = (activeIds.filter { it in downloadedIds })
                .ifEmpty { downloadedIds }
                .ifEmpty { if (lang == "id") listOf(33) else listOf(131) }
            val translation = candidateIds.firstNotNullOfOrNull {
                translationRepository.getRandomAyah(it)
            }
            
            if (translation == null) {
                return Result.success()
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "daily_verse_channel",
                    "Daily Verse",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily Quran verse notification"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val intent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(applicationContext, "daily_verse_channel")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Daily Verse")
                .setContentText("${QuranInfo.getSurahEnglishName(translation.surahNumber)} ${translation.surahNumber}:${translation.ayahNumber} - ${translation.text.take(100)}...")
                .setStyle(NotificationCompat.BigTextStyle().bigText(translation.text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(DAILY_VERSE_NOTIFICATION_ID, notification)
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
    
    companion object {
        const val DAILY_VERSE_NOTIFICATION_ID = 2001
    }
}
