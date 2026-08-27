package com.example.challenge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Footprint
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class StepsChallengeActivity : BaseChallengeActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    private val currentStepsState = MutableStateFlow(0)
    val currentSteps = currentStepsState.asStateFlow()

    private val targetStepsState = MutableStateFlow(5)
    val targetSteps = targetStepsState.asStateFlow()

    private var lastMagnitude = 0.0
    private var lastStepTime = 0L

    override fun getChallengeType(): ChallengeType = ChallengeType.STEPS

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 120
        3 -> 180
        else -> 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onStageStarted(stage: Int) {
        val target = when (stage) {
            1 -> 5  // Low: 5 steps
            2 -> 15 // Moderate: 15 steps
            3 -> 30 // Advanced: 30 steps
            else -> 5
        }
        targetStepsState.value = target
        currentStepsState.value = 0
    }

    override fun onResetToStage1() {
        targetStepsState.value = 5
        currentStepsState.value = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            recordStep()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Fallback step calculation via accelerometer peak magnitude
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val mag = sqrt((x * x + y * y + z * z).toDouble())
            val now = System.currentTimeMillis()

            if (mag - lastMagnitude > 3.5 && (now - lastStepTime) > 350) {
                lastStepTime = now
                recordStep()
            }
            lastMagnitude = mag
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun recordStep() {
        val next = currentStepsState.value + 1
        currentStepsState.value = next
        if (next >= targetStepsState.value) {
            completeCurrentStage()
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val steps by currentSteps.collectAsState()
        val target by targetSteps.collectAsState()
        val stage by currentStage.collectAsState()

        val progress = if (target > 0) (steps.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stage Goal Card
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
                        text = "Stage $stage: Physical Walking Routine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stand up and walk around the room with your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Big Step Progress Ring
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
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Steps",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$steps / $target",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "STEPS WALKED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Step Button for quick test/emulator
            Button(
                onClick = { recordStep() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("step_simulator_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = "Walk Step")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate Step (+1)", fontWeight = FontWeight.Bold)
            }
        }
    }
}
