package com.example.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Simple app-wide settings (volume, sound, vibration, strict mode), stored in SharedPreferences. */
object AppSettings {
    private const val PREFS = "force_alarm_settings"

    val soundNames = listOf("Default alarm", "Phone ringtone", "Soft chime")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun volumePercent(context: Context): Int = prefs(context).getInt("volume_percent", 80).coerceIn(40, 100)
    fun setVolumePercent(context: Context, value: Int) {
        prefs(context).edit().putInt("volume_percent", value.coerceIn(40, 100)).apply()
    }

    fun vibrationEnabled(context: Context): Boolean = prefs(context).getBoolean("vibration", true)
    fun setVibrationEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("vibration", value).apply()
    }

    fun soundChoice(context: Context): Int = prefs(context).getInt("sound", 0).coerceIn(0, soundNames.size - 1)
    fun setSoundChoice(context: Context, value: Int) {
        prefs(context).edit().putInt("sound", value).apply()
    }

    /** Strict mode hides the temporary "TEST: Exit puzzle" button. */
    fun strictMode(context: Context): Boolean = prefs(context).getBoolean("strict", false)
    fun setStrictMode(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("strict", value).apply()
    }

    /** Picks the sound the user chose, falling back to any available default sound. */
    fun soundUri(context: Context): Uri? {
        val order = when (soundChoice(context)) {
            1 -> listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION)
            2 -> listOf(RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE)
            else -> listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
        }
        for (type in order) {
            val uri = RingtoneManager.getDefaultUri(type)
            if (uri != null) return uri
        }
        return null
    }
}
