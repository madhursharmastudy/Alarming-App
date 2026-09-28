package com.example.challenge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChargerRequiredAction {
    UNPLUG,
    PLUG_IN
}

/**
 * Single-stage Charger Challenge.
 * Senses the phone's charging state at start.
 * If currently charging -> User must unplug the charger.
 * If currently on battery (not charging) -> User must plug in the charger.
 * Completes immediately when the state transitions.
 */
class ChargerChallengeActivity : BaseChallengeActivity() {

    private val requiredActionState = MutableStateFlow(ChargerRequiredAction.PLUG_IN)
    val requiredAction = requiredActionState.asStateFlow()

    private val currentChargingState = MutableStateFlow(false)
    val isCharging = currentChargingState.asStateFlow()

    private val batteryLevelState = MutableStateFlow(50)
    val batteryLevel = batteryLevelState.asStateFlow()

    private var powerReceiver: BroadcastReceiver? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.CHARGER

    // Charger challenge is a single stage action (no levels)
    override fun getTotalStages(): Int = 1

    override fun getStageTimeLimit(stage: Int): Int = 60 // 60 seconds time limit

    override fun onStageStarted(stage: Int) {
        checkInitialPowerState()
        registerPowerReceiver()
    }

    override fun onResetToStage1() {
        checkInitialPowerState()
    }

    private fun checkInitialPowerState() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isNowCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level != -1 && scale != -1) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 50
        batteryLevelState.value = pct

        currentChargingState.value = isNowCharging

        // At alarm start: If charging -> must unplug. If not charging -> must plug in.
        requiredActionState.value = if (isNowCharging) {
            ChargerRequiredAction.UNPLUG
        } else {
            ChargerRequiredAction.PLUG_IN
        }
    }

    private fun registerPowerReceiver() {
        if (powerReceiver != null) return

        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        currentChargingState.value = true
                        onPowerStateChanged(isNowCharging = true)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        currentChargingState.value = false
                        onPowerStateChanged(isNowCharging = false)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)
    }

    private fun onPowerStateChanged(isNowCharging: Boolean) {
        val action = requiredActionState.value
        val satisfied = (action == ChargerRequiredAction.UNPLUG && !isNowCharging) ||
                (action == ChargerRequiredAction.PLUG_IN && isNowCharging)

        if (satisfied) {
            Toast.makeText(this, "Charger state verified! Success!", Toast.LENGTH_SHORT).show()
            completeCurrentStage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        powerReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        powerReceiver = null
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val action by requiredAction.collectAsState()
        val charging by isCharging.collectAsState()
        val level by batteryLevel.collectAsState()

        ChargerChallengeScreen(
            requiredAction = action,
            isCharging = charging,
            batteryPct = level,
            modifier = modifier
        )
    }
}

@Composable
fun ChargerChallengeScreen(
    requiredAction: ChargerRequiredAction,
    isCharging: Boolean,
    batteryPct: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chargerPulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Card Top
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = "Battery Status",
                        tint = if (isCharging) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "DEVICE BATTERY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isCharging) "Charging ($batteryPct%)" else "On Battery ($batteryPct%)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Mode badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCharging) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isCharging) "CONNECTED" else "UNPLUGGED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCharging) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Center Pulsing Graphic & Giant Instruction Banner
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val isActionUnplug = requiredAction == ChargerRequiredAction.UNPLUG
            val themeColor = if (isActionUnplug) Color(0xFFEF4444) else Color(0xFF10B981)

            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                themeColor.copy(alpha = 0.35f),
                                themeColor.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(4.dp, themeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActionUnplug) Icons.Default.PowerOff else Icons.Default.Power,
                    contentDescription = "Charger Action",
                    tint = themeColor,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Card (Sleep-Inertia Friendly Giant Text)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("charger_action_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(themeColor)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REQUIRED PHYSICAL ACTION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = themeColor,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isActionUnplug) "UNPLUG THE CHARGER" else "PLUG IN THE CHARGER",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isActionUnplug) {
                            "Your phone was charging when the alarm rang. Unplug the cable from your phone to stop the alarm."
                        } else {
                            "Your phone is currently not charging. Connect your charger cable to prove you are awake."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Live listening notice at bottom
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Listening for hardware power state change in real time...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
