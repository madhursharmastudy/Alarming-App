package com.example.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.challenge.AudioChallengeActivity
import com.example.challenge.BaseChallengeActivity
import com.example.challenge.CameraChallengeActivity
import com.example.challenge.CardArrangementChallengeActivity
import com.example.challenge.ChargerChallengeActivity
import com.example.challenge.ImageArrangementChallengeActivity
import com.example.challenge.MathChallengeActivity
import com.example.challenge.OddOneOutChallengeActivity
import com.example.challenge.PatternLockChallengeActivity
import com.example.challenge.QrChallengeActivity
import com.example.challenge.ReactionGameChallengeActivity
import com.example.challenge.SHOW_TEST_STOP_BUTTON
import com.example.challenge.ShakeChallengeActivity
import com.example.challenge.StepsChallengeActivity
import com.example.challenge.TypingChallengeActivity
import com.example.data.ChallengeType
import com.example.service.AlarmReceiver
import com.example.service.AlarmRingingService
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {

    private var alarmId: Int = -1
    private var alarmLabel: String = "Aurum Alarm"
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
        alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Aurum Alarm"
        challengeTypeStr = intent.getStringExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE) ?: ChallengeType.MATH.name
        refLabels = intent.getStringExtra(AlarmReceiver.EXTRA_REF_LABELS)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Locked until completed
            }
        })

        setContent {
            MyApplicationTheme {
                AlarmRingingScreen(
                    alarmLabel = alarmLabel,
                    challengeType = try { ChallengeType.valueOf(challengeTypeStr) } catch (e: Exception) { ChallengeType.MATH },
                    onStartChallenge = { startChallengeFlow() },
                    onTestStopAlarm = {
                        // TEMPORARY TEST CODE - REMOVE LATER
                        AlarmRingingService.stopAlarm(this@AlarmRingingActivity)
                        finish()
                    }
                )
            }
        }
    }

    private fun startChallengeFlow() {
        AlarmRingingService.silenceForChallenge(this)

        val type = try { ChallengeType.valueOf(challengeTypeStr) } catch (e: Exception) { ChallengeType.MATH }
        val targetClass = when (type) {
            ChallengeType.AUDIO -> AudioChallengeActivity::class.java
            ChallengeType.CAMERA -> CameraChallengeActivity::class.java
            ChallengeType.TYPING -> TypingChallengeActivity::class.java
            ChallengeType.IMAGE_ARRANGEMENT -> ImageArrangementChallengeActivity::class.java
            ChallengeType.CHARGER -> ChargerChallengeActivity::class.java
            ChallengeType.CARD_ARRANGEMENT -> CardArrangementChallengeActivity::class.java
            ChallengeType.PATTERN_LOCK -> PatternLockChallengeActivity::class.java
            ChallengeType.ODD_ONE_OUT -> OddOneOutChallengeActivity::class.java
            ChallengeType.REACTION_GAME -> ReactionGameChallengeActivity::class.java
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
    onStartChallenge: () -> Unit,
    onTestStopAlarm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
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
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Lock Pill and Temporary Test Stop Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (challengeType == ChallengeType.CHARGER) "LOCKED: ACTION REQUIRED" else "LOCKED: 3-STAGE DISMISSAL",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // TEMPORARY TEST CODE - REMOVE LATER
                if (SHOW_TEST_STOP_BUTTON) {
                    OutlinedButton(
                        onClick = onTestStopAlarm,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("test_stop_alarm_button")
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "TEST: Stop Alarm",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Central Branding & Alarm Clock Visual (weight 1f, strictly non-scrollable)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val totalH = maxHeight
                val iconBoxSize = minOf(totalH * 0.28f, 110.dp).coerceAtLeast(60.dp)
                val clockSize = if (totalH < 300.dp) 38.sp else 48.sp
                val amPmSize = if (totalH < 300.dp) 16.sp else 20.sp

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Simple vector alarm-bell symbol drawn in the theme's main color
                    Box(
                        modifier = Modifier
                            .size(iconBoxSize)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Aurum Alarm Bell",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(iconBoxSize * 0.52f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = timeFormat.format(now),
                            fontSize = clockSize,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = amPmFormat.format(now),
                            fontSize = amPmSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = dateFormat.format(now),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = alarmLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // Challenge Requirement Info Card & Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REQUIRED CHALLENGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = challengeType.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (challengeType == ChallengeType.CHARGER) "Physical Sensor Action • Auto-Silences on Start" else "3-Stage Progressive • Auto-Silences on Start",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onStartChallenge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_challenge_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Start Challenge (Silence Alarm)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Start",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
