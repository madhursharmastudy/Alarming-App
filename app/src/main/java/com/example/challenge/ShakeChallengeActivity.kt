package com.example.challenge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class ShakeChallengeActivity : BaseChallengeActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private val currentShakesState = MutableStateFlow(0)
    val currentShakes = currentShakesState.asStateFlow()

    private val targetShakesState = MutableStateFlow(10)
    val targetShakes = targetShakesState.asStateFlow()

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdate: Long = 0
    private val SHAKE_THRESHOLD = 800 // Acceleration change threshold

    override fun getChallengeType(): ChallengeType = ChallengeType.SHAKE

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 30
        2 -> 50
        3 -> 80
        else -> 30
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onStageStarted(stage: Int) {
        val target = when (stage) {
            1 -> 10 // Low: 10 shakes
            2 -> 20 // Moderate: 20 shakes
            3 -> 35 // Advanced: 35 shakes
            else -> 10
        }
        targetShakesState.value = target
        currentShakesState.value = 0
    }

    override fun onResetToStage1() {
        targetShakesState.value = 10
        currentShakesState.value = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val curTime = System.currentTimeMillis()
        if ((curTime - lastUpdate) > 100) {
            val diffTime = curTime - lastUpdate
            lastUpdate = curTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val speed = kotlin.math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

            if (speed > SHAKE_THRESHOLD) {
                recordShake()
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun recordShake() {
        val next = currentShakesState.value + 1
        currentShakesState.value = next
        if (next >= targetShakesState.value) {
            completeCurrentStage()
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val current by currentShakes.collectAsState()
        val target by targetShakes.collectAsState()
        val stage by currentStage.collectAsState()

        val progress = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f

        val shakeAnim = rememberInfiniteTransition(label = "shake")
        val shakeAngle by shakeAnim.animateFloat(
            initialValue = -18f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(
                animation = tween(120),
                repeatMode = RepeatMode.Reverse
            ),
            label = "angle"
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stage Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Stage $stage: Shake Physical Energy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Vigorously shake your phone until the counter reaches target!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Big Shake Counter Dial
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 14.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Shake Phone",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(54.dp)
                            .rotate(shakeAngle)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$current / $target",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SHAKES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Interactive Shake / Manual Test Button (for testing or quick shake)
            Button(
                onClick = { recordShake() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("shake_phone_action_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Vibration, contentDescription = "Shake")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tap to Shake (+1)", fontWeight = FontWeight.Bold)
            }
        }
    }
}
