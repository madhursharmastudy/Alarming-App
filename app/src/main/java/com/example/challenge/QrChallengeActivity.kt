package com.example.challenge

import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.ChallengeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real QR / barcode challenge: the user must scan the QR code registered for this alarm
 * (for example one kept in the bathroom) with the camera. Single stage, 2 minutes.
 */
class QrChallengeActivity : BaseChallengeActivity() {

    private val scanReadyState = MutableStateFlow(false)

    // Registered code for this alarm. null = accept any code (test mode / old alarms)
    private var expectedQr: String? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.QR

    // Single stage: the user has to get up and reach the QR code
    override fun getTotalStages(): Int = 1

    override fun getStageTimeLimit(stage: Int): Int = 120

    override fun onStageStarted(stage: Int) {}

    override fun onResetToStage1() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCamera || !hasPermission) {
            // Safety net: never leave the user stuck if the camera cannot be used
            startFallbackChallenge()
            return
        }

        if (alarmId > 0) {
            lifecycleScope.launch {
                val code = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).alarmDao().getAlarmById(alarmId)?.qrCode
                }
                expectedQr = code?.takeIf { it.isNotBlank() }
                scanReadyState.value = true
            }
        } else {
            scanReadyState.value = true
        }
    }

    private fun startFallbackChallenge() {
        Toast.makeText(
            this,
            "Camera not available. Switching to Steps challenge.",
            Toast.LENGTH_LONG
        ).show()
        val next = Intent(this, StepsChallengeActivity::class.java).apply {
            putExtras(intent)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        startActivity(next)
        finish()
    }

    private fun onCodeScanned(value: String) {
        if (!scanReadyState.value || isCompletedState.value) return
        val expected = expectedQr
        if (expected == null || value.trim() == expected.trim()) {
            completeCurrentStage()
        } else {
            setFeedbackMessage("Wrong code! Scan your registered QR.", isError = true)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val ready by scanReadyState.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Scan your QR code",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You must get out of bed. Go to your registered QR code (for example in the bathroom) and scan it.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black)
                    .testTag("qr_camera_preview"),
                contentAlignment = Alignment.Center
            ) {
                if (ready) {
                    QrScannerView(
                        modifier = Modifier.fillMaxSize(),
                        onCodeScanned = { onCodeScanned(it) }
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Point the camera at the QR code",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
