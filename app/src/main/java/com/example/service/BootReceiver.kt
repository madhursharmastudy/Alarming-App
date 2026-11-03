package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Re-schedules all enabled alarms after reboot, app update, or a time / timezone change.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // goAsync keeps the process alive until the database work below is finished
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val enabledAlarms = db.alarmDao().getEnabledAlarms().firstOrNull() ?: emptyList()
                    for (alarm in enabledAlarms) {
                        AlarmScheduler.scheduleAlarm(context.applicationContext, alarm)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
