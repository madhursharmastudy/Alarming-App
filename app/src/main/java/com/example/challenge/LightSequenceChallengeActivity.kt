package com.example.challenge

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Light Sequence (Simon): four pads glow one after another, then you repeat the same order.
 * Low: 3 lights, Moderate: 5 lights, Difficult: 7 lights (a little faster each stage).
 * Lights never blink faster than about twice per second.
 */
class LightSequenceChallengeActivity : BaseChallengeActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0

    private val litState = MutableStateFlow(-1)
    val lit = litState.asStateFlow()

    private val phaseState = MutableStateFlow("watch")
    val phase = phaseState.asStateFlow()

    private val progressState = MutableStateFlow(0)
    val progress = progressState.asStateFlow()

    private val lengthState = MutableStateFlow(3)
    val length = lengthState.asStateFlow()

    private var sequence: List<Int> = emptyList()
    private var inputIndex = 0
    private var stepMs = 650L

    override fun getChallengeType(): ChallengeType = ChallengeType.LIGHT_SEQUENCE

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 45
        2 -> 70
        else -> 95
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        lengthState.value = when (stage) {
            1 -> 3
            2 -> 5
            else -> 7
        }
        stepMs = when (stage) {
            1 -> 650L
            2 -> 520L
            else -> 430L
        }
        setFeedbackMessage(null)
        startRound()
    }

    private fun startRound() {
        token++
        val myToken = token
        val len = lengthState.value
        sequence = List(len) { Random.nextInt(4) }
        inputIndex = 0
        progressState.value = 0
        phaseState.value = "watch"
        litState.value = -1

        var t = 700L
        for (pad in sequence) {
            handler.postDelayed({
                if (myToken == token) litState.value = pad
            }, t)
            handler.postDelayed({
                if (myToken == token) litState.value = -1
            }, t + stepMs * 6 / 10)
            t += stepMs
        }
        handler.postDelayed({
            if (myToken == token) {
                litState.value = -1
                phaseState.value = "repeat"
            }
        }, t)
    }

    fun onPadTapped(pad: Int) {
        if (phaseState.value != "repeat" || isCompletedState.value) return
        val myToken = token
        litState.value = pad
        handler.postDelayed({
            if (myToken == token) litState.value = -1
        }, 180L)

        if (pad == sequence[inputIndex]) {
            inputIndex++
            progressState.value = inputIndex
            if (inputIndex >= sequence.size) {
                phaseState.value = "done"
                setFeedbackMessage("Sequence correct!", isSuccess = true)
                completeCurrentStage()
            }
        } else {
            setFeedbackMessage("Wrong light! Watch again", isError = true)
            startRound()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val litPad by lit.collectAsState()
        val currentPhase by phase.collectAsState()
        val done by progress.collectAsState()
        val total by length.collectAsState()
        LightSequenceScreen(
            litPad = litPad,
            phase = currentPhase,
            progress = done,
            total = total,
            onPad = { onPadTapped(it) },
            modifier = modifier
        )
    }
}

private val padColors = listOf(
    Color(0xFF12B5E5),
    Color(0xFF3B82F6),
    Color(0xFF22D3A5),
    Color(0xFFFFB84D)
)

@Composable
fun LightSequenceScreen(
    litPad: Int,
    phase: String,
    progress: Int,
    total: Int,
    onPad: (Int) -> Unit,
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
            text = if (phase == "repeat") "Your turn:  $progress / $total" else "Watch the lights..."
        )
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)
            val pad = (side - 12.dp) / 2

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (col in 0 until 2) {
                            val index = row * 2 + col
                            val alpha = if (litPad == index) 1f else 0.32f
                            Box(
                                modifier = Modifier
                                    .size(pad)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(padColors[index].copy(alpha = alpha))
                                    .clickable { onPad(index) }
                                    .testTag("light_pad_$index")
                            )
                        }
                    }
                }
            }
        }

        GameHint(text = "Watch the order the lights glow, then tap the same order.")
        Spacer(modifier = Modifier.height(4.dp))
    }
}
