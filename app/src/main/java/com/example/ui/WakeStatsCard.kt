package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Home screen card: streak, this week's summary, and difficulty level. */
@Composable
fun WakeStatsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val refresh = remember { mutableIntStateOf(0) }
    var level by remember { mutableStateOf(WakeLevel.current(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.intValue = refresh.intValue + 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val streak = remember(refresh.intValue) { WakeStats.currentStreak(context) }
    val week = remember(refresh.intValue) { WakeStats.weekSummary(context) }

    val nextLevel = when {
        level == WakeLevel.EASY && streak >= 7 -> WakeLevel.NORMAL
        level == WakeLevel.NORMAL && streak >= 14 -> WakeLevel.HARD
        else -> null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wake_stats_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (streak > 0) "Streak: $streak day${if (streak == 1) "" else "s"}" else "Complete an alarm to start your streak",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (week.daysWoke > 0) {
                Text(
                    text = "This week: ${week.daysWoke}/7 days up, average ${WakeStats.formatDuration(week.averageSeconds)} to get up",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Level: ${level.hint}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WakeLevel.values().forEach { option ->
                    val onSelect = {
                        WakeLevel.set(context, option)
                        level = option
                    }
                    if (option == level) {
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                        ) { Text(option.displayName, fontSize = 12.sp) }
                    } else {
                        OutlinedButton(
                            onClick = onSelect,
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                        ) { Text(option.displayName, fontSize = 12.sp) }
                    }
                }
            }

            if (nextLevel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$streak-day streak! Ready for ${nextLevel.displayName}?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        WakeLevel.set(context, nextLevel)
                        level = nextLevel
                    }) { Text("Level up") }
                }
            }
        }
    }
}
