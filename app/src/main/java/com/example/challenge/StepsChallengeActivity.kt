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

class StepsChallengeActivity : BaseChallengeActivity() {

    private var detector: StepDetector? = null

    private val currentStepsState = MutableStateFlow(0)
    val currentSteps = currentStepsState.asStateFlow()

    private val targetStepsState = MutableStateFlow(5)
    val targetSteps = targetStepsState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.STEPS

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 120
        3 -> 180
        else -> 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detector = StepDetector(this) { recordStep() }
    }

    override fun onResume() {
        super.onResume()
        detector?.start()
    }

    override fun onPause() {
        super.onPause()
        detector?.stop()
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Walk $target steps!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Stage $stage: stand up and walk around with your phone.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Big step progress ring
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 14.dp,
                    color = com.example.ui.theme.OceanCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Steps",
                        tint = com.example.ui.theme.OceanCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$steps",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "/ $target",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Keep walking around",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step button for quick test / emulator
            com.example.ui.theme.OceanGradientButton(
                text = "Simulate step (+1)",
                icon = Icons.Default.DirectionsWalk,
                onClick = { recordStep() },
                modifier = Modifier.testTag("step_simulator_button")
            )
        }
    }
}
