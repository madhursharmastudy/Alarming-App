package com.example.challenge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Body + mind: follow a route such as "Walk 5 steps, turn LEFT, walk 4 steps".
 * Steps come from the step detector; turning is measured with the gyroscope around the
 * vertical (gravity) axis, so it works however the phone is held.
 */
class TurnWalkChallengeActivity : BaseChallengeActivity(), SensorEventListener {

    private sealed class Leg {
        data class Walk(val steps: Int) : Leg()
        data class Turn(val left: Boolean) : Leg()
    }

    private val legsState = MutableStateFlow<List<Leg>>(emptyList())
    private val legIndexState = MutableStateFlow(0)
    private val progressState = MutableStateFlow(0)   // steps walked, or degrees turned (0..90)

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    private var detector: StepDetector? = null

    private val gravity = floatArrayOf(0f, 0f, 9.81f)
    private var lastGyroTimestamp = 0L
    private var turnAngleDeg = 0f

    override fun getChallengeType(): ChallengeType = ChallengeType.TURN_WALK

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 90
        else -> 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyroscope == null || accelerometer == null) {
            fallbackTo(StepsChallengeActivity::class.java, "This phone cannot measure turning. Switching to Steps.")
            return
        }
        detector = StepDetector(this) { onStepWalked() }

        detector?.let {
            if (!it.usesStepSensor) {
                android.widget.Toast.makeText(
                    this,
                    "Tip: allow Physical activity in the setup card for the most accurate step counting",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        detector?.start()
        gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        lastGyroTimestamp = 0L
    }

    override fun onPause() {
        super.onPause()
        detector?.stop()
        sensorManager?.unregisterListener(this)
    }

    override fun onStageStarted(stage: Int) {
        buildRoute(stage)
    }

    override fun onResetToStage1() {
        buildRoute(1)
    }

    private fun buildRoute(stage: Int) {
        val firstLeft = Random.nextBoolean()
        val legs: List<Leg> = when (stage) {
            1 -> listOf(Leg.Walk(4), Leg.Turn(firstLeft), Leg.Walk(3))
            2 -> listOf(Leg.Walk(5), Leg.Turn(firstLeft), Leg.Walk(4), Leg.Turn(!firstLeft))
            else -> listOf(Leg.Walk(6), Leg.Turn(firstLeft), Leg.Walk(5), Leg.Turn(!firstLeft), Leg.Walk(4))
        }
        legsState.value = legs
        legIndexState.value = 0
        progressState.value = 0
        turnAngleDeg = 0f
    }

    private fun currentLeg(): Leg? = legsState.value.getOrNull(legIndexState.value)

    private fun onStepWalked() {
        val leg = currentLeg() as? Leg.Walk ?: return
        val next = progressState.value + 1
        progressState.value = next
        if (next >= leg.steps) advance()
    }

    private fun advance() {
        val nextIndex = legIndexState.value + 1
        if (nextIndex >= legsState.value.size) {
            legIndexState.value = nextIndex
            setFeedbackMessage("Route completed!", isSuccess = true)
            completeCurrentStage()
            return
        }
        legIndexState.value = nextIndex
        progressState.value = 0
        turnAngleDeg = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass filter: what is left is gravity (points "up" in the phone's axes)
                for (i in 0..2) gravity[i] = 0.9f * gravity[i] + 0.1f * event.values[i]
            }
            Sensor.TYPE_GYROSCOPE -> {
                val last = lastGyroTimestamp
                lastGyroTimestamp = event.timestamp
                if (last == 0L) return
                val turn = currentLeg() as? Leg.Turn ?: return

                val dt = (event.timestamp - last) / 1_000_000_000f
                val gMag = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
                if (gMag < 1f || dt <= 0f || dt > 0.5f) return

                // Rotation speed around the vertical axis: positive = turning LEFT (counter-clockwise)
                val up0 = gravity[0] / gMag
                val up1 = gravity[1] / gMag
                val up2 = gravity[2] / gMag
                val yawRate = event.values[0] * up0 + event.values[1] * up1 + event.values[2] * up2
                turnAngleDeg += yawRate * dt * 57.2958f

                val wanted = if (turn.left) 1f else -1f
                val progress = turnAngleDeg * wanted
                progressState.value = progress.toInt().coerceIn(0, 90)
                if (progress >= 75f) {
                    advance()
                } else if (progress <= -75f) {
                    setFeedbackMessage("Wrong way! Turn ${if (turn.left) "LEFT" else "RIGHT"}.", isError = true)
                    turnAngleDeg = 0f
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun legText(leg: Leg): String = when (leg) {
        is Leg.Walk -> "Walk ${leg.steps} steps"
        is Leg.Turn -> if (leg.left) "Turn LEFT" else "Turn RIGHT"
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val legs by legsState.collectAsState()
        val index by legIndexState.collectAsState()
        val progress by progressState.collectAsState()

        val leg = legs.getOrNull(index)
        val bigText = when (leg) {
            is Leg.Walk -> "WALK ${leg.steps} STEPS"
            is Leg.Turn -> if (leg.left) "TURN LEFT" else "TURN RIGHT"
            null -> "Done"
        }
        val subText = when (leg) {
            is Leg.Walk -> "$progress / ${leg.steps} steps"
            is Leg.Turn -> "Turn your body about a quarter circle ($progress\u00B0 / 75\u00B0)"
            null -> ""
        }
        val fraction = when (leg) {
            is Leg.Walk -> if (leg.steps > 0) progress.toFloat() / leg.steps else 0f
            is Leg.Turn -> (progress / 75f).coerceIn(0f, 1f)
            null -> 1f
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Follow the route in order",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = bigText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // The whole route, so you can plan ahead
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                legs.forEachIndexed { i, l ->
                    val done = i < index
                    val current = i == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${i + 1}. ${legText(l)}",
                            fontSize = 13.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (done) "\u2713" else "",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
