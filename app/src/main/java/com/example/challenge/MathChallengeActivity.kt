package com.example.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * 3-Stage Progressive Math Challenge.
 * Low: Single-digit addition.
 * Moderate: Double-digit arithmetic.
 * Difficult: Multi-step multiplication and addition.
 *
 * Strict single-screen non-scrollable layout with weight-based structure.
 */
class MathChallengeActivity : BaseChallengeActivity() {

    private val equationState = MutableStateFlow("7 + 8")
    val equation = equationState.asStateFlow()

    private val userInputState = MutableStateFlow("")
    val userInput = userInputState.asStateFlow()

    private var targetAnswer = 15

    override fun getChallengeType(): ChallengeType = ChallengeType.MATH

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40
        2 -> 60
        3 -> 90
        else -> 40
    }

    override fun onStageStarted(stage: Int) {
        userInputState.value = ""
        setFeedbackMessage(null)
        generateMathProblem(stage)
    }

    override fun onResetToStage1() {
        userInputState.value = ""
        setFeedbackMessage(null)
        generateMathProblem(1)
    }

    private fun generateMathProblem(stage: Int) {
        userInputState.value = ""
        when (stage) {
            1 -> {
                // Low: 1-digit addition
                val a = Random.nextInt(3, 10)
                val b = Random.nextInt(3, 10)
                targetAnswer = a + b
                equationState.value = "$a + $b"
            }
            2 -> {
                // Moderate: 2-digit addition or subtraction
                val isAdd = Random.nextBoolean()
                if (isAdd) {
                    val a = Random.nextInt(12, 60)
                    val b = Random.nextInt(12, 40)
                    targetAnswer = a + b
                    equationState.value = "$a + $b"
                } else {
                    val a = Random.nextInt(30, 99)
                    val b = Random.nextInt(10, a - 5)
                    targetAnswer = a - b
                    equationState.value = "$a - $b"
                }
            }
            3 -> {
                // Advanced: Multiplication or 2-step
                val isMulti = Random.nextBoolean()
                if (isMulti) {
                    val a = Random.nextInt(6, 13)
                    val b = Random.nextInt(6, 13)
                    targetAnswer = a * b
                    equationState.value = "$a × $b"
                } else {
                    val a = Random.nextInt(4, 9)
                    val b = Random.nextInt(4, 9)
                    val c = Random.nextInt(10, 30)
                    targetAnswer = (a * b) + c
                    equationState.value = "($a × $b) + $c"
                }
            }
        }
    }

    fun onKeypadPress(key: String) {
        when (key) {
            "DEL" -> {
                val cur = userInputState.value
                if (cur.isNotEmpty()) {
                    userInputState.value = cur.dropLast(1)
                    setFeedbackMessage(null)
                }
            }
            "OK" -> {
                checkAnswer()
            }
            else -> {
                if (userInputState.value.length < 5) {
                    userInputState.value += key
                    setFeedbackMessage(null)
                }
            }
        }
    }

    private fun checkAnswer() {
        val input = userInputState.value.toIntOrNull()
        if (input == null) return

        if (input == targetAnswer) {
            setFeedbackMessage("Correct! ($targetAnswer) - Stage Completed!", isSuccess = true)
            completeCurrentStage()
        } else {
            setFeedbackMessage("Incorrect: $input. Try again!", isError = true)
            userInputState.value = ""
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val eq by equation.collectAsState()
        val input by userInput.collectAsState()
        val stage by currentStage.collectAsState()

        MathChallengeScreen(
            equation = eq,
            userInput = input,
            currentStage = stage,
            onKeyPress = { onKeypadPress(it) },
            modifier = modifier
        )
    }
}

@Composable
fun MathChallengeScreen(
    equation: String,
    userInput: String,
    currentStage: Int,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Problem Display Board (weight 1f, strictly non-scrollable)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalH = maxHeight
            val eqFontSize = if (totalH < 180.dp) 28.sp else 36.sp
            val inputBoxHeight = if (totalH < 180.dp) 44.dp else 52.dp

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SOLVE TO DISMISS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$equation = ?",
                        fontSize = eqFontSize,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(inputBoxHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userInput.isEmpty()) "Answer" else userInput,
                            fontSize = if (totalH < 180.dp) 22.sp else 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (userInput.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Custom High-Contrast Keypad (Fixed height, strictly non-scrollable)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("DEL", "0", "OK")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        val isOk = key == "OK"
                        val isDel = key == "DEL"
                        val btnColor = when {
                            isOk -> MaterialTheme.colorScheme.primary
                            isDel -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val txtColor = when {
                            isOk -> MaterialTheme.colorScheme.onPrimary
                            isDel -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(btnColor)
                                .clickable { onKeyPress(key) }
                                .testTag("keypad_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDel) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Delete",
                                    tint = txtColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = txtColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
