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
import androidx.compose.material.icons.filled.Info
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

    data class ChallengeFeedback(
        val message: String,
        val isError: Boolean = false,
        val isSuccess: Boolean = false
    )

    protected val timeoutNoticeState = MutableStateFlow<String?>(null)
    val timeoutNotice = timeoutNoticeState.asStateFlow()

    protected val feedbackMessageState = MutableStateFlow<ChallengeFeedback?>(null)
    val feedbackMessage = feedbackMessageState.asStateFlow()

    protected val isAlarmLoudState = MutableStateFlow(false)
    val isAlarmLoud = isAlarmLoudState.asStateFlow()

    fun setFeedbackMessage(message: String?, isError: Boolean = false, isSuccess: Boolean = false) {
        feedbackMessageState.value = if (message != null) {
            ChallengeFeedback(message, isError, isSuccess)
        } else {
            null
        }
    }

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
    open fun getTotalStages(): Int = 3

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
        if (next <= getTotalStages()) {
            setFeedbackMessage("Stage ${currentStageState.value} Passed! Advancing...", isSuccess = true)
            startStage(next)
        } else {
            // Challenge Completed Fully!
            isCompletedState.value = true
            stageTimer?.cancel()
            AlarmRingingService.stopRinging(this)
            setFeedbackMessage("Challenge Completed! Alarm dismissed.", isSuccess = true)
            Toast.makeText(this, "Challenge Completed! Alarm dismissed.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleStageTimeout() {
        stageTimer?.cancel()
        isAlarmLoudState.value = true
        AlarmRingingService.resumeAlarm(this)
        setFeedbackMessage("TIME'S UP! Alarm resumed & reset to Stage 1!", isError = true)
        onResetToStage1()
        startStage(1)
    }

    fun reSilenceAlarm() {
        AlarmRingingService.silenceForChallenge(this)
        isAlarmLoudState.value = false
        setFeedbackMessage(null)
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
                setFeedbackMessage("Abandoned! Alarm resumed & reset to Stage 1!", isError = true)
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
        val feedback by feedbackMessage.collectAsState()
        val isLoud by isAlarmLoud.collectAsState()

        val activeNotice = feedback?.message ?: notice
        val isErrorNotice = feedback?.isError == true || isLoud
        val isSuccessNotice = feedback?.isSuccess == true && !isLoud

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
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Section 1: Fixed-Height Compact Header
                ChallengeHeader(
                    challengeType = getChallengeType(),
                    currentStage = stage,
                    totalStages = getTotalStages(),
                    remainingSec = remainingSec,
                    totalSec = totalSec,
                    progress = progress,
                    isUrgent = isUrgent,
                    isLoud = isLoud,
                    pulseScale = pulseScale,
                    onReSilence = { reSilenceAlarm() }
                )

                // Section 2: Reserved Fixed-Height Message Slot (Never shifts or pushes layout)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeNotice != null) {
                        val bgColor = when {
                            isErrorNotice -> MaterialTheme.colorScheme.errorContainer
                            isSuccessNotice -> Color(0xFF065F46)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val contentColor = when {
                            isErrorNotice -> MaterialTheme.colorScheme.onErrorContainer
                            isSuccessNotice -> Color(0xFFD1FAE5)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        val iconColor = when {
                            isErrorNotice -> MaterialTheme.colorScheme.error
                            isSuccessNotice -> Color(0xFF10B981)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val iconVector = when {
                            isErrorNotice -> Icons.Default.Warning
                            isSuccessNotice -> Icons.Default.Check
                            else -> Icons.Default.Info
                        }

                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeNotice,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }

                // Section 3: Main Play Area (weight 1f, strictly non-scrollable)
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
    totalStages: Int = 3,
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
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = challengeType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                // Silence/Loud indicator
                if (isLoud) {
                    Button(
                        onClick = onReSilence,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("re_silence_button")
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Silence", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Re-Silence", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Silent Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Silent",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Stage Progress Indicator Row
            if (totalStages == 1) {
                StageStepItem(
                    stageNumber = 1,
                    title = "Single Action",
                    isCurrent = true,
                    isPassed = false,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
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
                    Spacer(modifier = Modifier.width(4.dp))
                    StageStepItem(
                        stageNumber = 2,
                        title = "Moderate",
                        isCurrent = currentStage == 2,
                        isPassed = currentStage > 2,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    StageStepItem(
                        stageNumber = 3,
                        title = "Advanced",
                        isCurrent = currentStage == 3,
                        isPassed = currentStage > 3,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timer Bar Row
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
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${remainingSec}s left",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUrgent || isLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.scale(if (isUrgent || isLoud) pulseScale else 1.0f)
                    )
                }
                Text(
                    text = "Total ${totalSec}s",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            val progressColor by animateColorAsState(
                targetValue = if (isUrgent || isLoud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                label = "progressColor"
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
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
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(width = if (isCurrent) 1.5.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 3.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isPassed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Passed",
                    tint = textColor,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = "S$stageNumber: $title",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
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
