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

/**
 * Squats counted by the front camera: stand the phone upright on a table about 2 metres away,
 * keep your face in view and squat. A squat is counted when your head goes down and comes back up.
 */
class SquatChallengeActivity : BaseChallengeActivity() {

    private val statusState = MutableStateFlow("Stand in view of the camera")
    private val squatsState = MutableStateFlow(0)
    private val targetState = MutableStateFlow(3)

    private val samples = ArrayList<Float>()
    private var baseline = -1f
    private var isDown = false
    private var downFrames = 0
    private var finished = false
    private var detector: FaceDetector? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.SQUAT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 90
        2 -> 120
        else -> 150
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!canUseCamera()) {
            fallbackTo(StepsChallengeActivity::class.java, "Camera not available. Switching to Steps.")
            return
        }
        detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
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
        targetState.value = when (stage) {
            1 -> 3
            2 -> 5
            else -> 8
        }
        squatsState.value = 0
        samples.clear()
        baseline = -1f
        isDown = false
        downFrames = 0
        finished = false
        statusState.value = "Stand in view of the camera"
    }

    private fun processFrame(image: InputImage, frameHeight: Int, done: () -> Unit) {
        val d = detector
        if (d == null || finished) {
            done()
            return
        }
        d.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                handleFace(face, frameHeight)
            }
            .addOnCompleteListener { done() }
    }

    private fun handleFace(face: Face?, frameHeight: Int) {
        if (finished || isCompletedState.value || frameHeight <= 0) return
        if (face == null) {
            statusState.value = if (baseline < 0f) "Stand in view of the camera" else "Keep your face in view"
            return
        }

        val y = face.boundingBox.centerY().toFloat() / frameHeight

        // First: learn where your head is while standing
        if (baseline < 0f) {
            samples.add(y)
            statusState.value = "Stand still... ${samples.size} / 20"
            if (samples.size >= 20) {
                baseline = samples.average().toFloat()
                statusState.value = "Squat down!"
            }
            return
        }

        val drop = y - baseline
        if (!isDown) {
            if (drop > 0.09f) {
                downFrames++
                if (downFrames >= 2) {
                    isDown = true
                    statusState.value = "Now stand up!"
                }
            } else {
                downFrames = 0
            }
        } else if (drop < 0.03f) {
            isDown = false
            downFrames = 0
            val count = squatsState.value + 1
            squatsState.value = count
            if (count >= targetState.value) {
                finished = true
                statusState.value = "All squats done!"
                setFeedbackMessage("Squats completed!", isSuccess = true)
                completeCurrentStage()
            } else {
                statusState.value = "Squat down!"
            }
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val status by statusState.collectAsState()
        val squats by squatsState.collectAsState()
        val target by targetState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskInfoCard(
                title = "Squats: $squats / $target",
                subtitle = "Stand the phone upright about 2 m away. Keep your face in view."
            )

            CameraFrame(modifier = Modifier.weight(1f)) {
                MlCameraView(
                    modifier = Modifier.fillMaxSize(),
                    useFront = true,
                    analyze = { image, _, h, done -> processFrame(image, h, done) }
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
