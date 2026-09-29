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
 * 3-Stage Progressive Math Challenge:
 * - Low: 3 questions, 2-digit addition or subtraction (unmixed per problem), 60s.
 * - Moderate: 5 questions, 3-digit addition or subtraction, 90s.
 * - Difficult: 5 questions, multiplication with +/- terms (non-negative result), 120s.
 *
 * Sequence of questions per stage:
 * Correct answer moves to the next question. Wrong answer allows retry of same question.
 * Single-screen non-scrollable layout.
 */
class MathChallengeActivity : BaseChallengeActivity() {

    private val equationState = MutableStateFlow("24 + 18")
    val equation = equationState.asStateFlow()

    private val userInputState = MutableStateFlow("")
    val userInput = userInputState.asStateFlow()

    private val currentQuestionIndexState = MutableStateFlow(1)
    val currentQuestionIndex = currentQuestionIndexState.asStateFlow()

    private val totalQuestionsInStageState = MutableStateFlow(3)
    val totalQuestionsInStage = totalQuestionsInStageState.asStateFlow()

    private var targetAnswer = 42

    override fun getChallengeType(): ChallengeType = ChallengeType.MATH

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60   // Low: 3 questions, 60s
        2 -> 90   // Moderate: 5 questions, 90s
        3 -> 120  // Difficult: 5 questions, 120s
        else -> 60
    }

    override fun onStageStarted(stage: Int) {
        val totalQ = when (stage) {
            1 -> 3
            2 -> 5
            3 -> 5
            else -> 3
        }
        totalQuestionsInStageState.value = totalQ
        currentQuestionIndexState.value = 1
        userInputState.value = ""
        setFeedbackMessage(null)
        generateMathProblem(stage)
    }

    override fun onResetToStage1() {
        totalQuestionsInStageState.value = 3
        currentQuestionIndexState.value = 1
        userInputState.value = ""
        setFeedbackMessage(null)
        generateMathProblem(1)
    }

    private fun generateMathProblem(stage: Int) {
        userInputState.value = ""
        when (stage) {
            1 -> {
                // Low: 2-digit addition or subtraction (not mixed)
                val isAdd = Random.nextBoolean()
                if (isAdd) {
                    val a = Random.nextInt(12, 60)
                    val b = Random.nextInt(12, 39)
                    targetAnswer = a + b
                    equationState.value = "$a + $b"
                } else {
                    val a = Random.nextInt(30, 99)
                    val b = Random.nextInt(11, a - 5)
                    targetAnswer = a - b
                    equationState.value = "$a - $b"
                }
            }
            2 -> {
                // Moderate: 3-digit addition or subtraction
                val isAdd = Random.nextBoolean()
                if (isAdd) {
                    val a = Random.nextInt(110, 480)
                    val b = Random.nextInt(110, 480)
                    targetAnswer = a + b
                    equationState.value = "$a + $b"
                } else {
                    val a = Random.nextInt(300, 990)
                    val b = Random.nextInt(100, a - 50)
                    targetAnswer = a - b
                    equationState.value = "$a - $b"
                }
            }
            3 -> {
                // Difficult: Multiplication with + or - terms (non-negative result)
                val isAdd = Random.nextBoolean()
                val a = Random.nextInt(6, 15)
                val b = Random.nextInt(6, 15)
                val product = a * b
                if (isAdd) {
                    val c = Random.nextInt(15, 60)
                    targetAnswer = product + c
                    equationState.value = "($a × $b) + $c"
                } else {
                    val c = Random.nextInt(5, (product - 5).coerceAtLeast(6))
                    targetAnswer = product - c
                    equationState.value = "($a × $b) - $c"
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
                if (userInputState.value.length < 6) {
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
            val curQ = currentQuestionIndexState.value
            val totalQ = totalQuestionsInStageState.value

            if (curQ < totalQ) {
                // Move to next question within the same stage
                currentQuestionIndexState.value = curQ + 1
                setFeedbackMessage("Correct! Next question ($curQ of $totalQ done)", isSuccess = true)
                generateMathProblem(currentStageState.value)
            } else {
                // Completed all questions in this stage
                setFeedbackMessage("Correct! Stage Passed!", isSuccess = true)
                completeCurrentStage()
            }
        } else {
            setFeedbackMessage("Incorrect: $input. Try again!", isError = true)
            userInputState.value = ""
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val eq by equation.collectAsState()
        val input by userInput.collectAsState()
        val questionNum by currentQuestionIndex.collectAsState()
        val totalQ by totalQuestionsInStage.collectAsState()
        val stage by currentStage.collectAsState()

        MathScreen(
            equation = eq,
            userInput = input,
            questionNumber = questionNum,
            totalQuestions = totalQ,
            currentStage = stage,
            onKeyPress = { onKeypadPress(it) },
            modifier = modifier
        )
    }
}

@Composable
fun MathScreen(
    equation: String,
    userInput: String,
    questionNumber: Int,
    totalQuestions: Int,
    currentStage: Int,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalH = maxHeight

        // Dynamic compact sizing so everything strictly fits without scrolling
        val keypadH = (totalH * 0.52f).coerceIn(160.dp, 250.dp)
        val equationH = (totalH * 0.35f).coerceIn(80.dp, 140.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stage question progress pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Question $questionNumber of $totalQuestions",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${totalQuestions - questionNumber + 1} remaining",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top Area: Equation Display & Answer Box Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(equationH)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = equation,
                        fontSize = if (equationH < 100.dp) 24.sp else 30.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Answer Input Display Field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(if (equationH < 100.dp) 32.dp else 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInput.ifEmpty { "Answer" },
                            fontSize = if (equationH < 100.dp) 18.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (userInput.isEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Bottom Area: Numeric Keypad (Weight-free, bounded height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keypadH)
                    .padding(horizontal = 4.dp)
            ) {
                MathKeypad(
                    onKeyClick = onKeyPress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun MathKeypad(
    onKeyClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("DEL", "0", "OK")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (row in keys) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (key in row) {
                    val isAction = key == "DEL" || key == "OK"
                    val isOk = key == "OK"

                    val btnColor = when {
                        isOk -> MaterialTheme.colorScheme.primary
                        isAction -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }

                    val txtColor = when {
                        isOk -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(btnColor)
                            .border(
                                width = 1.dp,
                                color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onKeyClick(key) }
                            .testTag("math_key_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = txtColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = key,
                                fontSize = if (isAction) 16.sp else 22.sp,
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
