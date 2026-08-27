package com.example.challenge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.random.Random

class AudioChallengeActivity : BaseChallengeActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    private val promptTextState = MutableStateFlow("Loading question...")
    val promptText = promptTextState.asStateFlow()

    private val recognizedTextState = MutableStateFlow("")
    val recognizedText = recognizedTextState.asStateFlow()

    private val isListeningState = MutableStateFlow(false)
    val isListening = isListeningState.asStateFlow()

    private val stepInfoState = MutableStateFlow("")
    val stepInfo = stepInfoState.asStateFlow()

    private val feedbackState = MutableStateFlow<String?>(null)
    val feedback = feedbackState.asStateFlow()

    // Internal stage progression tracking
    private var subStep = 0 // For moderate (0/2, 1/2) or advanced (0/2 math, 1/2 phrase)
    private var expectedAnswer = ""
    private var isPhraseTarget = false

    private val requestRecordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            feedbackState.value = "Microphone permission required for Voice challenge!"
        }
    }

    override fun getChallengeType(): ChallengeType = ChallengeType.AUDIO

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40
        2 -> 60
        3 -> 80
        else -> 40
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        initSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
            speakCurrentQuestion()
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListeningState.value = true
                        feedbackState.value = "Listening... Speak your answer now!"
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListeningState.value = false
                    }

                    override fun onError(error: Int) {
                        isListeningState.value = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please speak clearly!"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to retry."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Try again."
                            else -> "Voice recognition retry required."
                        }
                        feedbackState.value = errorMsg
                    }

                    override fun onResults(results: Bundle?) {
                        isListeningState.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spoken = matches[0]
                            recognizedTextState.value = spoken
                            verifySpokenAnswer(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            recognizedTextState.value = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    override fun onStageStarted(stage: Int) {
        subStep = 0
        feedbackState.value = null
        recognizedTextState.value = ""
        generateQuestionForStage(stage)
    }

    override fun onResetToStage1() {
        subStep = 0
        feedbackState.value = "Challenge reset to Stage 1. Listen carefully!"
        recognizedTextState.value = ""
    }

    private fun generateQuestionForStage(stage: Int) {
        when (stage) {
            1 -> {
                // Low: 1 spoken sum (1-digit addition)
                val a = Random.nextInt(2, 10)
                val b = Random.nextInt(2, 10)
                val ans = a + b
                expectedAnswer = ans.toString()
                isPhraseTarget = false
                promptTextState.value = "What is $a plus $b?"
                stepInfoState.value = "Solve 1 spoken arithmetic question"
                speakCurrentQuestion()
            }
            2 -> {
                // Moderate: 2 spoken sums in a row
                isPhraseTarget = false
                val a = Random.nextInt(10, 30)
                val isAddition = Random.nextBoolean()
                if (isAddition) {
                    val b = Random.nextInt(5, 20)
                    expectedAnswer = (a + b).toString()
                    promptTextState.value = "What is $a plus $b?"
                } else {
                    val b = Random.nextInt(2, a - 1)
                    expectedAnswer = (a - b).toString()
                    promptTextState.value = "What is $a minus $b?"
                }
                stepInfoState.value = "Question ${subStep + 1} of 2 (Solve 2 sums in a row)"
                speakCurrentQuestion()
            }
            3 -> {
                // Advanced: 1 sum + repeat phrase
                if (subStep == 0) {
                    isPhraseTarget = false
                    val a = Random.nextInt(3, 9)
                    val b = Random.nextInt(3, 9)
                    expectedAnswer = (a * b).toString()
                    promptTextState.value = "What is $a times $b?"
                    stepInfoState.value = "Part 1 of 2: Advanced Multiplication"
                } else {
                    isPhraseTarget = true
                    expectedAnswer = "I am wide awake and ready"
                    promptTextState.value = "Now repeat clearly: \"I am wide awake and ready\""
                    stepInfoState.value = "Part 2 of 2: Awakening phrase verification"
                }
                speakCurrentQuestion()
            }
        }
    }

    fun speakCurrentQuestion() {
        if (isTtsReady) {
            tts?.speak(promptTextState.value, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_MATH")
        }
    }

    fun triggerVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptTextState.value)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            feedbackState.value = "Speech recognition start failed. You can use manual entry below if needed."
        }
    }

    fun verifySpokenAnswer(input: String) {
        val normalized = input.trim().lowercase()
        val isCorrect = if (isPhraseTarget) {
            normalized.contains("awake") || normalized.contains("ready") || normalized.contains("wide awake")
        } else {
            // Check numeric or word representation e.g. "15" or "fifteen"
            val numberWordMap = mapOf(
                "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
                "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
                "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
                "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
                "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "twenty-one" to "21",
                "twenty-two" to "22", "twenty-three" to "23", "twenty-four" to "24",
                "twenty-five" to "25", "twenty-six" to "26", "twenty-seven" to "27",
                "twenty-eight" to "28", "twenty-nine" to "29", "thirty" to "30",
                "thirty-one" to "31", "thirty-two" to "32", "thirty-five" to "35",
                "thirty-six" to "36", "forty" to "40", "forty-two" to "42",
                "forty-eight" to "48", "forty-nine" to "49", "fifty-four" to "54",
                "fifty-six" to "56", "sixty-three" to "63", "sixty-four" to "64",
                "seventy-two" to "72", "eighty-one" to "81"
            )
            val convertedNumber = numberWordMap[normalized] ?: normalized
            convertedNumber == expectedAnswer || normalized.contains(expectedAnswer)
        }

        if (isCorrect) {
            feedbackState.value = "Correct! ($input)"
            val stage = currentStageState.value
            when (stage) {
                1 -> {
                    completeCurrentStage()
                }
                2 -> {
                    subStep++
                    if (subStep >= 2) {
                        completeCurrentStage()
                    } else {
                        generateQuestionForStage(2)
                    }
                }
                3 -> {
                    subStep++
                    if (subStep >= 2) {
                        completeCurrentStage()
                    } else {
                        generateQuestionForStage(3)
                    }
                }
            }
        } else {
            feedbackState.value = "Incorrect: \"$input\". Try again! (Expected: $expectedAnswer)"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val prompt by promptText.collectAsState()
        val recognized by recognizedText.collectAsState()
        val listening by isListening.collectAsState()
        val stepInfoText by stepInfo.collectAsState()
        val feedbackText by feedback.collectAsState()

        var manualText by remember { mutableStateOf("") }

        val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
        val micScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (listening) 1.25f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micScale"
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stage Goal Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stepInfoText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { speakCurrentQuestion() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Hear Again",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Big Voice / Mic Action Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        .border(
                            width = 4.dp,
                            color = if (listening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { triggerVoiceInput() },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("voice_mic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Tap to Speak",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (listening) "LISTENING... SPEAK NOW" else "Tap Mic to Answer by Voice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                if (recognized.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Heard: \"$recognized\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                AnimatedVisibility(visible = feedbackText != null) {
                    feedbackText?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (it.startsWith("Correct")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Manual Answer Fallback Area (in case voice recognition is tricky in environment)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        placeholder = { Text("Or type answer...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_answer_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualText.isNotBlank()) {
                                verifySpokenAnswer(manualText)
                                manualText = ""
                            }
                        },
                        modifier = Modifier.testTag("submit_manual_answer_button")
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}
