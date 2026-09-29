package com.example.challenge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

// TEMPORARY TEST CODE - REMOVE LATER
const val SHOW_TEST_STOP_BUTTON = true

/**
 * Base Activity providing:
 * - 3-stage progression logic with timer per stage
 * - Idempotent State Machine: Ringing -> Challenge(1..3) -> Completed (terminal)
 * - Automatic alarm silencing during challenge solving
 * - 3-second grace period on home/recents press before resuming alarm & resetting to stage 1
 * - Reserved fixed-height header, message slot, and non-scrollable play area
 * - Two-color theme consistency (Gold & Background)
 */
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

    private val feedbackHandler = Handler(Looper.getMainLooper())
    private var feedbackClearRunnable: Runnable? = null

    fun setFeedbackMessage(message: String?, isError: Boolean = false, isSuccess: Boolean = false) {
        feedbackClearRunnable?.let { feedbackHandler.removeCallbacks(it) }
        if (message != null) {
            feedbackMessageState.value = ChallengeFeedback(message, isError, isSuccess)
            val durationMs = if (isSuccess) 1500L else 2200L
            val clearRun = Runnable {
                if (feedbackMessageState.value?.message == message) {
                    feedbackMessageState.value = null
                }
            }
            feedbackClearRunnable = clearRun
            feedbackHandler.postDelayed(clearRun, durationMs)
        } else {
            feedbackMessageState.value = null
        }
    }

    private var stageTimer: CountDownTimer? = null
    private var abandonHandler: Handler? = null
    private var abandonRunnable: Runnable? = null
    private var isLeavingScreen = false

    protected var alarmId: Int = -1
    protected var alarmLabel: String = "Aurum Alarm"
    protected var refLabels: String? = null

    abstract fun getChallengeType(): ChallengeType
    abstract fun getStageTimeLimit(stage: Int): Int
    abstract fun onStageStarted(stage: Int)
    abstract fun onResetToStage1()
    open fun getTotalStages(): Int = 3

    @Composable
    abstract fun ChallengeContent(modifier: Modifier)

    // TEMPORARY TEST CODE - REMOVE LATER
    fun testStopAlarm() {
        stageTimer?.cancel()
        abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
        abandonRunnable = null
        abandonHandler = null
        AlarmRingingService.stopAlarm(this)
        finish()
    }

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
        refLabels = intent.getStringExtra(AlarmReceiver.EXTRA_REF_LABELS)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Locked until completed
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
        if (isCompletedState.value) return

        currentStageState.value = stage
        val timeLimitSec = getStageTimeLimit(stage)
        totalStageTimeState.value = timeLimitSec
        timeLeftState.value = timeLimitSec

        stageTimer?.cancel()
        stageTimer = object : CountDownTimer((timeLimitSec * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isCompletedState.value) {
                    cancel()
                    return
                }
                timeLeftState.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                if (isCompletedState.value) return
                timeLeftState.value = 0
                handleStageTimeout()
            }
        }.start()

        onStageStarted(stage)
    }

    protected fun completeCurrentStage() {
        if (isCompletedState.value) return

        val current = currentStageState.value
        val total = getTotalStages()

        if (current < total) {
            val next = current + 1
            setFeedbackMessage("Stage $current Passed! Advancing...", isSuccess = true)
            startStage(next)
        } else {
            // Stage 3 Passed! Terminal Completed state reached.
            currentStageState.value = total + 1
            stageTimer?.cancel()
            stageTimer = null

            abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
            abandonRunnable = null
            abandonHandler = null

            feedbackClearRunnable?.let { feedbackHandler.removeCallbacks(it) }
            feedbackMessageState.value = null
            timeoutNoticeState.value = null

            isAlarmLoudState.value = false
            AlarmRingingService.stopAlarm(this)
            isCompletedState.value = true
        }
    }

    private fun handleStageTimeout() {
        if (isCompletedState.value) return

        stageTimer?.cancel()
        isAlarmLoudState.value = true
        AlarmRingingService.resumeAlarm(this)
        setFeedbackMessage("TIME'S UP! Alarm resumed & reset to Stage 1!", isError = true)
        onResetToStage1()
        startStage(1)
    }

    fun reSilenceAlarm() {
        if (isCompletedState.value) return
        AlarmRingingService.silenceForChallenge(this)
        isAlarmLoudState.value = false
        setFeedbackMessage(null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCompletedState.value) return

        isLeavingScreen = true
        abandonHandler = Handler(Looper.getMainLooper())
        abandonRunnable = Runnable {
            if (isLeavingScreen && !isCompletedState.value) {
                AlarmRingingService.resumeAlarm(this@BaseChallengeActivity)
                isAlarmLoudState.value = true
                setFeedbackMessage("Abandoned! Alarm resumed & reset to Stage 1!", isError = true)
                onResetToStage1()
                startStage(1)

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
        abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
        abandonRunnable = null
        abandonHandler = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stageTimer?.cancel()
        abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
        feedbackClearRunnable?.let { feedbackHandler.removeCallbacks(it) }
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
            targetValue = if (isUrgent || isLoud) 1.06f else 1.0f,
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
            if (completed) {
                // Terminal Completed State: ONE message only, no header, no toast, no banner over dismiss button
                ChallengeSuccessView(
                    onDismiss = {
                        AlarmRingingService.stopAlarm(this@BaseChallengeActivity)
                        finish()
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
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
                        onReSilence = { reSilenceAlarm() },
                        onTestStopAlarm = { testStopAlarm() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Section 2: Reserved Fixed-Height Message Slot (Never shifts or pushes layout)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeNotice != null) {
                            val cardBorderColor = if (isErrorNotice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            val iconVector = when {
                                isErrorNotice -> Icons.Default.Close
                                isSuccessNotice -> Icons.Default.Check
                                else -> Icons.Default.Info
                            }

                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(cardBorderColor)
                                ),
                                shape = RoundedCornerShape(10.dp)
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeNotice,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Section 3: Main Play Area (weight 1f, strictly non-scrollable)
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
    onReSilence: () -> Unit,
    onTestStopAlarm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Top Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challengeType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Silence/Loud indicator button
                    if (isLoud) {
                        Button(
                            onClick = onReSilence,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("re_silence_button")
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = "Silence", modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Re-Silence", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeOff,
                                contentDescription = "Silent Mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Silent",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }

                    // TEMPORARY TEST CODE - REMOVE LATER
                    if (SHOW_TEST_STOP_BUTTON) {
                        OutlinedButton(
                            onClick = onTestStopAlarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("test_stop_alarm_button_header")
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("TEST: Stop", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Stage Progress Tabs
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StageStepItem(
                        stageNumber = 1,
                        title = "Low",
                        isCurrent = currentStage == 1,
                        isPassed = currentStage > 1,
                        modifier = Modifier.weight(1f)
                    )
                    StageStepItem(
                        stageNumber = 2,
                        title = "Moderate",
                        isCurrent = currentStage == 2,
                        isPassed = currentStage > 2,
                        modifier = Modifier.weight(1f)
                    )
                    StageStepItem(
                        stageNumber = 3,
                        title = "Difficult",
                        isCurrent = currentStage == 3,
                        isPassed = currentStage > 3,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${remainingSec}s left",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
                targetValue = MaterialTheme.colorScheme.primary,
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
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val textColor = when {
        isPassed -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }

    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(width = if (isCurrent) 1.5.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            ),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "YOU ARE AWAKE",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "All 3 challenge stages completed successfully. Alarm is permanently turned off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_success_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Dismiss & Good Morning",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
