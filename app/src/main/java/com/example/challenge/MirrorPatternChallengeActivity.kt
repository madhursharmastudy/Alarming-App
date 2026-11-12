package com.example.challenge

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirror Pattern: some cells light up for a few seconds. Then you tap the MIRROR IMAGE of them
 * (left becomes right). The last stage asks for the opposite spot after turning the board upside down.
 * Low: 3x3, 4 cells, any order. Moderate: 4x4, 5 cells, same order. Difficult: 4x4, 6 cells, upside-down mirror.
 */
class MirrorPatternChallengeActivity : BaseChallengeActivity() {

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

    private val modeState = MutableStateFlow(0)
    val mode = modeState.asStateFlow()

    private var expected: List<Int> = emptyList()
    private var patternLength = 4
    private var showMs = 3500L
    private var orderMatters = false
    private var rotate = false

    override fun getChallengeType(): ChallengeType = ChallengeType.MIRROR_PATTERN

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 50
        2 -> 70
        else -> 90
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
                patternLength = 4; showMs = 3500L; orderMatters = false; rotate = false
            }
            2 -> {
                colsState.value = 4; rowsState.value = 4
                patternLength = 5; showMs = 3000L; orderMatters = true; rotate = false
            }
            else -> {
                colsState.value = 4; rowsState.value = 4
                patternLength = 6; showMs = 3000L; orderMatters = true; rotate = true
            }
        }
        modeState.value = if (rotate) 2 else if (orderMatters) 1 else 0
        setFeedbackMessage(null)
        startRound()
    }

    private fun mirrorOf(index: Int): Int {
        val c = colsState.value
        val r = rowsState.value
        val row = index / c
        val col = index % c
        return if (rotate) (r - 1 - row) * c + (c - 1 - col) else row * c + (c - 1 - col)
    }

    private fun startRound() {
        token++
        val myToken = token
        val total = colsState.value * rowsState.value
        val chosen = (0 until total).shuffled().take(patternLength)
        patternState.value = chosen
        expected = chosen.map { mirrorOf(it) }
        tappedState.value = emptyList()
        phaseState.value = "show"
        handler.postDelayed({
            if (myToken == token) phaseState.value = "input"
        }, showMs)
    }

    fun onCellTapped(index: Int) {
        if (phaseState.value != "input" || isCompletedState.value) return
        val done = tappedState.value
        if (done.contains(index) || done.size >= expected.size) return

        val ok = if (orderMatters) expected[done.size] == index else expected.contains(index)
        if (ok) {
            val updated = done + index
            tappedState.value = updated
            if (updated.size == expected.size) {
                phaseState.value = "done"
                setFeedbackMessage("Mirror perfect!", isSuccess = true)
                completeCurrentStage()
            }
        } else {
            setFeedbackMessage("Not the mirror spot! New pattern", isError = true)
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
        val m by mode.collectAsState()
        MirrorPatternScreen(
            cols = c,
            rows = r,
            pattern = p,
            tapped = t,
            phase = ph,
            mode = m,
            onCell = { onCellTapped(it) },
            modifier = modifier
        )
    }
}

@Composable
fun MirrorPatternScreen(
    cols: Int,
    rows: Int,
    pattern: List<Int>,
    tapped: List<Int>,
    phase: String,
    mode: Int,
    onCell: (Int) -> Unit,
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
            text = if (phase == "show") "Memorize the lit cells..." else "Tap the mirror image:  ${tapped.size} / ${pattern.size}"
        )
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val cell = minOf(maxWidth / cols, maxHeight / rows).coerceAtMost(88.dp)

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
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            showing -> Color(0xFF12B5E5)
                                            isTapped -> Color(0xFF3B82F6)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable { onCell(index) }
                                    .testTag("mirror_cell_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showing && mode >= 1) {
                                    Text(
                                        text = "${patternPos + 1}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isTapped && mode >= 1) {
                                    Text(
                                        text = "${tappedPos + 1}",
                                        color = Color.White,
                                        fontSize = 16.sp,
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
            text = when (mode) {
                0 -> "Left becomes right. Tap the mirror image of each lit cell, in any order."
                1 -> "Left becomes right. Tap the mirror images in the same order (1, 2, 3...)."
                else -> "Turn the board upside down: tap the opposite spot, in the same order."
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
