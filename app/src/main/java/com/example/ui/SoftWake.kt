package com.example.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.example.service.AlarmRingingService

/**
 * Soft wake-up: the screen starts dim and slowly gets brighter, so the eyes are not hit by
 * full brightness the moment the alarm rings. Based on the time since the alarm started ringing,
 * so it continues smoothly across the ringing screen and the challenge screens.
 */
class SoftWakeBrightness(private val activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            applyBrightness()
            handler.postDelayed(this, 1000L)
        }
    }

    fun start() {
        if (running) return
        running = true
        AlarmRingingService.markRingStartIfNeeded()
        tick.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun applyBrightness() {
        val elapsed = System.currentTimeMillis() - AlarmRingingService.ringStartTime
        val fraction = (elapsed.toFloat() / RAMP_MS).coerceIn(0f, 1f)
        setBrightness(MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * fraction)
    }

    private fun setBrightness(value: Float) {
        try {
            val params = activity.window.attributes
            params.screenBrightness = value
            activity.window.attributes = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val MIN_BRIGHTNESS = 0.10f
        private const val MAX_BRIGHTNESS = 0.70f
        private const val RAMP_MS = 90_000L
    }
}
