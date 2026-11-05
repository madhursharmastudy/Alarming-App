package com.example.challenge

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

/**
 * Mind + body: solve a sum in your head, then WALK that many steps and press DONE.
 * The step counter is shown, but you only know where to stop if you solved the sum.
 */
class WalkMathChallengeActivity : BaseChallengeActivity() {

    private val questionState = MutableStateFlow("")
    private val stepsState = MutableStateFlow(0)
    private var answer = 0
    private var lastStage = 1
    private var detector: StepDetector? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.WALK_MATH

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 90
        else -> 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detector = StepDetector(this) { stepsState.value = stepsState.value + 1 }
    }

    override fun onResume() {
        super.onResume()
        detector?.start()
    }

    override fun onPause() {
        super.onPause()
        detector?.stop()
    }

    override fun onStageStarted(stage: Int) {
        lastStage = stage
        newQuestion(stage)
    }

    override fun onResetToStage1() {
        lastStage = 1
        newQuestion(1)
    }

    private fun newQuestion(stage: Int) {
        val text: String
        when (stage) {
            1 -> {
                val a = Random.nextInt(2, 6)
                val b = Random.nextInt(2, 6)
                answer = a + b
                text = "$a + $b"
            }
            2 -> {
                val a = Random.nextInt(4, 10)
                val b = Random.nextInt(3, 9)
                answer = a + b
                text = "$a + $b"
            }
            else -> {
                val a = Random.nextInt(2, 5)
                val b = Random.nextInt(3, 6)
                answer = a * b
                text = "$a \u00D7 $b"
            }
        }
        questionState.value = "$text = ?"
        stepsState.value = 0
    }

    fun onDoneClicked() {
        val walked = stepsState.value
        if (walked == answer) {
            setFeedbackMessage("Correct! You walked $walked steps.", isSuccess = true)
            completeCurrentStage()
        } else {
            setFeedbackMessage("You walked $walked steps but the answer was $answer. New sum!", isError = true)
            newQuestion(lastStage)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val question by questionState.collectAsState()
        val steps by stepsState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Solve the sum, then WALK that many steps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = question,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Steps counted: $steps",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hold the phone and really walk. Shaking it does not count.",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { onDoneClicked() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("walk_done_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("DONE", fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
