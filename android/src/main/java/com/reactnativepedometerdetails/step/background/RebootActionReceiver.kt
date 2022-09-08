package com.reactnativepedometerdetails.step.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat



class RebootActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }

        val action = intent.action
        action?.let {
            if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                val serviceIntent = Intent(context, StepCounterService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
