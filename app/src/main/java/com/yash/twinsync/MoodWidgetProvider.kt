package com.yash.twinsync.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.work.*
import com.yash.twinsync.R
import com.yash.twinsync.TokenManager
import com.yash.twinsync.worker.DeviceDataWorker
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MoodWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.yash.twinsync.ACTION_REFRESH"

        // HTTP Client with proper configuration
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateMoodWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            mood: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.mood_widget)
            val displayMood = if (mood.isNotEmpty()) mood else "--"
            views.setTextViewText(R.id.my_mood_value, displayMood)
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        fun updatePartnerData(
            context: Context,
            appWidgetManager: AppWidgetManager,
            componentName: ComponentName
        ) {
            val token = TokenManager.getAccessToken(context)
            if (token.isNullOrEmpty()) {
                updateWidgetWithError(context, appWidgetManager, componentName, "Not logged in")
                return
            }

            widgetScope.launch {
                try {
                    val response = fetchPartnerData(token)
                    if (response.isSuccessful) {
                        response.body?.string()?.let { body ->
                            parseAndUpdatePartnerData(context, appWidgetManager, componentName, body)
                        }
                    } else {
                        updateWidgetWithError(context, appWidgetManager, componentName, "Failed to load")
                    }
                } catch (e: Exception) {
                    updateWidgetWithError(context, appWidgetManager, componentName, "Connection error")
                }
            }
        }

        private suspend fun fetchPartnerData(token: String): Response = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://twinsync.vercel.app/api/userdata/partner-data/")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute()
        }

        private fun parseAndUpdatePartnerData(
            context: Context,
            appWidgetManager: AppWidgetManager,
            componentName: ComponentName,
            responseBody: String
        ) {
            try {
                val json = JSONObject(responseBody)
                val views = RemoteViews(context.packageName, R.layout.mood_widget)

                // Parse data with proper formatting
                val mood = json.optString("mood", "Unknown")
                val battery = json.optInt("battery", -1)
                val gpsLat = json.optDouble("gps_lat", Double.NaN)
                val gpsLon = json.optDouble("gps_lon", Double.NaN)
                val updatedAt = json.optString("updated_at", "")
                val selfMood=json.optString("self_mood","--")

                // ✅ Update My Mood section
                views.setTextViewText(R.id.my_mood_value, selfMood)

                // Format and set text views
                views.setTextViewText(R.id.partner_mood, "😊 $mood")

                val batteryText = if (battery >= 0) "🔋 ${battery}%" else "🔋 Unknown"
                views.setTextViewText(R.id.partner_battery, batteryText)

                // Handle GPS data
                if (!gpsLat.isNaN() && !gpsLon.isNaN()) {
                    views.setTextViewText(R.id.partner_gps, "📍 Location available")
                    setupGpsClickListener(context, views, gpsLat, gpsLon)
                } else {
                    views.setTextViewText(R.id.partner_gps, "📍 No location")
                }

                // Format timestamp
                val formattedTime = updatedAt
                views.setTextViewText(R.id.last_updated_on, "🕒 $formattedTime")

                appWidgetManager.updateAppWidget(componentName, views)

            } catch (e: JSONException) {
                updateWidgetWithError(context, appWidgetManager, componentName, "Data error")
            }
        }

        private fun setupGpsClickListener(
            context: Context,
            views: RemoteViews,
            lat: Double,
            lon: Double
        ) {
            try {
                val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt(),
                    mapIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                views.setOnClickPendingIntent(R.id.partner_gps, pendingIntent)
            } catch (e: Exception) {
                // GPS click setup failed, ignore
            }
        }

        private fun updateWidgetWithError(
            context: Context,
            appWidgetManager: AppWidgetManager,
            componentName: ComponentName,
            errorMessage: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.mood_widget)
            views.setTextViewText(R.id.partner_mood, "😊 --")
            views.setTextViewText(R.id.partner_battery, "🔋 --")
            views.setTextViewText(R.id.partner_gps, "📍 --")
            views.setTextViewText(R.id.last_updated_on, "🕒 $errorMessage")
            appWidgetManager.updateAppWidget(componentName, views)
        }

        fun postUserMood(context: Context, mood: String) {
            val token = TokenManager.getAccessToken(context) ?: return

            widgetScope.launch {
                try {
                    val json = JSONObject().apply { put("mood", mood.trim()) }
                    val body = json.toString().toRequestBody(
                        "application/json; charset=utf-8".toMediaTypeOrNull()
                    )

                    val request = Request.Builder()
                        .url("https://twinsync.vercel.app/api/userdata/update/")
                        .post(body)
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer $token")
                        .build()

                    client.newCall(request).execute().use { response ->
                        // Optional: Handle response if needed
                    }
                } catch (e: Exception) {
                    // Handle error silently or log it
                }
            }
        }

        fun schedulePeriodicRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<RefreshWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PartnerRefreshWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun scheduleDeviceDataWorker(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DeviceDataWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DeviceDataWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun runDeviceDataWorkerNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<DeviceDataWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)

        appWidgetIds.forEach { widgetId ->
            setupWidgetClickListeners(context, appWidgetManager, widgetId)
        }

        // Fetch partner data immediately
        updatePartnerData(context, appWidgetManager, componentName)

        // Schedule background tasks
        schedulePeriodicRefresh(context)
        scheduleDeviceDataWorker(context)
    }

    private fun setupWidgetClickListeners(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.mood_widget)

        // Update Mood button
        val updateIntent = Intent(context, UpdateMoodActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val updatePendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            updateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.update_mood_button, updatePendingIntent)

        // Refresh button
        val refreshIntent = Intent(context, MoodWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,widgetId+1000,
            refreshIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)

        // Partner Image Click
        val imageIntent = Intent(context, ViewPartnerImageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val imagePendingIntent = PendingIntent.getActivity(
            context,
            widgetId + 2000,
            imageIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.partner_image_click, imagePendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MoodWidgetProvider::class.java)

            // Refresh partner data
            updatePartnerData(context, appWidgetManager, componentName)

            // Send own data
            runDeviceDataWorkerNow(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel work when all widgets are removed
        WorkManager.getInstance(context).cancelUniqueWork("PartnerRefreshWork")
        WorkManager.getInstance(context).cancelUniqueWork("DeviceDataWorker")
    }
}

// Optimized RefreshWorker
class RefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MoodWidgetProvider::class.java)

        // Check if widgets are still active
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) {
            Result.success()
        } else {
            MoodWidgetProvider.updatePartnerData(context, appWidgetManager, componentName)
            Result.success()
        }
    } catch (e: Exception) {
        Result.retry()
    }
}