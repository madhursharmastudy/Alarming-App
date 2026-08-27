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
import com.example.R
import com.example.data.ChallengeType
import com.example.ui.AlarmRingingActivity

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentVolume = 0.1f
    private val maxVolume = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private var volumeEscalationRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "force_alarm_ringing_channel"
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
        var currentAlarmLabel: String = "Wake Up!"
            private set

        @Volatile
        var currentChallengeType: String = ChallengeType.MATH.name
            private set

        @Volatile
        var currentRefLabels: String? = null
            private set

        fun silenceForChallenge(context: Context) {
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_SILENCE_FOR_CHALLENGE
            }
            context.startService(intent)
        }

        fun resumeAlarm(context: Context) {
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
        val action = intent?.action ?: ACTION_START_RINGING

        when (action) {
            ACTION_START_RINGING -> {
                isServiceRunning = true
                isSilenced = false
                currentAlarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: currentAlarmId
                currentAlarmLabel = intent?.getStringExtra(EXTRA_ALARM_LABEL) ?: currentAlarmLabel
                currentChallengeType = intent?.getStringExtra(EXTRA_CHALLENGE_TYPE) ?: currentChallengeType
                currentRefLabels = intent?.getStringExtra(EXTRA_REF_LABELS) ?: currentRefLabels

                startForeground(NOTIFICATION_ID, buildNotification(isSilenced = false))
                startAlarmMediaAndVibration(escalating = true)
            }
            ACTION_SILENCE_FOR_CHALLENGE -> {
                isSilenced = true
                stopAlarmMediaAndVibration()
                // Keep the foreground service alive to track state!
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(isSilenced = true))
            }
            ACTION_RESUME_ALARM -> {
                isSilenced = false
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(isSilenced = false))
                startAlarmMediaAndVibration(escalating = false) // Full volume immediately!
            }
            ACTION_STOP_RINGING -> {
                isServiceRunning = false
                isSilenced = false
                stopAlarmMediaAndVibration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startAlarmMediaAndVibration(escalating: Boolean) {
        stopAlarmMediaAndVibration()

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

        val title = if (isSilenced) "Challenge in Progress..." else "ForceAlarm Ringing!"
        val contentText = if (isSilenced) "Complete all 3 stages to stop the alarm" else "$currentAlarmLabel - Tap to start challenge"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ForceAlarm Ringing Channel",
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
        stopAlarmMediaAndVibration()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
