package com.example.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import kotlin.random.Random

data class InkColor(val name: String, val color: Color)

private val allInkColors = listOf(
    InkColor("RED", Color(0xFFEF4444)),
    InkColor("BLUE", Color(0xFF3B82F6)),
    InkColor("GREEN", Color(0xFF22C55E)),
    InkColor("YELLOW", Color(0xFFFACC15)),
    InkColor("PURPLE", Color(0xFFA855F7)),
    InkColor("ORANGE", Color(0xFFFB923C))
)

/**
 * Color Trap (Stroop): a colour NAME is written in a different ink colour. Tap the INK colour,
 * not the word. A wrong answer sends the streak back to 0.
 * Low: 3 colours, 4 in a row. Moderate: 4 colours, 6 in a row. Difficult: 6 colours, 8 in a row.
 */
class ColorTrapChallengeActivity : BaseChallengeActivity() {

    private val colorsState = MutableStateFlow(allInkColors.take(3))
    val colors = colorsState.asStateFlow()

    private val wordState = MutableStateFlow(0)
    val word = wordState.asStateFlow()

    private val inkState = MutableStateFlow(1)
    val ink = inkState.asStateFlow()

    private val streakState = MutableStateFlow(0)
    val streak = streakState.asStateFlow()

    private val needState = MutableStateFlow(4)
    val need = needState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.COLOR_TRAP

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40
        2 -> 50
        else -> 60
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        val count = when (stage) {
            1 -> 3
            2 -> 4
            else -> 6
        }
        colorsState.value = allInkColors.shuffled().take(count)
        needState.value = when (stage) {
            1 -> 4
            2 -> 6
            else -> 8
        }
        streakState.value = 0
        setFeedbackMessage(null)
        newRound()
    }

    private fun newRound() {
        val size = colorsState.value.size
        val w = Random.nextInt(size)
        var i = Random.nextInt(size)
        while (i == w) i = Random.nextInt(size)
        wordState.value = w
        inkState.value = i
    }

    fun onColorChosen(index: Int) {
        if (isCompletedState.value) return
        if (index == inkState.value) {
            val s = streakState.value + 1
            streakState.value = s
            if (s >= needState.value) {
                setFeedbackMessage("Sharp eyes!", isSuccess = true)
                completeCurrentStage()
            } else {
                newRound()
            }
        } else {
            streakState.value = 0
            setFeedbackMessage("Wrong! Pick the ink colour, not the word", isError = true)
            newRound()
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val list by colors.collectAsState()
        val w by word.collectAsState()
        val i by ink.collectAsState()
        val s by streak.collectAsState()
        val n by need.collectAsState()
        ColorTrapScreen(
            colors = list,
            wordIndex = w,
            inkIndex = i,
            streak = s,
            need = n,
            onChoose = { onColorChosen(it) },
            modifier = modifier
        )
    }
}

@Composable
fun ColorTrapScreen(
    colors: List<InkColor>,
    wordIndex: Int,
    inkIndex: Int,
    streak: Int,
    need: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        GameInfoPill(text = "In a row:  $streak / $need")

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (colors.isNotEmpty() && wordIndex < colors.size && inkIndex < colors.size) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "What COLOUR is this word written in?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = colors[wordIndex].name,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors[inkIndex].color,
                        modifier = Modifier.testTag("color_trap_word")
                    )
                }
            }
        }

        // Answer buttons: colour names in plain white (so the buttons cannot be matched by sight)
        val rows = colors.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEachIndexed { colIndex, item ->
                        val index = rowIndex * 2 + colIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onChoose(index) }
                                .testTag("color_choice_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        GameHint(text = "The word says one colour but is painted in another. Tap the painted colour.")
        Spacer(modifier = Modifier.height(4.dp))
    }
}
