package com.example.challenge

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
 * Number Grid (Schulte table): tap the numbers 1, 2, 3... in order on a shuffled grid.
 * Your eyes have to search the whole grid every time.
 * Low: 3x3. Moderate: 4x4. Difficult: 5x5 and the numbers reshuffle every 4 correct taps.
 */
class NumberGridChallengeActivity : BaseChallengeActivity() {

    private val sizeState = MutableStateFlow(3)
    val gridSize = sizeState.asStateFlow()

    private val numbersState = MutableStateFlow<List<Int>>(emptyList())
    val numbers = numbersState.asStateFlow()

    private val nextState = MutableStateFlow(1)
    val next = nextState.asStateFlow()

    private var reshuffle = false
    private var correctTaps = 0

    override fun getChallengeType(): ChallengeType = ChallengeType.NUMBER_GRID

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 30
        2 -> 50
        else -> 80
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        val n = when (stage) {
            1 -> 3
            2 -> 4
            else -> 5
        }
        sizeState.value = n
        reshuffle = stage == 3
        correctTaps = 0
        nextState.value = 1
        numbersState.value = (1..(n * n)).toList().shuffled()
        setFeedbackMessage(null)
    }

    fun onNumberTapped(number: Int) {
        if (isCompletedState.value) return
        val expected = nextState.value
        val total = sizeState.value * sizeState.value
        if (number == expected) {
            correctTaps++
            if (expected >= total) {
                nextState.value = expected + 1
                setFeedbackMessage("All numbers found!", isSuccess = true)
                completeCurrentStage()
                return
            }
            nextState.value = expected + 1
            if (reshuffle && correctTaps % 4 == 0) {
                numbersState.value = numbersState.value.shuffled()
            }
        } else if (number > expected) {
            setFeedbackMessage("Find $expected first", isError = true)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val n by gridSize.collectAsState()
        val list by numbers.collectAsState()
        val nx by next.collectAsState()
        NumberGridScreen(
            size = n,
            numbers = list,
            next = nx,
            onNumber = { onNumberTapped(it) },
            modifier = modifier
        )
    }
}

@Composable
fun NumberGridScreen(
    size: Int,
    numbers: List<Int>,
    next: Int,
    onNumber: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        GameInfoPill(text = "Find:  $next")
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)
            val gap = 8.dp
            val cell = (side - gap * (size - 1)) / size

            if (numbers.size == size * size) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in 0 until size) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (col in 0 until size) {
                                val number = numbers[row * size + col]
                                val found = number < next
                                Box(
                                    modifier = Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(18.dp))
                                        .then(
                                            if (found) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            else Modifier.background(MaterialTheme.colorScheme.surface)
                                        )
                                        .clickable { onNumber(number) }
                                        .testTag("grid_cell_$number"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$number",
                                        fontSize = if (size >= 5) 20.sp else 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (found) Color(0x5594A3B8) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        GameHint(
            text = if (size == 5) "Tap 1, 2, 3... in order. Careful: the numbers shuffle now and then!" else "Tap the numbers in order, starting from 1."
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
