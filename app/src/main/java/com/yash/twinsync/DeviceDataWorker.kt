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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DeviceDataWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    companion object {
        private val client = OkHttpClient()
        private const val TAG = "DeviceDataWorker"
    }

    override suspend fun doWork(): Result {
        // 1) Battery
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        Log.d(TAG, "Battery: $battery")

        // 2) Steps (from SharedPreferences set by StepCounterService)
        val prefs = applicationContext.getSharedPreferences("steps_prefs", Context.MODE_PRIVATE)
        val steps = prefs.getInt("latest_steps", 0)
        Log.d(TAG, "Steps (today): $steps")

        // 3) GPS
        val (lat, lon) = getLastKnownLocationSafe()
        Log.d(TAG, "GPS: lat=$lat, lon=$lon")

        // 4) Build JSON
        val json = JSONObject().apply {
            put("battery", battery)
            put("steps", steps)
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationSafe(): Pair<Double, Double> {
        if (!hasLocationPermission()) return 0.0 to 0.0

        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)

        // Try last location quickly
        try {
            val last = fused.lastLocation.await()
            if (last != null) return last.latitude to last.longitude
        } catch (_: Exception) {}

        // Fallback: request a fresh location (may still return null quickly)
        return try {
            val tokenSource = CancellationTokenSource()
            val task: Task<android.location.Location> =
                fused.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
            val loc = task.await()
            if (loc != null) loc.latitude to loc.longitude else 0.0 to 0.0
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed: ${e.message}")
            0.0 to 0.0
        }
    }
}
