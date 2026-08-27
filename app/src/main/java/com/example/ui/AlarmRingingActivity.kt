package com.example.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.challenge.AudioChallengeActivity
import com.example.challenge.CameraChallengeActivity
import com.example.challenge.MathChallengeActivity
import com.example.challenge.PuzzleChallengeActivity
import com.example.challenge.QrChallengeActivity
import com.example.challenge.ShakeChallengeActivity
import com.example.challenge.StepsChallengeActivity
import com.example.data.ChallengeType
import com.example.service.AlarmReceiver
import com.example.service.AlarmRingingService
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {

    private var alarmId: Int = -1
    private var alarmLabel: String = "Wake Up!"
    private var challengeTypeStr: String = ChallengeType.MATH.name
    private var refLabels: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
        alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Wake Up!"
        challengeTypeStr = intent.getStringExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE) ?: ChallengeType.MATH.name
        refLabels = intent.getStringExtra(AlarmReceiver.EXTRA_REF_LABELS)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@AlarmRingingActivity,
                    "Alarm is locked! You must start and complete the 3-stage challenge.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        setContent {
            MyApplicationTheme {
                AlarmRingingScreen(
                    alarmLabel = alarmLabel,
                    challengeType = try { ChallengeType.valueOf(challengeTypeStr) } catch (e: Exception) { ChallengeType.MATH },
                    onStartChallenge = { startChallengeFlow() }
                )
            }
        }
    }

    private fun startChallengeFlow() {
        // 1. Silence alarm sound in AlarmRingingService (keeps service alive to track state!)
        AlarmRingingService.silenceForChallenge(this)

        // 2. Launch the corresponding challenge activity
        val type = try { ChallengeType.valueOf(challengeTypeStr) } catch (e: Exception) { ChallengeType.MATH }
        val targetClass = when (type) {
            ChallengeType.AUDIO -> AudioChallengeActivity::class.java
            ChallengeType.CAMERA -> CameraChallengeActivity::class.java
            ChallengeType.PUZZLE -> PuzzleChallengeActivity::class.java
            ChallengeType.MATH -> MathChallengeActivity::class.java
            ChallengeType.SHAKE -> ShakeChallengeActivity::class.java
            ChallengeType.STEPS -> StepsChallengeActivity::class.java
            ChallengeType.QR -> QrChallengeActivity::class.java
        }

        val intent = Intent(this, targetClass).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE, challengeTypeStr)
            putExtra(AlarmReceiver.EXTRA_REF_LABELS, refLabels)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_FORWARD_RESULT
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun AlarmRingingScreen(
    alarmLabel: String,
    challengeType: ChallengeType,
    onStartChallenge: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmPulse"
    )

    val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    val now = Date()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A) // Rich deep midnight background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Lock Info
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LOCKED: 3-STAGE DISMISSAL",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = Color(0xFFFCA5A5)
                )
            }

            // Central Branding & Alarm Clock Visual
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFEF4444), Color(0xFF7F1D1D))
                            )
                        )
                        .border(4.dp, Color(0xFFFCA5A5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.force_alarm_logo),
                        contentDescription = "ForceAlarm Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeFormat.format(now),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = amPmFormat.format(now),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Text(
                    text = dateFormat.format(now),
                    fontSize = 15.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = alarmLabel,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF38BDF8),
                    textAlign = TextAlign.Center
                )
            }

            // Challenge Requirement Info Card & Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REQUIRED CHALLENGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = challengeType.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "3-Stage Progressive • Auto-Silences on Start",
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("start_challenge_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Start Challenge (Silence Alarm)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Start",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
