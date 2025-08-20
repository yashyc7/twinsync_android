package com.yash.twinsync.worker

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.yash.twinsync.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class DeviceDataWorker(private val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private val client = OkHttpClient()
        private const val TAG = "DeviceDataWorker"
    }

    override fun doWork(): Result {

        // --- 1. Fetch Battery ---
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        Log.d(TAG, "Battery Level: $batteryLevel")

        // --- 2. Fetch Steps ---
        // Replace this with your step counting logic, here using placeholder
        val steps = 300 // Example, replace with real step count
        Log.d(TAG, "Steps: $steps")

        // --- 3. Fetch GPS ---
        // Replace with real GPS fetching logic
        val gpsLat = -10.0 // Example
        val gpsLon = 35.0  // Example
        Log.d(TAG, "GPS: Lat=$gpsLat Lon=$gpsLon")

        // --- 4. Prepare JSON ---
        val json = JSONObject().apply {
            put("battery", batteryLevel)
            put("steps", steps)
            put("gps_lat", gpsLat)
            put("gps_lon", gpsLon)
        }
        Log.d(TAG, "Request JSON: ${json.toString()}")

        // --- 5. Get Token ---
        val token = TokenManager.getAccessToken(context) ?: return Result.retry()

        // --- 6. Create POST request ---
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://twinsync.vercel.app/api/userdata/update/")
            .post(body)
            .header("accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "POST Failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    Log.d(TAG, "Server Response: $body")
                }
            }
        })

        return Result.success()
    }
}
