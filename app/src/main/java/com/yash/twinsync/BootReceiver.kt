package com.yash.twinsync.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "BOOT_COMPLETED received, starting StepCounterService")
        StepCounterService.start(context)
    }
}
