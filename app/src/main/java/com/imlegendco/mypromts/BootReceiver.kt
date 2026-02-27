package com.imlegendco.mypromts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isNotificationEnabled = sharedPrefs.getBoolean("keyboard_notification_enabled", false)

            if (isNotificationEnabled) {
                KeyboardSwitcherService.start(context)
            }
        }
    }
}
