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
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.OceanBrush
import com.example.ui.theme.OceanCyan
import com.example.ui.theme.OceanGradientButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.challenge.BaseChallengeActivity
import com.example.challenge.ComboCatalog
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.challenge.CardArrangementChallengeActivity
import com.example.challenge.ChargerChallengeActivity
import com.example.challenge.ImageArrangementChallengeActivity
import com.example.challenge.MathChallengeActivity
import com.example.challenge.OddOneOutChallengeActivity
import com.example.challenge.PatternLockChallengeActivity
import com.example.challenge.QrChallengeActivity
import com.example.challenge.ReactionGameChallengeActivity
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
    private var alarmLabel: String = "Force Alarm"
    private var challengeTypeStr: String = ChallengeType.MATH.name
    private var refLabels: String? = null
    private lateinit var softWake: SoftWakeBrightness

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
        alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Force Alarm"
        challengeTypeStr = intent.getStringExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE) ?: ChallengeType.MATH.name
        refLabels = intent.getStringExtra(AlarmReceiver.EXTRA_REF_LABELS)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Locked until completed
            }
        })

        softWake = SoftWakeBrightness(this)
        softWake.start()

        // Once the full-screen alarm is showing, the notification can become small and quiet
        window.decorView.postDelayed({ AlarmRingingService.onScreenShown(this) }, 1500L)

        setContent {
            // Always dark at wake-up time: a bright white screen is hard on sleepy eyes
            MyApplicationTheme(forceDark = true) {
                AlarmRingingScreen(
                    alarmLabel = alarmLabel,
                    challengeType = try { ChallengeType.valueOf(challengeTypeStr) } catch (e: Exception) { ChallengeType.MATH },
                    onStartChallenge = { startChallengeFlow() },
                    onEscape = {
                        AlarmRingingService.stopAlarm(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        softWake.stop()
    }

    private fun startChallengeFlow() {
        AlarmRingingService.silenceForChallenge(this)

        val type = try { ChallengeType.valueOf(challengeTypeStr) } catch (e: Exception) { ChallengeType.MATH }

        if (type == ChallengeType.COMBO) {
            // The combo to run is stored with the alarm
            lifecycleScope.launch {
                val comboId = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).alarmDao().getAlarmById(alarmId)?.comboId ?: 0
                }
                val combo = ComboCatalog.byId(comboId) ?: ComboCatalog.byId(1)!!
                launchChallenge(combo.steps.first().type, combo.id)
            }
        } else {
            launchChallenge(type, 0)
        }
    }

    private fun launchChallenge(type: ChallengeType, comboId: Int) {
        val intent = Intent(this, ComboCatalog.activityClass(type)).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE, challengeTypeStr)
            putExtra(AlarmReceiver.EXTRA_REF_LABELS, refLabels)
            if (comboId > 0) {
                putExtra(BaseChallengeActivity.EXTRA_COMBO_ID, comboId)
                putExtra(BaseChallengeActivity.EXTRA_FORCE_STEP, 1)
            }
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
    onEscape: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmPulse"
    )

    val timeFormat = SimpleDateFormat("hh:mm", Locale.getDefault())
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val small = maxHeight < 440.dp
                val ringSize = minOf(maxHeight * 0.42f, 250.dp).coerceAtLeast(120.dp)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rise and shine",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeFormat.format(now),
                        fontSize = if (small) 52.sp else 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = dateFormat.format(now),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (alarmLabel.isNotBlank()) {
                        Text(
                            text = alarmLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OceanCyan,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(if (small) 10.dp else 20.dp))

                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFF0D2442)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ringSize * 0.82f)
                                .clip(CircleShape)
                                .background(Color(0xFF0A3A58)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ringSize * 0.64f)
                                    .clip(CircleShape)
                                    .background(OceanBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Alarm bell",
                                    tint = Color.White,
                                    modifier = Modifier.size(ringSize * 0.28f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No snooze \u00B7 No dismiss",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Task: ${challengeType.displayName}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))

                OceanGradientButton(
                    text = "Start task",
                    onClick = onStartChallenge,
                    modifier = Modifier.testTag("start_challenge_button")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmergencyEscapeButton(onEscape = onEscape)
                    TestExitButton(onExit = onEscape)
                }
            }
        }
    }
}
