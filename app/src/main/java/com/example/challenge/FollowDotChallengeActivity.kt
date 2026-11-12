package com.example.challenge

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ChallengeType
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.OceanCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class FollowDot(val x: Float, val y: Float, val vx: Float, val vy: Float)

/**
 * Follow the Dot: one dot glows for a moment, then all dots look the same and drift around.
 * When they stop, tap the dot you followed.
 * Low: 3 dots, slow, 2 rounds. Moderate: 5 dots, 3 rounds. Difficult: 7 dots, fast, 4 rounds.
 */
class FollowDotChallengeActivity : BaseChallengeActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var moveStart = 0L

    private val dotsState = MutableStateFlow<List<FollowDot>>(emptyList())
    val dots = dotsState.asStateFlow()

    private val trackedState = MutableStateFlow(0)
    val tracked = trackedState.asStateFlow()

    private val phaseState = MutableStateFlow("mark")
    val phase = phaseState.asStateFlow()

    private val doneState = MutableStateFlow(0)
    val done = doneState.asStateFlow()

    private val needState = MutableStateFlow(2)
    val need = needState.asStateFlow()

    private var dotCount = 3
    private var speed = 0.35f
    private var moveMs = 4500L

    override fun getChallengeType(): ChallengeType = ChallengeType.FOLLOW_DOT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 90
        else -> 120
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        when (stage) {
            1 -> {
                dotCount = 3; speed = 0.35f; moveMs = 4500L; needState.value = 2
            }
            2 -> {
                dotCount = 5; speed = 0.5f; moveMs = 5500L; needState.value = 3
            }
            else -> {
                dotCount = 7; speed = 0.7f; moveMs = 6500L; needState.value = 4
            }
        }
        doneState.value = 0
        setFeedbackMessage(null)
        startRound()
    }

    private fun startRound() {
        token++
        val myToken = token
        val list = List(dotCount) {
            val angle = Random.nextFloat() * 6.2831f
            FollowDot(
                0.1f + Random.nextFloat() * 0.8f,
                0.1f + Random.nextFloat() * 0.8f,
                cos(angle) * speed,
                sin(angle) * speed
            )
        }
        dotsState.value = list
        trackedState.value = Random.nextInt(dotCount)
        phaseState.value = "mark"
        handler.postDelayed({
            if (myToken == token) {
                phaseState.value = "move"
                moveStart = SystemClock.elapsedRealtime()
                frame(myToken)
            }
        }, 2200L)
    }

    private fun frame(myToken: Int) {
        if (myToken != token || phaseState.value != "move") return
        val dt = 0.033f
        dotsState.value = dotsState.value.map { d ->
            var x = d.x + d.vx * dt
            var y = d.y + d.vy * dt
            var vx = d.vx
            var vy = d.vy
            if (x < 0.05f) {
                x = 0.05f
                vx = abs(vx)
            } else if (x > 0.95f) {
                x = 0.95f
                vx = -abs(vx)
            }
            if (y < 0.05f) {
                y = 0.05f
                vy = abs(vy)
            } else if (y > 0.95f) {
                y = 0.95f
                vy = -abs(vy)
            }
            FollowDot(x, y, vx, vy)
        }
        if (SystemClock.elapsedRealtime() - moveStart >= moveMs) {
            phaseState.value = "pick"
            return
        }
        handler.postDelayed({ frame(myToken) }, 33L)
    }

    fun onDotPicked(index: Int) {
        if (phaseState.value != "pick" || isCompletedState.value) return
        if (index == trackedState.value) {
            val d = doneState.value + 1
            doneState.value = d
            if (d >= needState.value) {
                phaseState.value = "finished"
                setFeedbackMessage("You tracked it!", isSuccess = true)
                completeCurrentStage()
            } else {
                setFeedbackMessage("Correct! Next round", isSuccess = true)
                startRound()
            }
        } else {
            setFeedbackMessage("Not that one. Try again", isError = true)
            startRound()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val list by dots.collectAsState()
        val t by tracked.collectAsState()
        val ph by phase.collectAsState()
        val d by done.collectAsState()
        val n by need.collectAsState()
        FollowDotScreen(
            dots = list,
            tracked = t,
            phase = ph,
            done = d,
            need = n,
            onPick = { onDotPicked(it) },
            modifier = modifier
        )
    }
}

@Composable
fun FollowDotScreen(
    dots: List<FollowDot>,
    tracked: Int,
    phase: String,
    done: Int,
    need: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        GameInfoPill(
            text = when (phase) {
                "mark" -> "Remember the glowing dot"
                "move" -> "Follow it..."
                else -> "Tap the dot you followed  ($done / $need)"
            }
        )
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)

            Canvas(
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F2038))
                    .testTag("follow_dot_board")
                    .pointerInput(dots, phase) {
                        detectTapGestures { offset: Offset ->
                            val w = size.width.toFloat()
                            var best = -1
                            var bestDist = Float.MAX_VALUE
                            dots.forEachIndexed { i, d ->
                                val dx = offset.x - d.x * w
                                val dy = offset.y - d.y * w
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                if (dist < bestDist) {
                                    bestDist = dist
                                    best = i
                                }
                            }
                            if (best >= 0 && bestDist < 0.1f * w) onPick(best)
                        }
                    }
            ) {
                val w = size.width
                val radius = w * 0.045f
                dots.forEachIndexed { i, d ->
                    val center = Offset(d.x * w, d.y * w)
                    if (phase == "mark" && i == tracked) {
                        drawCircle(color = Color(0x66FFB84D), radius = radius * 2.2f, center = center)
                        drawCircle(color = Color(0xFFFFB84D), radius = radius * 1.2f, center = center)
                    } else {
                        drawCircle(color = if (phase == "move") OceanCyan else OceanBlue, radius = radius * 1.2f, center = center)
                    }
                }
            }
        }

        GameHint(text = "One dot glows first. Keep your eyes on it while all dots move, then tap it.")
        Spacer(modifier = Modifier.height(4.dp))
    }
}
