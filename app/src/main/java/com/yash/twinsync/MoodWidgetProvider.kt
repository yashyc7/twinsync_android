package com.yash.twinsync.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import com.yash.twinsync.R
import com.yash.twinsync.TokenManager
import com.yash.twinsync.worker.DeviceDataWorker
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MoodWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.yash.twinsync.ACTION_REFRESH"
        private val client = OkHttpClient()

        // Update widget UI for current user mood
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

        // Schedule WorkManager to refresh partner data every 15 minutes
        fun schedulePeriodicRefresh(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<RefreshWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PartnerRefreshWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun scheduleDeviceDataWorker(context: Context) {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<DeviceDataWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DeviceDataWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.mood_widget)

            // Update Mood button opens UpdateMoodActivity
            val updateIntent = Intent(context, UpdateMoodActivity::class.java)
            val updatePendingIntent = PendingIntent.getActivity(context, 0, updateIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.update_mood_button, updatePendingIntent)

            // Refresh button
            val refreshIntent = Intent(context, MoodWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)

            // Fetch partner data immediately when widget loads
            updatePartnerData(context, appWidgetManager, componentName)
        }

        // Start periodic background refresh
        schedulePeriodicRefresh(context)
        //send own data to server
        scheduleDeviceDataWorker(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)

        if (intent.action == ACTION_REFRESH) {
            updatePartnerData(context, appWidgetManager, componentName)
        }
    }
}

// Worker class to refresh partner data in background
class RefreshWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)
        MoodWidgetProvider.updatePartnerData(context, appWidgetManager, componentName)
        return Result.success()
    }
}
