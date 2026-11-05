package com.example.challenge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Counts real walking steps.
 *  - Uses the phone's step sensor when the "Physical activity" permission is granted,
 *    otherwise a stricter accelerometer detector.
 *  - Ignores steps right after a violent jerk (hand shaking) and steps closer than 0.35 s,
 *    so shaking the phone up and down counts much less than walking.
 */
class StepDetector(
    private val context: Context,
    private val onStep: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val hasPermission = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    private val stepSensor: Sensor? =
        if (hasPermission) sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastStepTime = 0L
    private var lastJerkTime = 0L
    private var lastMagnitude = 0.0
    private var lastPeakTime = 0L

    /** true when the phone's own step sensor is used (more reliable than the accelerometer). */
    val usesStepSensor: Boolean get() = stepSensor != null

    fun start() {
        stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble())

                // A violent jerk is shaking, not walking
                if (abs(magnitude - 9.81) > 13.0) lastJerkTime = now

                if (stepSensor == null) {
                    // Accelerometer only: count a rising peak, at most about 3 steps per second
                    if (magnitude - lastMagnitude > 3.0 && now - lastPeakTime > 380) {
                        lastPeakTime = now
                        acceptStep(now)
                    }
                }
                lastMagnitude = magnitude
            }
            Sensor.TYPE_STEP_DETECTOR -> acceptStep(now)
        }
    }

    private fun acceptStep(now: Long) {
        if (now - lastJerkTime < 700) return
        if (now - lastStepTime < 350) return
        lastStepTime = now
        onStep()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
