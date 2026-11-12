package com.example.challenge

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Place + camera: show a chosen object (for example the toothbrush cup or a bottle in the
 * bathroom) to the back camera. The object names come from the alarm settings; without them
 * a common object is picked at random.
 */
class ObjectPhotoChallengeActivity : BaseChallengeActivity() {

    private val targetNameState = MutableStateFlow("")
    private val seenState = MutableStateFlow("")
    private val statusState = MutableStateFlow("Point the camera at it")

    private var keywords: List<String> = emptyList()
    private var hitCount = 0
    private var lastAnalyzed = 0L
    private var finished = false
    private var labeler: ImageLabeler? = null

    private val defaultTargets = listOf(
        "a bottle" to listOf("bottle", "water bottle", "plastic bottle", "glass bottle", "drinkware"),
        "a cup or mug" to listOf("cup", "mug", "coffee cup", "teacup"),
        "a shoe" to listOf("shoe", "footwear", "sneaker", "sandal"),
        "a plant" to listOf("plant", "flower", "leaf"),
        "a laptop" to listOf("laptop", "netbook", "personal computer", "computer"),
        "a book" to listOf("book", "paper"),
        "a bag" to listOf("bag", "handbag", "backpack", "luggage")
    )

    override fun getChallengeType(): ChallengeType = ChallengeType.OBJECT_PHOTO

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 90
        else -> 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!canUseCamera()) {
            fallbackTo(MathChallengeActivity::class.java, "Camera not available. Switching to Math.")
            return
        }
        labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        labeler?.close()
    }

    override fun onStageStarted(stage: Int) {
        pickTarget()
    }

    override fun onResetToStage1() {
        pickTarget()
    }

    private fun pickTarget() {
        val custom = (refLabels ?: "")
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (custom.isNotEmpty()) {
            val chosen = custom.random()
            targetNameState.value = chosen
            keywords = listOf(chosen)
        } else {
            val (name, words) = defaultTargets.random()
            targetNameState.value = name
            keywords = words
        }
        hitCount = 0
        finished = false
        seenState.value = ""
        statusState.value = "Point the camera at it"
    }

    private fun processFrame(image: InputImage, done: () -> Unit) {
        val l = labeler
        val now = SystemClock.elapsedRealtime()
        if (l == null || finished || now - lastAnalyzed < 600L) {
            done()
            return
        }
        lastAnalyzed = now
        l.process(image)
            .addOnSuccessListener { labels -> handleLabels(labels) }
            .addOnCompleteListener { done() }
    }

    private fun handleLabels(labels: List<ImageLabel>) {
        if (finished || isCompletedState.value) return
        seenState.value = if (labels.isEmpty()) "" else "I see: " + labels.take(4).joinToString(", ") { it.text }

        val match = labels.any { label ->
            val text = label.text.lowercase()
            keywords.any { k -> text.contains(k) || k.contains(text) }
        }
        hitCount = if (match) hitCount + 1 else 0
        if (hitCount >= 2) {
            finished = true
            statusState.value = "Found it!"
            setFeedbackMessage("Object found!", isSuccess = true)
            completeCurrentStage()
        } else {
            statusState.value = if (match) "Hold it steady..." else "Keep looking"
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val target by targetNameState.collectAsState()
        val seen by seenState.collectAsState()
        val status by statusState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskInfoCard(
                title = "Show $target",
                subtitle = "Find it and point the back camera at it"
            )

            CameraFrame(modifier = Modifier.weight(1f)) {
                MlCameraView(
                    modifier = Modifier.fillMaxSize(),
                    useFront = false,
                    analyze = { image, _, _, done -> processFrame(image, done) }
                )
            }

            Text(
                text = status,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = seen,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
