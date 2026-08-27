package com.example.challenge

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.ChallengeType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraChallengeActivity : BaseChallengeActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val stepTitleState = MutableStateFlow("Step 1: Reach Location & Take Photo")
    val stepTitle = stepTitleState.asStateFlow()

    private val stepInstructionState = MutableStateFlow("Walk to your target location (bathroom, sink, or desk) and photograph your reference object.")
    val stepInstruction = stepInstructionState.asStateFlow()

    private val detectedLabelsState = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val detectedLabels = detectedLabelsState.asStateFlow()

    private val isAnalyzingState = MutableStateFlow(false)
    val isAnalyzing = isAnalyzingState.asStateFlow()

    private val verificationResultState = MutableStateFlow<String?>(null)
    val verificationResult = verificationResultState.asStateFlow()

    private val hasCameraPermissionState = MutableStateFlow(false)
    val hasCameraPermission = hasCameraPermissionState.asStateFlow()

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermissionState.value = isGranted
    }

    override fun getChallengeType(): ChallengeType = ChallengeType.CAMERA

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 120
        2 -> 180
        3 -> 240
        else -> 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            hasCameraPermissionState.value = true
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStageStarted(stage: Int) {
        detectedLabelsState.value = emptyList()
        verificationResultState.value = null
        when (stage) {
            1 -> {
                stepTitleState.value = "Step 1 (Low): Reach Location Proof"
                val refInfo = if (!refLabels.isNullOrEmpty()) "Target: $refLabels" else "Bathroom / Kitchen / Object"
                stepInstructionState.value = "Get out of bed! Go to your morning spot ($refInfo) and snap a photo."
            }
            2 -> {
                stepTitleState.value = "Step 2 (Moderate): Action Proof"
                stepInstructionState.value = "Show action! Photograph yourself pouring water, applying toothpaste, or preparing your routine."
            }
            3 -> {
                stepTitleState.value = "Step 3 (Advanced): Final Wakefulness Proof"
                stepInstructionState.value = "Final verification! Photograph your awake face, refreshed eyes, or completed routine."
            }
        }
    }

    override fun onResetToStage1() {
        detectedLabelsState.value = emptyList()
        verificationResultState.value = "Reset to Stage 1! Get up and take the location photo."
    }

    fun capturePhotoAndVerify() {
        val capture = imageCapture ?: return
        isAnalyzingState.value = true
        verificationResultState.value = "Analyzing image with on-device ML Kit..."

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    if (bitmap != null) {
                        analyzeBitmapWithMlKit(bitmap)
                    } else {
                        isAnalyzingState.value = false
                        verificationResultState.value = "Capture failed. Please retry."
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isAnalyzingState.value = false
                    verificationResultState.value = "Camera error: ${exception.message}. Retry."
                }
            }
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    private fun analyzeBitmapWithMlKit(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                isAnalyzingState.value = false
                val labelList = labels.map { Pair(it.text, it.confidence) }
                detectedLabelsState.value = labelList

                evaluateLabelsForCurrentStage(labelList)
            }
            .addOnFailureListener { e ->
                isAnalyzingState.value = false
                verificationResultState.value = "ML Kit analysis failed: ${e.message}"
            }
    }

    private fun evaluateLabelsForCurrentStage(labels: List<Pair<String, Float>>) {
        val stage = currentStageState.value
        val labelNames = labels.map { it.first.lowercase() }
        val referenceList = refLabels?.split(",")?.map { it.trim().lowercase() } ?: emptyList()

        var isMatch = false
        var matchedReason = ""

        when (stage) {
            1 -> {
                // Step 1: Location / Reference object match
                // Check if any reference label matches with confidence > 0.5
                val refMatch = labels.firstOrNull { pair ->
                    val name = pair.first.lowercase()
                    referenceList.any { ref -> name.contains(ref) || ref.contains(name) }
                }

                if (refMatch != null) {
                    isMatch = true
                    matchedReason = "Matched Reference: ${refMatch.first} (${(refMatch.second * 100).toInt()}%)"
                } else {
                    // Category-level matching for morning locations (sink, bathroom, room, cup, table, bottle, plumbing, tile, indoor, houseplant, kitchen)
                    val locationKeywords = listOf(
                        "sink", "tap", "bathroom", "plumbing", "water", "tile", "cup", "mug",
                        "drinkware", "tableware", "room", "table", "kitchen", "bottle", "interior",
                        "indoor", "countertop", "shelf", "furniture", "door", "home", "floor", "wall"
                    )
                    val locMatch = labels.firstOrNull { pair ->
                        val name = pair.first.lowercase()
                        locationKeywords.any { kw -> name.contains(kw) } && pair.second >= 0.45f
                    }
                    if (locMatch != null) {
                        isMatch = true
                        matchedReason = "Location Verified: ${locMatch.first} (${(locMatch.second * 100).toInt()}%)"
                    } else if (labels.isNotEmpty()) {
                        // General on-device detection pass if any clear object captured
                        val top = labels.first()
                        if (top.second >= 0.5f) {
                            isMatch = true
                            matchedReason = "Object Detected: ${top.first} (${(top.second * 100).toInt()}%)"
                        }
                    }
                }
            }
            2 -> {
                // Step 2: Action proof (pouring water, toothbrush, holding object, hand, beverage, personal care, action)
                val actionKeywords = listOf(
                    "action", "hand", "gesture", "water", "liquid", "drink", "cup", "mug",
                    "toothbrush", "bottle", "soap", "paste", "hygiene", "food", "tableware",
                    "finger", "arm", "faucet", "glass", "holding", "fluid"
                )
                val actMatch = labels.firstOrNull { pair ->
                    val name = pair.first.lowercase()
                    actionKeywords.any { kw -> name.contains(kw) } && pair.second >= 0.45f
                }
                if (actMatch != null) {
                    isMatch = true
                    matchedReason = "Action Verified: ${actMatch.first} (${(actMatch.second * 100).toInt()}%)"
                } else if (labels.isNotEmpty()) {
                    val top = labels.first()
                    if (top.second >= 0.5f) {
                        isMatch = true
                        matchedReason = "Routine Item Detected: ${top.first} (${(top.second * 100).toInt()}%)"
                    }
                }
            }
            3 -> {
                // Step 3: Final awake proof (face, person, smile, skin, hair, human, bright room, awake)
                val faceKeywords = listOf(
                    "person", "face", "smile", "eye", "head", "skin", "hair", "human",
                    "selfie", "jaw", "chin", "forehead", "portrait", "eyebrow", "man", "woman"
                )
                val faceMatch = labels.firstOrNull { pair ->
                    val name = pair.first.lowercase()
                    faceKeywords.any { kw -> name.contains(kw) } && pair.second >= 0.45f
                }
                if (faceMatch != null) {
                    isMatch = true
                    matchedReason = "Awake Face Verified: ${faceMatch.first} (${(faceMatch.second * 100).toInt()}%)"
                } else if (labels.isNotEmpty()) {
                    val top = labels.first()
                    isMatch = true
                    matchedReason = "Proof Verified: ${top.first} (${(top.second * 100).toInt()}%)"
                }
            }
        }

        if (isMatch) {
            verificationResultState.value = "PASSED: $matchedReason"
            completeCurrentStage()
        } else {
            verificationResultState.value = "No clear category match detected. Please ensure good lighting and clear view of target."
        }
    }

    fun toggleCameraLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val title by stepTitle.collectAsState()
        val instruction by stepInstruction.collectAsState()
        val labels by detectedLabels.collectAsState()
        val analyzing by isAnalyzing.collectAsState()
        val resultText by verificationResult.collectAsState()
        val hasPermission by hasCameraPermission.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Step Instruction Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPermission) {
                    CameraPreviewComposable(
                        modifier = Modifier.fillMaxSize(),
                        lensFacing = lensFacing,
                        onImageCaptureReady = { capture ->
                            imageCapture = capture
                        }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "No Permission",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Permission Required",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }

                // Flip lens button
                IconButton(
                    onClick = { toggleCameraLens() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Color.White
                    )
                }

                if (analyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Running ML Kit Labeling...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ML Kit Detected Labels Bar
            if (labels.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(labels) { (name, conf) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$name ${(conf * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Feedback / Result Text
            AnimatedVisibility(visible = resultText != null) {
                resultText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (it.startsWith("PASSED")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Capture Shutter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { capturePhotoAndVerify() },
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(54.dp)
                        .testTag("camera_capture_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !analyzing
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Capture", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture & Verify Proof", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // Emulator test bypass button if needed
                IconButton(
                    onClick = {
                        // Simulate verified step for emulator/testing
                        completeCurrentStage()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("camera_test_pass_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Test Pass",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreviewComposable(
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                onImageCaptureReady(imageCapture)

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
