package com.example.challenge

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class MathChallengeActivity : BaseChallengeActivity() {

    private val equationState = MutableStateFlow("7 + 8 = ?")
    val equation = equationState.asStateFlow()

    private val userInputState = MutableStateFlow("")
    val userInput = userInputState.asStateFlow()

    private val feedbackState = MutableStateFlow<String?>(null)
    val feedback = feedbackState.asStateFlow()

    private val solvedInStageState = MutableStateFlow(0)
    val solvedInStage = solvedInStageState.asStateFlow()

    private var targetAnswer = 15

    override fun getChallengeType(): ChallengeType = ChallengeType.MATH

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40
        2 -> 60
        3 -> 90
        else -> 40
    }

    override fun onStageStarted(stage: Int) {
        solvedInStageState.value = 0
        userInputState.value = ""
        feedbackState.value = null
        generateMathProblem(stage)
    }

    override fun onResetToStage1() {
        solvedInStageState.value = 0
        userInputState.value = ""
        feedbackState.value = "Reset to Stage 1! Solve the math problem."
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
                }
            }
            "OK" -> {
                checkAnswer()
            }
            else -> {
                if (userInputState.value.length < 5) {
                    userInputState.value += key
                }
            }
        }
    }

    private fun checkAnswer() {
        val input = userInputState.value.toIntOrNull()
        if (input == null) return

        if (input == targetAnswer) {
            feedbackState.value = "Correct! ($targetAnswer)"
            completeCurrentStage()
        } else {
            feedbackState.value = "Incorrect: $input. Try again!"
            userInputState.value = ""
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val eq by equation.collectAsState()
        val input by userInput.collectAsState()
        val fb by feedback.collectAsState()
        val stage by currentStage.collectAsState()

        val stageDescription = when (stage) {
            1 -> "Low: Single-Digit Addition"
            2 -> "Moderate: Double-Digit Arithmetic"
            3 -> "Advanced: Multiplication & Multi-Step"
            else -> ""
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Problem Display Board
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stageDescription,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "$eq = ?",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 38.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (input.isEmpty()) "Enter Answer" else input,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (input.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (fb != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = fb!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (fb!!.startsWith("Correct")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Custom High-Contrast Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val isAction = key == "DEL" || key == "OK"
                            val btnColor = when (key) {
                                "OK" -> MaterialTheme.colorScheme.primary
                                "DEL" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val txtColor = when (key) {
                                "OK" -> MaterialTheme.colorScheme.onPrimary
                                "DEL" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(btnColor)
                                    .clickable { onKeypadPress(key) }
                                    .testTag("keypad_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "Delete",
                                        tint = txtColor
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
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
}
