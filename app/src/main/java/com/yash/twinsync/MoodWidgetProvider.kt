package com.yash.twinsync.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.yash.twinsync.R
import com.yash.twinsync.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MoodWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_SET_MOOD = "com.yash.twinsync.ACTION_SET_MOOD"
        private const val ACTION_REFRESH = "com.yash.twinsync.ACTION_REFRESH"
        const val EXTRA_MOOD = "EXTRA_MOOD"

        private val client = OkHttpClient()

        // Update widget UI for current user
        fun updateMoodWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, mood: String) {
            val views = RemoteViews(context.packageName, R.layout.mood_widget)
            views.setTextViewText(R.id.my_mood_value, mood)
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        // Update partner data from server
        fun updatePartnerData(context: Context, appWidgetManager: AppWidgetManager, componentName: ComponentName) {
            val token = TokenManager.getAccessToken(context) ?: return
            val request = Request.Builder()
                .url("https://twinsync.vercel.app/api/userdata/partner-data/")
                .header("accept", "application/json")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { body ->
                        val json = JSONObject(body)
                        val partnerMood = "Mood: ${json.optString("mood", "--")}"
                        val partnerBattery = "Battery: ${json.optInt("battery", 0)}%"
                        val partnerSteps = "Steps: ${json.optInt("steps", 0)}"
                        val partnerGps = "GPS: ${json.opt("gps_lat")}, ${json.opt("gps_lon")}"

                        val views = RemoteViews(context.packageName, R.layout.mood_widget)
                        views.setTextViewText(R.id.partner_mood, partnerMood)
                        views.setTextViewText(R.id.partner_battery, partnerBattery)
                        views.setTextViewText(R.id.partner_steps, partnerSteps)
                        views.setTextViewText(R.id.partner_gps, partnerGps)

                        appWidgetManager.updateAppWidget(componentName, views)
                    }
                }
            })
        }

        // POST user mood to server
        fun postUserMood(context: Context, mood: String) {
            val token = TokenManager.getAccessToken(context) ?: return
            val json = JSONObject().apply { put("mood", mood) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://twinsync.vercel.app/api/userdata/update/")
                .post(body)
                .header("accept", "application/json")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {}
            })
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.mood_widget)

            // Mood buttons
            val moods = mapOf(
                R.id.mood_happy to "😄 Happy",
                R.id.mood_sad to "😢 Sad",
                R.id.mood_excited to "🤩 Excited"
            )
            for ((buttonId, moodText) in moods) {
                val intent = Intent(context, MoodWidgetProvider::class.java).apply {
                    action = ACTION_SET_MOOD
                    putExtra(EXTRA_MOOD, moodText)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, buttonId, intent, PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(buttonId, pendingIntent)
            }

            // Refresh button
            val refreshIntent = Intent(context, MoodWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        // Auto-refresh partner data every 5 minutes
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val manager = AppWidgetManager.getInstance(context)
            updatePartnerData(context, manager, componentName)
        }, 5 * 60 * 1000L) // 5 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)

        when (intent.action) {
            ACTION_SET_MOOD -> {
                val mood = intent.getStringExtra(EXTRA_MOOD) ?: "--"

                // Update widget UI
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                for (id in ids) updateMoodWidget(context, appWidgetManager, id, mood)

                // Update server
                postUserMood(context, mood)
            }
            ACTION_REFRESH -> {
                updatePartnerData(context, appWidgetManager, componentName)
            }
        }
    }
}
