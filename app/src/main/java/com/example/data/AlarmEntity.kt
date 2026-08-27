package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "Wake Up!",
    val isEnabled: Boolean = true,
    val daysOfWeek: Int = 127, // Bitmask: bit0=Sun, bit1=Mon, bit2=Tue, bit3=Wed, bit4=Thu, bit5=Fri, bit6=Sat
    val challengeType: ChallengeType = ChallengeType.MATH,
    val referencePhotoPath: String? = null,
    val referenceLabels: String? = null,
    val vibrate: Boolean = true,
    val escalatingVolume: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun formatTime(): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val m = String.format("%02d", minute)
        val amPm = if (hour < 12) "AM" else "PM"
        return "$h:$m $amPm"
    }

    fun daysFormatted(): String {
        if (daysOfWeek == 127) return "Every day"
        if (daysOfWeek == 62) return "Weekdays (Mon-Fri)"
        if (daysOfWeek == 65) return "Weekends (Sat-Sun)"
        if (daysOfWeek == 0) return "Once"
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val activeDays = mutableListOf<String>()
        for (i in 0..6) {
            if ((daysOfWeek and (1 shl i)) != 0) {
                activeDays.add(dayNames[i])
            }
        }
        return activeDays.joinToString(", ")
    }
}
