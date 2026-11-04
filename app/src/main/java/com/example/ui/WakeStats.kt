package com.example.ui

import android.content.Context
import java.util.TimeZone

/** Difficulty level: changes how much time each stage gives. */
enum class WakeLevel(val displayName: String, val timeMultiplier: Float, val hint: String) {
    EASY("Easy", 1.5f, "More time per stage"),
    NORMAL("Normal", 1.0f, "Standard time"),
    HARD("Hard", 0.8f, "Less time per stage");

    companion object {
        private const val PREFS = "force_alarm_stats"
        private const val KEY_LEVEL = "level"

        fun current(context: Context): WakeLevel {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LEVEL, NORMAL.name)
            return values().firstOrNull { it.name == name } ?: NORMAL
        }

        fun set(context: Context, level: WakeLevel) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LEVEL, level.name).apply()
        }
    }
}

data class WeekSummary(val daysWoke: Int, val averageSeconds: Int)

/** Stores how fast the user got up after each real alarm; gives streak and weekly summary. */
object WakeStats {
    private const val PREFS = "force_alarm_stats"
    private const val KEY_WAKES = "wakes"
    private const val MAX_RECORDS = 90
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun load(context: Context): List<Pair<Long, Int>> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WAKES, "") ?: ""
        return raw.split(",").mapNotNull {
            val parts = it.split(":")
            val time = parts.getOrNull(0)?.toLongOrNull()
            val seconds = parts.getOrNull(1)?.toIntOrNull()
            if (time != null && seconds != null) time to seconds else null
        }
    }

    fun recordWake(context: Context, seconds: Int) {
        val updated = (load(context) + (System.currentTimeMillis() to seconds)).takeLast(MAX_RECORDS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WAKES, updated.joinToString(",") { "${it.first}:${it.second}" })
            .apply()
    }

    private fun dayIndex(timeMs: Long): Long = (timeMs + TimeZone.getDefault().getOffset(timeMs)) / DAY_MS

    /** Number of days in a row (up to today) with at least one successful wake-up. */
    fun currentStreak(context: Context): Int {
        val days = load(context).map { dayIndex(it.first) }.toSet()
        if (days.isEmpty()) return 0
        val today = dayIndex(System.currentTimeMillis())
        var day = if (days.contains(today)) today else today - 1
        var streak = 0
        while (days.contains(day)) {
            streak++
            day--
        }
        return streak
    }

    fun weekSummary(context: Context): WeekSummary {
        val today = dayIndex(System.currentTimeMillis())
        val recent = load(context).filter { dayIndex(it.first) > today - 7 }
        if (recent.isEmpty()) return WeekSummary(0, 0)
        val days = recent.map { dayIndex(it.first) }.toSet().size
        return WeekSummary(days, recent.map { it.second }.average().toInt())
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
