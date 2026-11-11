package com.example.challenge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

/** Mind + voice: say the answer of a sum out loud (digits or English number words). */
class VoiceMathChallengeActivity : BaseChallengeActivity() {

    private val questionState = MutableStateFlow("")
    private val statusState = MutableStateFlow("Say the answer out loud")
    private val heardState = MutableStateFlow("")
    private val roundState = MutableStateFlow("")

    private var answer = 0
    private var roundsLeft = 1
    private var totalRounds = 1
    private var lastStage = 1
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun getChallengeType(): ChallengeType = ChallengeType.VOICE_MATH

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 45
        2 -> 70
        else -> 90
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!canUseMicrophone() || !SpeechRecognizer.isRecognitionAvailable(this)) {
            fallbackTo(MathChallengeActivity::class.java, "Voice is not available. Switching to Math.")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({ startListening() }, 600)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    override fun onStageStarted(stage: Int) {
        lastStage = stage
        totalRounds = if (stage == 1) 1 else 2
        roundsLeft = totalRounds
        newQuestion(stage)
    }

    override fun onResetToStage1() {
        onStageStarted(1)
    }

    private fun newQuestion(stage: Int) {
        val text: String
        when (stage) {
            1 -> {
                val a = Random.nextInt(3, 10)
                val b = Random.nextInt(3, 10)
                answer = a + b
                text = "$a + $b"
            }
            2 -> {
                val a = Random.nextInt(3, 10)
                val b = Random.nextInt(3, 10)
                answer = a * b
                text = "$a \u00D7 $b"
            }
            else -> {
                val a = Random.nextInt(6, 10)
                val b = Random.nextInt(6, 10)
                answer = a * b
                text = "$a \u00D7 $b"
            }
        }
        questionState.value = "$text = ?"
        heardState.value = ""
        roundState.value = "Question ${totalRounds - roundsLeft + 1} of $totalRounds"
    }

    private fun startListening() {
        if (isCompletedState.value) return
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            r.startListening(intent)
            statusState.value = "Listening... say the answer"
        } catch (e: Exception) {
            statusState.value = "Tap the button and speak"
        }
    }

    private fun checkSpoken(candidates: List<String>, isFinal: Boolean) {
        if (candidates.isEmpty() || isCompletedState.value) return
        heardState.value = "Heard: " + candidates.first()
        val numbers = candidates.flatMap { parseNumbers(it) }
        if (answer in numbers) {
            roundsLeft--
            if (roundsLeft <= 0) {
                statusState.value = "Correct!"
                setFeedbackMessage("Correct! Well spoken.", isSuccess = true)
                completeCurrentStage()
            } else {
                setFeedbackMessage("Correct! One more.", isSuccess = true)
                newQuestion(lastStage)
                recognizer?.cancel()
                handler.postDelayed({ startListening() }, 700)
            }
        } else if (isFinal) {
            statusState.value = "Not correct. Try again"
            handler.postDelayed({ startListening() }, 700)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (isCompletedState.value) return
            handler.postDelayed({ startListening() }, 800)
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            if (list.isEmpty()) {
                handler.postDelayed({ startListening() }, 600)
            } else {
                checkSpoken(list, isFinal = true)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            checkSpoken(list, isFinal = false)
        }
    }

    private val smallNumbers = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19
    )
    private val tensNumbers = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    /** Numbers found in the spoken text: digits ("56") and English words ("fifty six"). */
    private fun parseNumbers(text: String): List<Int> {
        val result = mutableListOf<Int>()
        Regex("\\d+").findAll(text).forEach { m -> m.value.toIntOrNull()?.let { result.add(it) } }

        val words = text.lowercase().replace("-", " ").split(" ").filter { it.isNotBlank() }
        var current = 0
        var found = false
        for (w in words) {
            when {
                smallNumbers.containsKey(w) -> { current += smallNumbers[w]!!; found = true }
                tensNumbers.containsKey(w) -> { current += tensNumbers[w]!!; found = true }
                w == "hundred" -> { current = (if (current == 0) 1 else current) * 100; found = true }
                w == "and" -> {}
                else -> {
                    if (found) {
                        result.add(current)
                        current = 0
                        found = false
                    }
                }
            }
        }
        if (found) result.add(current)
        return result
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val question by questionState.collectAsState()
        val status by statusState.collectAsState()
        val heard by heardState.collectAsState()
        val round by roundState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            TaskInfoCard(title = question, subtitle = "$round \u2022 Say the answer out loud")

            Text(
                text = status,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = heard,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { startListening() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("voice_listen_button"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("Tap to speak again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
