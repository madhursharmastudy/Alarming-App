package com.example.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp

/** Limited emergency exit: stops the alarm without the challenge, at most twice per week. */
object EmergencyEscape {
    private const val PREFS = "force_alarm_escape"
    private const val KEY_TIMES = "escape_times"
    const val MAX_PER_WEEK = 2
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    private fun recentUses(context: Context): List<Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TIMES, "") ?: ""
        val cutoff = System.currentTimeMillis() - WEEK_MS
        return raw.split(",").mapNotNull { it.toLongOrNull() }.filter { it >= cutoff }
    }

    fun remaining(context: Context): Int = (MAX_PER_WEEK - recentUses(context).size).coerceAtLeast(0)

    fun record(context: Context) {
        val updated = recentUses(context) + System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TIMES, updated.joinToString(","))
            .apply()
    }
}

@Composable
fun EmergencyEscapeButton(
    modifier: Modifier = Modifier,
    onEscape: () -> Unit
) {
    val context = LocalContext.current
    var remaining by remember { mutableIntStateOf(EmergencyEscape.remaining(context)) }
    var showConfirm by remember { mutableStateOf(false) }

    TextButton(
        onClick = { if (remaining > 0) showConfirm = true },
        enabled = remaining > 0,
        modifier = modifier.testTag("emergency_escape_button")
    ) {
        Text(
            text = if (remaining > 0) {
                "Emergency exit ($remaining left)"
            } else {
                "No emergency exits left"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Emergency escape") },
            text = {
                Text("This stops the alarm without finishing the challenge. Use it only if you are unwell or something urgent happened. You have $remaining left this week.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    EmergencyEscape.record(context)
                    remaining = EmergencyEscape.remaining(context)
                    onEscape()
                }) { Text("Stop alarm") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Keep going") }
            }
        )
    }
}

/** TEMPORARY for testing: set SHOW_TEST_EXIT to false (or delete) before real use. */
object TestMode {
    const val SHOW_TEST_EXIT = true
}

@Composable
fun TestExitButton(
    modifier: Modifier = Modifier,
    onExit: () -> Unit
) {
    if (!TestMode.SHOW_TEST_EXIT) return
    OutlinedButton(
        onClick = onExit,
        modifier = modifier.testTag("test_exit_button")
    ) {
        Text("TEST: Exit puzzle", fontSize = 12.sp)
    }
}
