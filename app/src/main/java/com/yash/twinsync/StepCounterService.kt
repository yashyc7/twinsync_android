package com.yash.twinsync.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yash.twinsync.R

class StepCounterService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "steps_tracker_channel"
        private const val NOTIF_ID = 1001
        private const val TAG = "StepCounterService"

        fun start(context: Context) {
            val i = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    // prefs keys
    private val prefs by lazy { getSharedPreferences("steps_prefs", Context.MODE_PRIVATE) }
    private val KEY_BASE = "base_steps"
    private val KEY_LATEST = "latest_steps"
    private val KEY_DAY = "day_key" // yyyyMMdd string

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            Log.w(TAG, "TYPE_STEP_COUNTER not available on this device")
            stopSelf()
            return
        }

        sensorManager.registerListener(
            this,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        Log.d(TAG, "Step sensor listener registered")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val cumulative = event.values.firstOrNull()?.toLong() ?: return

        val today = android.text.format.DateFormat.format("yyyyMMdd", System.currentTimeMillis()).toString()
        val savedDay = prefs.getString(KEY_DAY, null)
        if (savedDay != today) {
            // New day: reset base to current cumulative
            prefs.edit()
                .putString(KEY_DAY, today)
                .putLong(KEY_BASE, cumulative)
                .putInt(KEY_LATEST, 0)
                .apply()
            Log.d(TAG, "New day, reset base=$cumulative")
        }

        val base = prefs.getLong(KEY_BASE, cumulative) // init base if first time
        val todaySteps = (cumulative - base).coerceAtLeast(0).toInt()
        prefs.edit().putInt(KEY_LATEST, todaySteps).apply()

        Log.d(TAG, "cumulative=$cumulative base=$base today=$todaySteps")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "TwinSync step tracking", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.main_logo) // or another small icon
            .setContentTitle("TwinSync is tracking steps")
            .setContentText("Keeping your step count up-to-date")
            .setOngoing(true)
            .build()
    }
}
