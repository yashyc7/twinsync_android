package com.yash.twinsync.worker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.yash.twinsync.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull  // ADDED: For timeout handling
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.yash.twinsync.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
// Add import at top of DeviceDataWorker.kt
import android.app.Service
import android.content.pm.ServiceInfo

class DeviceDataWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    companion object {
        private val client = OkHttpClient()
        private const val TAG = "DeviceDataWorker"
        private const val LOCATION_TIMEOUT = 15000L  // ADDED: 15 second timeout
        private const val LOCATION_MAX_AGE = 300000L  // ADDED: 5 minutes max age for cached location
    }

    override suspend fun doWork(): Result {

        setForeground(getForegroundInfo())
        // 1) Battery
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        Log.d(TAG, "Battery: $battery")

        // 3) GPS - UPDATED: Better error handling and logging
        val (lat, lon) = getLastKnownLocationSafe()
        Log.d(TAG, "GPS: lat=$lat, lon=$lon")

        // ADDED: Log if GPS data is missing
        if (lat == 0.0 && lon == 0.0) {
            Log.w(TAG, "GPS coordinates are 0,0 - location may not be available")
        }

        // 4) Build JSON
        val json = JSONObject().apply {
            put("battery", battery)
            put("gps_lat", lat)
            put("gps_lon", lon)
        }
        Log.d(TAG, "JSON => $json")

        // 5) Token
        val token = TokenManager.getAccessToken(applicationContext) ?: run {
            Log.e(TAG, "No token found")
            return Result.retry()
        }

        // 6) POST
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://twinsync.vercel.app/api/userdata/update/")
            .post(body)
            .header("accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    val txt = resp.body?.string()
                    Log.d(TAG, "Server response: $txt")
                }
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "POST failed: ${e.message}")
                Result.retry()
            }
        }
    }

    // DeviceDataWorker.kt - Update getForegroundInfo()
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "DeviceDataWorkerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TwinSync Background Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("TwinSync")
            .setContentText("Syncing device data…")
            .setSmallIcon(R.mipmap.updated_logo_round)
            .setOngoing(true)
            .build()

        // ✅ FIX: Specify service type explicitly
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            ForegroundInfo(1, notification)
        }
    }

    // UPDATED: Added background location permission check
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // ADDED: Check for background location permission (Android 10+)
        val backgroundLocation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }

        Log.d(TAG, "Permissions - Fine: $fine, Coarse: $coarse, Background: $backgroundLocation")
        return (fine || coarse) && backgroundLocation
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationSafe(): Pair<Double, Double> {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return 0.0 to 0.0
        }

        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)

        // UPDATED: Try last location with age check
        try {
            val last = fused.lastLocation.await()
            if (last != null) {
                val age = System.currentTimeMillis() - last.time
                Log.d(TAG, "Last known location age: ${age / 1000} seconds")

                // ADDED: Only use cached location if it's recent enough
                if (age < LOCATION_MAX_AGE) {
                    Log.d(TAG, "Using cached location")
                    return last.latitude to last.longitude
                } else {
                    Log.d(TAG, "Cached location too old, requesting fresh location")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get last location: ${e.message}")
        }

        // UPDATED: Request fresh location with timeout and high accuracy
        return try {
            Log.d(TAG, "Requesting fresh location...")

            // ADDED: Use withTimeoutOrNull for better timeout handling
            val location = withTimeoutOrNull(LOCATION_TIMEOUT) {
                val tokenSource = CancellationTokenSource()
                val task: Task<android.location.Location> = fused.getCurrentLocation(
                    // UPDATED: Changed to HIGH_ACCURACY for better results when screen is off
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    tokenSource.token
                )
                task.await()
            }

            if (location != null) {
                Log.d(TAG, "Fresh location obtained successfully")
                location.latitude to location.longitude
            } else {
                Log.w(TAG, "Fresh location request timed out or returned null")
                0.0 to 0.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation failed: ${e.message}")
            0.0 to 0.0
        }
    }
}