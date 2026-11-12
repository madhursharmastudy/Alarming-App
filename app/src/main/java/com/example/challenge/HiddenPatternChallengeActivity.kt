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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import com.example.ui.theme.OceanBrush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hidden Pattern: numbered dots are shown for a few seconds, then they disappear and you tap the
 * dots from memory. The last stage asks for the REVERSE order.
 * Low: 3x3 grid, 5 dots. Moderate: 3x4 grid, 7 dots. Difficult: 4x4 grid, 9 dots, reverse order.
 */
class HiddenPatternChallengeActivity : BaseChallengeActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0

    private val colsState = MutableStateFlow(3)
    val cols = colsState.asStateFlow()

    private val rowsState = MutableStateFlow(3)
    val rows = rowsState.asStateFlow()

    private val patternState = MutableStateFlow<List<Int>>(emptyList())
    val pattern = patternState.asStateFlow()

    private val tappedState = MutableStateFlow<List<Int>>(emptyList())
    val tapped = tappedState.asStateFlow()

    private val phaseState = MutableStateFlow("show")
    val phase = phaseState.asStateFlow()

    private val reverseState = MutableStateFlow(false)
    val reverse = reverseState.asStateFlow()

    private var patternLength = 5
    private var showMs = 4000L

    override fun getChallengeType(): ChallengeType = ChallengeType.HIDDEN_PATTERN

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 50
        2 -> 75
        else -> 100
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
                colsState.value = 3; rowsState.value = 3
                patternLength = 5; showMs = 4000L; reverseState.value = false
            }
            2 -> {
                colsState.value = 3; rowsState.value = 4
                patternLength = 7; showMs = 3500L; reverseState.value = false
            }
            else -> {
                colsState.value = 4; rowsState.value = 4
                patternLength = 9; showMs = 3500L; reverseState.value = true
            }
        }
        setFeedbackMessage(null)
        startRound()
    }

    private fun startRound() {
        token++
        val myToken = token
        val total = colsState.value * rowsState.value
        patternState.value = (0 until total).shuffled().take(patternLength)
        tappedState.value = emptyList()
        phaseState.value = "show"
        handler.postDelayed({
            if (myToken == token) phaseState.value = "input"
        }, showMs)
    }

    fun onDotTapped(index: Int) {
        if (phaseState.value != "input" || isCompletedState.value) return
        val done = tappedState.value
        if (done.contains(index)) return

        val order = if (reverseState.value) patternState.value.reversed() else patternState.value
        if (done.size >= order.size) return

        if (order[done.size] == index) {
            val updated = done + index
            tappedState.value = updated
            if (updated.size == order.size) {
                phaseState.value = "done"
                setFeedbackMessage("Perfect memory!", isSuccess = true)
                completeCurrentStage()
            }
        } else {
            setFeedbackMessage("Wrong dot! New pattern", isError = true)
            startRound()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val c by cols.collectAsState()
        val r by rows.collectAsState()
        val p by pattern.collectAsState()
        val t by tapped.collectAsState()
        val ph by phase.collectAsState()
        val rev by reverse.collectAsState()
        HiddenPatternScreen(
            cols = c,
            rows = r,
            pattern = p,
            tapped = t,
            phase = ph,
            reverse = rev,
            onDot = { onDotTapped(it) },
            modifier = modifier
        )
    }
}

@Composable
fun HiddenPatternScreen(
    cols: Int,
    rows: Int,
    pattern: List<Int>,
    tapped: List<Int>,
    phase: String,
    reverse: Boolean,
    onDot: (Int) -> Unit,
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
            text = if (phase == "show") "Memorize the order..." else "Tap from memory:  ${tapped.size} / ${pattern.size}"
        )
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val cell = minOf(maxWidth / cols, maxHeight / rows).coerceAtMost(92.dp)

            Column {
                for (row in 0 until rows) {
                    Row {
                        for (col in 0 until cols) {
                            val index = row * cols + col
                            val patternPos = pattern.indexOf(index)
                            val tappedPos = tapped.indexOf(index)
                            val showing = phase == "show" && patternPos >= 0
                            val isTapped = tappedPos >= 0

                            Box(
                                modifier = Modifier
                                    .size(cell)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            showing -> Color(0xFF12B5E5)
                                            isTapped -> Color(0xFF3B82F6)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable { onDot(index) }
                                    .testTag("hidden_dot_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showing) {
                                    Text(
                                        text = "${patternPos + 1}",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isTapped) {
                                    Text(
                                        text = "${tappedPos + 1}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        GameHint(
            text = if (reverse) "Tap the dots in REVERSE order: last number first." else "Tap the dots in the same order: 1, 2, 3..."
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
