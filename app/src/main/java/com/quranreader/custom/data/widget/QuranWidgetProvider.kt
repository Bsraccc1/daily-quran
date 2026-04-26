package com.quranreader.custom.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.quranreader.custom.R
import com.quranreader.custom.ui.MainActivity

class QuranWidgetProvider : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quran)
            
            val lastPage = prefs.getInt("last_page", 1)
            val totalPagesRead = prefs.getInt("total_pages_read", 0)
            val sessionData = parseSessionData(prefs)
            val progressPercent = if (sessionData.targetPages > 0) {
                (sessionData.pagesRead.toFloat() / sessionData.targetPages * 100).toInt().coerceIn(0, 100)
            } else 0
            
            views.setTextViewText(R.id.widget_title, "Quran Reader")
            views.setTextViewText(R.id.widget_last_page, "Last Page: $lastPage")
            views.setTextViewText(R.id.widget_total_read, "Total Read: $totalPagesRead")
            
            if (progressPercent > 0) {
                views.setTextViewText(R.id.widget_session, "Session: $progressPercent%")
                views.setViewVisibility(R.id.widget_session, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_session, android.view.View.GONE)
            }
            
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun parseSessionData(prefs: SharedPreferences): SessionData {
        val sessionsRaw = prefs.getString("reading_sessions", "") ?: ""
        val activeId = prefs.getString("active_session_id", "")
        if (sessionsRaw.isBlank() || activeId.isNullOrBlank()) return SessionData(0, 0)
        return sessionsRaw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 7 && parts[0] == activeId) {
                try { SessionData(parts[4].toInt(), parts[3].toInt()) } catch (_: Exception) { null }
            } else null
        }.firstOrNull() ?: SessionData(0, 0)
    }
    
    data class SessionData(val pagesRead: Int, val targetPages: Int)
}
