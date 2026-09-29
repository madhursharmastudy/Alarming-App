package com.example.challenge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 *
 * Strict single-screen non-scrollable layout with weight-based structure.
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
        setFeedbackMessage(null)
    }

    override fun onResetToStage1() {
        checkInitialPowerState()
        setFeedbackMessage(null)
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
            setFeedbackMessage("Charger state verified! Challenge Completed!", isSuccess = true)
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

    val themeColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isCharging) "Charging ($batteryPct%) • Connected" else "On Battery ($batteryPct%) • Disconnected",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Center Pulsing Graphic & Giant Instruction Area (weight 1f, strictly non-scrollable)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalHeight = maxHeight
            val iconBoxSize = minOf(totalHeight * 0.38f, 130.dp).coerceAtLeast(80.dp)
            val iconInnerSize = (iconBoxSize.value * 0.52f).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Pulsing Icon
                Box(
                    modifier = Modifier
                        .size(iconBoxSize)
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
                        .border(3.dp, themeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActionUnplug) Icons.Default.PowerOff else Icons.Default.Power,
                        contentDescription = "Charger Action",
                        tint = themeColor,
                        modifier = Modifier.size(iconInnerSize)
                    )
                }

                // Sleep-Inertia Friendly Giant Text Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("charger_action_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = themeColor.copy(alpha = 0.15f)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(themeColor)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REQUIRED PHYSICAL ACTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = themeColor,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isActionUnplug) "UNPLUG THE CHARGER" else "PLUG IN THE CHARGER",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isActionUnplug) {
                                "Phone was charging when alarm rang. Unplug the cable to stop the alarm."
                            } else {
                                "Phone is not charging. Connect your charger cable to prove you are awake."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Fixed-Height Bottom Note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Alarm automatically dismisses the instant state changes",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
