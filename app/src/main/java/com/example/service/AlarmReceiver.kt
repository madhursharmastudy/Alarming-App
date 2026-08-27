package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.data.AppDatabase
import com.example.data.ChallengeType
import com.example.ui.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_CHALLENGE_TYPE = "extra_challenge_type"
        const val EXTRA_REF_LABELS = "extra_ref_labels"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Wake Up!"
        val challengeType = intent.getStringExtra(EXTRA_CHALLENGE_TYPE) ?: ChallengeType.MATH.name
        val refLabels = intent.getStringExtra(EXTRA_REF_LABELS)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "ForceAlarm:AlarmReceiverWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L /* 10 minutes */)

        // Start AlarmRingingService
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(AlarmRingingService.EXTRA_CHALLENGE_TYPE, challengeType)
            putExtra(AlarmRingingService.EXTRA_REF_LABELS, refLabels)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Launch full-screen ringing activity
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(EXTRA_CHALLENGE_TYPE, challengeType)
            putExtra(EXTRA_REF_LABELS, refLabels)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(activityIntent)

        // Reschedule next occurrence if alarm is repeat
        if (alarmId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val alarm = db.alarmDao().getAlarmById(alarmId)
                if (alarm != null && alarm.isEnabled) {
                    if (alarm.daysOfWeek > 0) {
                        AlarmScheduler.scheduleAlarm(context, alarm)
                    } else {
                        // One-time alarm, disable after ring
                        db.alarmDao().updateAlarm(alarm.copy(isEnabled = false))
                    }
                }
            }
        }
    }
}
