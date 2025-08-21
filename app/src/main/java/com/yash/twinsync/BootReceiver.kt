package com.yash.twinsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yash.twinsync.ui.MoodWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule both workers after reboot
            MoodWidgetProvider.schedulePeriodicRefresh(context)
            MoodWidgetProvider.scheduleDeviceDataWorker(context)
        }
    }
}
