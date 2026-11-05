package com.example.challenge

import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Proof that you are really awake: look at the front camera, blink (1 to 3 times) and,
 * on the harder levels, smile.
 */
class FaceCheckChallengeActivity : BaseChallengeActivity() {

    private val statusState = MutableStateFlow("Show your face to the camera")
    private val blinksState = MutableStateFlow(0)
    private val blinksNeededState = MutableStateFlow(1)
    private val needSmileState = MutableStateFlow(false)

    private var eyesWereOpen = false
    private var finished = false
    private var detector: FaceDetector? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.FACE_CHECK

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
        detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
    }

    override fun onStageStarted(stage: Int) {
        setup(stage)
    }

    override fun onResetToStage1() {
        setup(1)
    }

    private fun setup(stage: Int) {
        blinksNeededState.value = when (stage) {
            1 -> 1
            2 -> 2
            else -> 3
        }
        needSmileState.value = stage >= 2
        blinksState.value = 0
        eyesWereOpen = false
        finished = false
        statusState.value = "Show your face to the camera"
    }

    private fun processFrame(image: InputImage, done: () -> Unit) {
        val d = detector
        if (d == null || finished) {
            done()
            return
        }
        d.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                handleFace(face)
            }
            .addOnCompleteListener { done() }
    }

    private fun handleFace(face: Face?) {
        if (finished || isCompletedState.value) return
        if (face == null) {
            statusState.value = "Show your face to the camera"
            return
        }

        val left = face.leftEyeOpenProbability
        val right = face.rightEyeOpenProbability
        if (left != null && right != null) {
            val open = min(left, right) > 0.6f
            val closed = max(left, right) < 0.35f
            if (open) {
                eyesWereOpen = true
            } else if (closed && eyesWereOpen) {
                eyesWereOpen = false
                if (blinksState.value < blinksNeededState.value) blinksState.value = blinksState.value + 1
            }
        }

        val needed = blinksNeededState.value
        if (blinksState.value < needed) {
            statusState.value = "Blink slowly: ${blinksState.value} / $needed"
            return
        }

        if (!needSmileState.value) {
            finishTask()
        } else {
            statusState.value = "Now smile!"
            if ((face.smilingProbability ?: 0f) > 0.75f) finishTask()
        }
    }

    private fun finishTask() {
        if (finished) return
        finished = true
        statusState.value = "Face verified!"
        setFeedbackMessage("Face verified! You are awake.", isSuccess = true)
        completeCurrentStage()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val status by statusState.collectAsState()
        val needed by blinksNeededState.collectAsState()
        val needSmile by needSmileState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskInfoCard(
                title = "Look at the camera",
                subtitle = "Blink $needed time${if (needed > 1) "s" else ""}" + if (needSmile) " and then smile" else ""
            )

            CameraFrame(modifier = Modifier.weight(1f)) {
                MlCameraView(
                    modifier = Modifier.fillMaxSize(),
                    useFront = true,
                    analyze = { image, _, _, done -> processFrame(image, done) }
                )
            }

            Text(
                text = status,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
