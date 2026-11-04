package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.ChallengeType
import com.example.ui.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentVolume = 0.1f
    private val maxVolume = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private var volumeEscalationRunnable: Runnable? = null
    private var backupRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "aurum_alarm_ringing_channel"
        const val NOTIFICATION_ID = 9991

        const val ACTION_START_RINGING = "com.example.service.ACTION_START_RINGING"
        const val ACTION_SILENCE_FOR_CHALLENGE = "com.example.service.ACTION_SILENCE_FOR_CHALLENGE"
        const val ACTION_RESUME_ALARM = "com.example.service.ACTION_RESUME_ALARM"
        const val ACTION_STOP_RINGING = "com.example.service.ACTION_STOP_RINGING"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_CHALLENGE_TYPE = "extra_challenge_type"
        const val EXTRA_REF_LABELS = "extra_ref_labels"

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var isSilenced = false
            private set

        @Volatile
        var currentAlarmId: Int = -1
            private set

        @Volatile
        var currentAlarmLabel: String = "Aurum Alarm"
            private set

        @Volatile
        var currentChallengeType: String = ChallengeType.MATH.name
            private set

        @Volatile
        var currentRefLabels: String? = null
            private set

        // Backup alarm: if the app/service is killed while the alarm is silenced for a
        // challenge, this backup fires again so the user cannot escape by killing the app.
        private const val BACKUP_HEARTBEAT_MS = 60_000L

        // True after a timeout/abandon penalty: alarm is loud again and cannot be silenced
        // for free (e.g. by reopening a challenge screen) until the one allowed re-silence is used.
        @Volatile
        var penaltyActive = false
            private set

        @Volatile
        private var reSilenceUsed = false

        // When the current ringing session started (used by the soft-wake brightness ramp)
        @Volatile
        var ringStartTime = 0L
            private set

        fun markRingStartIfNeeded() {
            if (ringStartTime == 0L) ringStartTime = System.currentTimeMillis()
        }

        fun silenceForChallenge(context: Context) {
            // No real alarm ringing (e.g. test run): nothing to silence, and never start the
            // service/notification just for that
            if (!isServiceRunning) return
            // Do not allow free silencing while a penalty is active
            if (penaltyActive) return
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_SILENCE_FOR_CHALLENGE
            }
            context.startService(intent)
        }

        /** The user may re-silence a penalised alarm only once per ringing session. */
        fun reSilenceOnce(context: Context): Boolean {
            if (!isServiceRunning) return true
            if (reSilenceUsed) return false
            reSilenceUsed = true
            penaltyActive = false
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_SILENCE_FOR_CHALLENGE
            }
            context.startService(intent)
            return true
        }

        fun resumeAlarm(context: Context) {
            if (!isServiceRunning) return
            penaltyActive = true
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_RESUME_ALARM
            }
            context.startService(intent)
        }

        fun stopRinging(context: Context) {
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_STOP_RINGING
            }
            context.startService(intent)
        }

        fun stopAlarm(context: Context) {
            stopRinging(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Never start or restore on null intents (prevents phantom sticky starts)
        if (intent == null || intent.action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent.action

        when (action) {
            ACTION_START_RINGING -> {
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)

                // If this is a real alarm ID (not a test preview), verify it is still enabled in DB
                if (alarmId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val alarm = db.alarmDao().getAlarmById(alarmId)
                        if (alarm == null || !alarm.isEnabled) {
                            // Alarm was deleted or disabled, do not ring!
                            AlarmScheduler.cancelBackup(applicationContext, alarmId)
                            stopSelf()
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(NOTIFICATION_ID)
                            return@launch
                        }
                    }
                }

                isServiceRunning = true
                isSilenced = false
                penaltyActive = false
                reSilenceUsed = false
                ringStartTime = System.currentTimeMillis()
                currentAlarmId = alarmId
                currentAlarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Aurum Alarm"
                currentChallengeType = intent.getStringExtra(EXTRA_CHALLENGE_TYPE) ?: ChallengeType.MATH.name
                currentRefLabels = intent.getStringExtra(EXTRA_REF_LABELS)

                startForeground(NOTIFICATION_ID, buildNotification(isSilenced = false))
                startAlarmMediaAndVibration(escalating = true)
                startBackupHeartbeat()
            }
            ACTION_SILENCE_FOR_CHALLENGE -> {
                isSilenced = true
                stopAlarmMediaAndVibration()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(isSilenced = true))
            }
            ACTION_RESUME_ALARM -> {
                isSilenced = false
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(isSilenced = false))
                startAlarmMediaAndVibration(escalating = false)
            }
            ACTION_STOP_RINGING -> {
                val finishedAlarmId = currentAlarmId
                isServiceRunning = false
                isSilenced = false
                penaltyActive = false
                reSilenceUsed = false
                ringStartTime = 0L
                stopBackupHeartbeat()
                if (finishedAlarmId > 0) {
                    AlarmScheduler.cancelBackup(applicationContext, finishedAlarmId)
                    // One-time alarms are switched off only after the user has really finished
                    // (not at ring time), so the ringing check and the backup alarm still work.
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = AppDatabase.getDatabase(applicationContext).alarmDao()
                        val alarm = dao.getAlarmById(finishedAlarmId)
                        if (alarm != null && alarm.daysOfWeek == 0 && alarm.isEnabled) {
                            dao.updateAlarm(alarm.copy(isEnabled = false))
                        }
                    }
                }
                stopAlarmMediaAndVibration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startBackupHeartbeat() {
        stopBackupHeartbeat()
        if (currentAlarmId <= 0) return
        val runnable = object : Runnable {
            override fun run() {
                if (!isServiceRunning) return
                AlarmScheduler.scheduleBackup(
                    applicationContext,
                    currentAlarmId,
                    currentAlarmLabel,
                    currentChallengeType,
                    currentRefLabels
                )
                handler.postDelayed(this, BACKUP_HEARTBEAT_MS)
            }
        }
        backupRunnable = runnable
        runnable.run()
    }

    private fun stopBackupHeartbeat() {
        backupRunnable?.let { handler.removeCallbacks(it) }
        backupRunnable = null
    }

    private fun ensureAlarmVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val minimum = (max * 0.7f).toInt().coerceAtLeast(1)
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < minimum) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minimum, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAlarmMediaAndVibration(escalating: Boolean) {
        stopAlarmMediaAndVibration()
        ensureAlarmVolume()

        try {
            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                currentVolume = if (escalating) 0.15f else maxVolume
                setVolume(currentVolume, currentVolume)
                prepare()
                start()
            }

            if (escalating) {
                volumeEscalationRunnable = object : Runnable {
                    override fun run() {
                        if (mediaPlayer != null && mediaPlayer?.isPlaying == true && currentVolume < maxVolume) {
                            currentVolume = (currentVolume + 0.05f).coerceAtMost(maxVolume)
                            mediaPlayer?.setVolume(currentVolume, currentVolume)
                            handler.postDelayed(this, 1500)
                        }
                    }
                }
                handler.postDelayed(volumeEscalationRunnable!!, 1500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start vibration
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400, 1200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmMediaAndVibration() {
        volumeEscalationRunnable?.let { handler.removeCallbacks(it) }
        volumeEscalationRunnable = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(isSilenced: Boolean): Notification {
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
            putExtra(EXTRA_ALARM_LABEL, currentAlarmLabel)
            putExtra(EXTRA_CHALLENGE_TYPE, currentChallengeType)
            putExtra(EXTRA_REF_LABELS, currentRefLabels)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSilenced) "Challenge in Progress..." else "Aurum Alarm Ringing!"
        val contentText = if (isSilenced) "Complete the challenge to stop the alarm" else "$currentAlarmLabel - Tap to start challenge"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            // Updates must not pop up / buzz again every time
            .setOnlyAlertOnce(true)

        // Only the first (ringing) notification may open the full-screen alarm
        if (!isSilenced) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aurum Alarm Ringing Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical full-screen alarm ringing notifications"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isSilenced = false
        penaltyActive = false
        reSilenceUsed = false
        stopBackupHeartbeat()
        if (currentAlarmId > 0) {
            // Normal stop: backup no longer needed. If the process is killed, onDestroy never
            // runs and the pending backup alarm fires again.
            AlarmScheduler.cancelBackup(applicationContext, currentAlarmId)
        }
        stopAlarmMediaAndVibration()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
