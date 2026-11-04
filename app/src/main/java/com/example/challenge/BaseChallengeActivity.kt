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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import com.example.service.AlarmReceiver
import com.example.service.AlarmRingingService
import com.example.ui.EmergencyEscapeButton
import com.example.ui.SoftWakeBrightness
import com.example.ui.TestExitButton
import com.example.ui.WakeLevel
import com.example.ui.WakeStats
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base Activity providing:
 * - 3-stage progression logic with timer per stage
 * - Idempotent State Machine: Ringing -> Challenge(1..3) -> Completed (terminal)
 * - Automatic alarm silencing during challenge solving
 * - 3-second grace period on home/recents press before resuming alarm & resetting to stage 1
 * - Non-overlapping header, message slot, and non-scrollable play area
 * - 2-color sharp contrast theme consistency
 */
abstract class BaseChallengeActivity : ComponentActivity() {

    protected val currentStageState = MutableStateFlow(1)
    val currentStage = currentStageState.asStateFlow()

    protected val timeLeftState = MutableStateFlow(30)
    val timeLeft = timeLeftState.asStateFlow()

    protected val totalStageTimeState = MutableStateFlow(30)
    val totalStageTime = totalStageTimeState.asStateFlow()

    protected val isCompletedState = MutableStateFlow(false)
    private val wakeSecondsState = MutableStateFlow(0)
    private val streakState = MutableStateFlow(0)
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

    // 0 = normal single challenge. 1..3 = current step of Force Mode (Shake -> Math -> QR)
    protected var forceStep: Int = 0

    private lateinit var softWake: SoftWakeBrightness

    companion object {
        const val EXTRA_FORCE_STEP = "extra_force_step"
    }

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
        alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Aurum Alarm"
        refLabels = intent.getStringExtra(AlarmReceiver.EXTRA_REF_LABELS)
        forceStep = intent.getIntExtra(EXTRA_FORCE_STEP, 0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Locked until completed
            }
        })

        // Ensure alarm is silenced while solving (unless a penalty is active: then it stays loud)
        AlarmRingingService.silenceForChallenge(this)
        isAlarmLoudState.value = AlarmRingingService.penaltyActive

        softWake = SoftWakeBrightness(this)
        softWake.start()

        setContent {
            // Always dark at wake-up time: a bright white screen is hard on sleepy eyes
            MyApplicationTheme(forceDark = true) {
                ChallengeScreenWrapper()
            }
        }

        // Start Stage 1 (or the difficulty that matches the current Force Mode step)
        startStage(restartStage())
    }

    protected fun startStage(stage: Int) {
        if (isCompletedState.value) return

        currentStageState.value = stage
        val timeLimitSec = (getStageTimeLimit(stage) * WakeLevel.current(this).timeMultiplier).toInt().coerceAtLeast(10)
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

    private fun restartStage(): Int = if (forceStep > 0) forceStep else 1

    // Force Mode: hand over to the next challenge (step 1 Shake -> step 2 Math -> step 3 QR)
    private fun launchNextForceStep() {
        val nextStep = forceStep + 1
        val nextClass = if (nextStep == 2) MathChallengeActivity::class.java else QrChallengeActivity::class.java
        val next = Intent(this, nextClass).apply {
            putExtras(intent)
            putExtra(EXTRA_FORCE_STEP, nextStep)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        startActivity(next)
        finish()
    }

    protected fun completeCurrentStage() {
        if (isCompletedState.value) return

        if (forceStep in 1..2) {
            stageTimer?.cancel()
            stageTimer = null
            abandonRunnable?.let { abandonHandler?.removeCallbacks(it) }
            abandonRunnable = null
            abandonHandler = null
            launchNextForceStep()
            return
        }

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

            // Record how long it took to get up (only for real alarms, not test runs)
            val ringStart = AlarmRingingService.ringStartTime
            if (alarmId > 0 && ringStart > 0L) {
                val wakeSec = ((System.currentTimeMillis() - ringStart) / 1000L).toInt().coerceAtLeast(1)
                WakeStats.recordWake(this, wakeSec)
                wakeSecondsState.value = wakeSec
                streakState.value = WakeStats.currentStreak(this)
            }

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
        startStage(restartStage())
    }

    fun reSilenceAlarm() {
        if (isCompletedState.value) return
        // Only one re-silence is allowed per ringing session; after that the alarm stays loud
        if (AlarmRingingService.reSilenceOnce(this)) {
            isAlarmLoudState.value = false
            setFeedbackMessage(null)
        } else {
            setFeedbackMessage("Alarm stays loud until you finish the challenge!", isError = true)
        }
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
                startStage(restartStage())

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
        softWake.stop()
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
            targetValue = if (isUrgent || isLoud) 1.03f else 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
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
                val wakeSeconds by wakeSecondsState.collectAsState()
                val streak by streakState.collectAsState()
                ChallengeSuccessView(
                    wakeSeconds = wakeSeconds,
                    streak = streak,
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
                    // Section 1: Fixed-Height Compact Header with Non-overlapping Layout
                    ChallengeHeader(
                        challengeType = if (forceStep > 0) ChallengeType.FORCE else getChallengeType(),
                        currentStage = if (forceStep > 0) forceStep else stage,
                        totalStages = if (forceStep > 0) 3 else getTotalStages(),
                        stepTitles = if (forceStep > 0) listOf("Body", "Mind", "QR") else listOf("Low", "Moderate", "Difficult"),
                        remainingSec = remainingSec,
                        totalSec = totalSec,
                        progress = progress,
                        isUrgent = isUrgent,
                        isLoud = isLoud,
                        pulseScale = pulseScale,
                        onReSilence = { reSilenceAlarm() }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Section 2: Reserved Fixed-Height Message Slot (Never shifts or pushes layout)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
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
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Section 3: Main Play Area (weight 1f, strictly non-scrollable)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ChallengeContent(modifier = Modifier.fillMaxSize())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmergencyEscapeButton(
                            onEscape = {
                                AlarmRingingService.stopAlarm(this@BaseChallengeActivity)
                                finish()
                            }
                        )
                        TestExitButton(
                            onExit = {
                                AlarmRingingService.stopAlarm(this@BaseChallengeActivity)
                                finish()
                            }
                        )
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
    stepTitles: List<String> = listOf("Low", "Moderate", "Difficult"),
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Top Action Row with proper space distribution: title on left, action on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challengeType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                // Silence/Loud indicator button with fixed minimum width so text is never clipped
                if (isLoud) {
                    Button(
                        onClick = onReSilence,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 96.dp)
                            .testTag("re_silence_button")
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = "Silence", modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Re-Silence", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 72.dp)
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
            }

            // Fixed 8dp spacing between header row and stage tabs
            Spacer(modifier = Modifier.height(8.dp))

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
                        title = stepTitles[0],
                        isCurrent = currentStage == 1,
                        isPassed = currentStage > 1,
                        modifier = Modifier.weight(1f)
                    )
                    StageStepItem(
                        stageNumber = 2,
                        title = stepTitles[1],
                        isCurrent = currentStage == 2,
                        isPassed = currentStage > 2,
                        modifier = Modifier.weight(1f)
                    )
                    StageStepItem(
                        stageNumber = 3,
                        title = stepTitles[2],
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
fun ChallengeSuccessView(wakeSeconds: Int = 0, streak: Int = 0, onDismiss: () -> Unit) {
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

                if (wakeSeconds > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You got up in ${WakeStats.formatDuration(wakeSeconds)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    if (streak > 0) {
                        Text(
                            text = "Streak: $streak day${if (streak == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Next: drink some water and get a little daylight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

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
