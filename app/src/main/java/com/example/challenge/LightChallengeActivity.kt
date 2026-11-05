package com.example.challenge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Get light into the room: open the curtain, switch on a light or go to a window.
 * The phone first measures the light around you, then you must reach a much brighter level
 * and hold it for a few seconds.
 */
class LightChallengeActivity : BaseChallengeActivity(), SensorEventListener {

    private val luxState = MutableStateFlow(0f)
    private val targetState = MutableStateFlow(0f)
    private val holdFractionState = MutableStateFlow(0f)
    private val statusState = MutableStateFlow("Measuring the light around you...")

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private val handler = Handler(Looper.getMainLooper())

    private var latestLux = 0f
    private var listenStart = 0L
    private val baselineSamples = ArrayList<Float>()
    private var baseline = -1f
    private var target = 0f
    private var holdStart = 0L
    private var holdNeededMs = 2000L
    private var finished = false

    override fun getChallengeType(): ChallengeType = ChallengeType.LIGHT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 90
        else -> 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            fallbackTo(MathChallengeActivity::class.java, "No light sensor. Switching to Math.")
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (lightSensor != null) {
            listenStart = System.currentTimeMillis()
            handler.post(tick)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        handler.removeCallbacks(tick)
    }

    override fun onStageStarted(stage: Int) {
        holdNeededMs = when (stage) {
            1 -> 2000L
            2 -> 3000L
            else -> 4000L
        }
        baselineSamples.clear()
        baseline = -1f
        holdStart = 0L
        finished = false
        listenStart = System.currentTimeMillis()
        holdFractionState.value = 0f
        targetState.value = 0f
        statusState.value = "Measuring the light around you..."
    }

    override fun onResetToStage1() {
        onStageStarted(1)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_LIGHT) {
            latestLux = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // The light sensor only reports when the light changes, so the hold time is checked on a timer
    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 200)
        }
    }

    private fun check() {
        if (finished || isCompletedState.value) return
        val now = System.currentTimeMillis()
        luxState.value = latestLux

        if (baseline < 0f) {
            baselineSamples.add(latestLux)
            if (now - listenStart >= 1500L && baselineSamples.size >= 3) {
                baseline = baselineSamples.average().toFloat()
                target = if (baseline >= 600f) {
                    baseline * 1.5f
                } else {
                    maxOf(baseline * 3f, baseline + 100f).coerceAtMost(1500f)
                }
                targetState.value = target
                statusState.value = "Now bring more light: open the curtain or switch on a light"
            }
            return
        }

        if (latestLux >= target) {
            if (holdStart == 0L) holdStart = now
            val held = now - holdStart
            holdFractionState.value = (held.toFloat() / holdNeededMs).coerceIn(0f, 1f)
            statusState.value = "Bright enough! Hold it..."
            if (held >= holdNeededMs) {
                finished = true
                setFeedbackMessage("Light found! Good morning.", isSuccess = true)
                completeCurrentStage()
            }
        } else {
            holdStart = 0L
            holdFractionState.value = 0f
            statusState.value = "Not bright enough yet"
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val lux by luxState.collectAsState()
        val needed by targetState.collectAsState()
        val hold by holdFractionState.collectAsState()
        val status by statusState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            TaskInfoCard(
                title = "Find the light",
                subtitle = "Open the curtain, switch on a light or go to a window. Keep the phone facing the light."
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Light now: ${lux.toInt()} lux",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (needed > 0f) "Needed: ${needed.toInt()} lux" else "Measuring...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (needed > 0f) (lux / needed).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { hold },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Text(
                    text = "hold",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = status,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
