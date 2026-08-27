package com.example.challenge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import com.example.service.AlarmReceiver
import com.example.service.AlarmRingingService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseChallengeActivity : ComponentActivity() {

    protected val currentStageState = MutableStateFlow(1)
    val currentStage = currentStageState.asStateFlow()

    protected val timeLeftState = MutableStateFlow(30)
    val timeLeft = timeLeftState.asStateFlow()

    protected val totalStageTimeState = MutableStateFlow(30)
    val totalStageTime = totalStageTimeState.asStateFlow()

    protected val isCompletedState = MutableStateFlow(false)
    val isCompleted = isCompletedState.asStateFlow()

    protected val timeoutNoticeState = MutableStateFlow<String?>(null)
    val timeoutNotice = timeoutNoticeState.asStateFlow()

    protected val isAlarmLoudState = MutableStateFlow(false)
    val isAlarmLoud = isAlarmLoudState.asStateFlow()

    private var stageTimer: CountDownTimer? = null
    private var abandonHandler: Handler? = null
    private var abandonRunnable: Runnable? = null
    private var isLeavingScreen = false

    protected var alarmId: Int = -1
    protected var alarmLabel: String = "Wake Up!"
    protected var refLabels: String? = null

    abstract fun getChallengeType(): ChallengeType
    abstract fun getStageTimeLimit(stage: Int): Int
    abstract fun onStageStarted(stage: Int)
    abstract fun onResetToStage1()

    @Composable
    abstract fun ChallengeContent(modifier: Modifier)

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
        refLabels = intent.getStringExtra(AlarmReceiver.EXTRA_REF_LABELS)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@BaseChallengeActivity,
                    "Locked! Complete all 3 stages to dismiss alarm.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // Ensure alarm is silenced while solving
        AlarmRingingService.silenceForChallenge(this)
        isAlarmLoudState.value = false

        setContent {
            MyApplicationTheme {
                ChallengeScreenWrapper()
            }
        }

        // Start Stage 1
        startStage(1)
    }

    protected fun startStage(stage: Int) {
        currentStageState.value = stage
        val timeLimitSec = getStageTimeLimit(stage)
        totalStageTimeState.value = timeLimitSec
        timeLeftState.value = timeLimitSec

        stageTimer?.cancel()
        stageTimer = object : CountDownTimer((timeLimitSec * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftState.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                timeLeftState.value = 0
                handleStageTimeout()
            }
        }.start()

        onStageStarted(stage)
    }

    protected fun completeCurrentStage() {
        val next = currentStageState.value + 1
        if (next <= 3) {
            timeoutNoticeState.value = "Stage ${currentStageState.value} Passed! Advancing..."
            startStage(next)
        } else {
            // Challenge Completed Fully!
            isCompletedState.value = true
            stageTimer?.cancel()
            AlarmRingingService.stopRinging(this)
            Toast.makeText(this, "Challenge Completed! Alarm dismissed.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleStageTimeout() {
        stageTimer?.cancel()
        isAlarmLoudState.value = true
        AlarmRingingService.resumeAlarm(this)
        timeoutNoticeState.value = "TIME'S UP! Alarm resumed at full volume & reset to Stage 1!"
        onResetToStage1()
        startStage(1)
    }

    fun reSilenceAlarm() {
        AlarmRingingService.silenceForChallenge(this)
        isAlarmLoudState.value = false
        timeoutNoticeState.value = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCompletedState.value) return

        isLeavingScreen = true
        // 3-second grace period for accidental home/recents touch
        abandonHandler = Handler(Looper.getMainLooper())
        abandonRunnable = Runnable {
            if (isLeavingScreen && !isCompletedState.value) {
                // Grace period expired!
                AlarmRingingService.resumeAlarm(this@BaseChallengeActivity)
                isAlarmLoudState.value = true
                timeoutNoticeState.value = "Abandoned! Alarm resumed & reset to Stage 1!"
                onResetToStage1()
                startStage(1)

                // Forcibly bring challenge activity back to foreground
                val bringToFrontIntent = Intent(applicationContext, this@BaseChallengeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(bringToFrontIntent)
            }
        }
        abandonHandler?.postDelayed(abandonRunnable!!, 3000)
    }

    override fun onResume() {
        super.onResume()
        isLeavingScreen = false
        // Returned within grace period -> cancel penalty countdown!
        abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
        abandonRunnable = null
        abandonHandler = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stageTimer?.cancel()
        abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
    }

    @Composable
    private fun ChallengeScreenWrapper() {
        val stage by currentStage.collectAsState()
        val remainingSec by timeLeft.collectAsState()
        val totalSec by totalStageTime.collectAsState()
        val completed by isCompleted.collectAsState()
        val notice by timeoutNotice.collectAsState()
        val isLoud by isAlarmLoud.collectAsState()

        val progress = if (totalSec > 0) (remainingSec.toFloat() / totalSec.toFloat()) else 0f
        val isUrgent = remainingSec <= 10 && remainingSec > 0

        val pulseAnim = rememberInfiniteTransition(label = "pulse")
        val pulseScale by pulseAnim.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isUrgent || isLoud) 1.08f else 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header Bar with 3-Stage Stepper
                ChallengeHeader(
                    challengeType = getChallengeType(),
                    currentStage = stage,
                    remainingSec = remainingSec,
                    totalSec = totalSec,
                    progress = progress,
                    isUrgent = isUrgent,
                    isLoud = isLoud,
                    pulseScale = pulseScale,
                    onReSilence = { reSilenceAlarm() }
                )

                // Urgent Notice / Timeout Warning Banner
                AnimatedVisibility(visible = notice != null) {
                    notice?.let { msg ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLoud) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isLoud) Icons.Default.Warning else Icons.Default.Check,
                                    contentDescription = "Alert",
                                    tint = if (isLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLoud) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Specific Challenge Body
                if (completed) {
                    ChallengeSuccessView {
                        finish()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ChallengeContent(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengeHeader(
    challengeType: ChallengeType,
    currentStage: Int,
    remainingSec: Int,
    totalSec: Int,
    progress: Float,
    isUrgent: Boolean,
    isLoud: Boolean,
    pulseScale: Float,
    onReSilence: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = challengeType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "3-Stage Progressive Challenge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Silence/Loud indicator
                if (isLoud) {
                    Button(
                        onClick = onReSilence,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("re_silence_button")
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Silence", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-Silence", fontSize = 12.sp)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Silent Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Silent Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Stage Progress Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StageStepItem(
                    stageNumber = 1,
                    title = "Low",
                    isCurrent = currentStage == 1,
                    isPassed = currentStage > 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                StageStepItem(
                    stageNumber = 2,
                    title = "Moderate",
                    isCurrent = currentStage == 2,
                    isPassed = currentStage > 2,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                StageStepItem(
                    stageNumber = 3,
                    title = "Advanced",
                    isCurrent = currentStage == 3,
                    isPassed = currentStage > 3,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timer Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = if (isUrgent || isLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Time Limit: ${remainingSec}s left",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUrgent || isLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.scale(if (isUrgent || isLoud) pulseScale else 1.0f)
                    )
                }
                Text(
                    text = "Total ${totalSec}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val progressColor by animateColorAsState(
                targetValue = if (isUrgent || isLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                label = "progressColor"
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun StageStepItem(
    stageNumber: Int,
    title: String,
    isCurrent: Boolean,
    isPassed: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isPassed -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isPassed -> MaterialTheme.colorScheme.onPrimary
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(width = if (isCurrent) 2.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPassed) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Passed",
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = "Stage $stageNumber",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            Text(
                text = title,
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun ChallengeSuccessView(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "YOU ARE AWAKE!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "All 3 challenge stages completed successfully. Alarm has been silenced and stopped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("dismiss_success_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Dismiss & Good Morning", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
