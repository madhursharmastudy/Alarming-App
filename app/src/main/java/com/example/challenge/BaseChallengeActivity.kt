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
import com.example.ui.theme.OceanBrush
import com.example.ui.theme.OceanCyan
import com.example.ui.theme.OceanGradientButton
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
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
    protected var comboId: Int = 0
    private var comboSteps: List<ComboStep> = emptyList()

    private lateinit var softWake: SoftWakeBrightness

    companion object {
        const val EXTRA_FORCE_STEP = "extra_force_step"
        const val EXTRA_COMBO_ID = "extra_combo_id"
    }

    protected fun canUseCamera(): Boolean =
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    protected fun canUseMicrophone(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Safety net: if a task cannot run on this phone (no camera, no sensor), switch to another task. */
    protected fun fallbackTo(target: Class<*>, message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        val next = Intent(this, target).apply {
            putExtras(intent)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        startActivity(next)
        finish()
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
        comboId = intent.getIntExtra(EXTRA_COMBO_ID, 0)
        comboSteps = ComboCatalog.byId(comboId)?.steps ?: emptyList()
        if (comboSteps.isEmpty()) forceStep = 0

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

    // Difficulty (stage number) the current task runs at: normal tasks start at stage 1,
    // a combo step uses the difficulty given by the combo.
    private fun restartStage(): Int =
        if (forceStep > 0 && comboSteps.isNotEmpty()) comboSteps[(forceStep - 1).coerceIn(0, comboSteps.size - 1)].difficulty else 1

    // Combo: hand over to the next task of the combo
    private fun launchNextForceStep() {
        val nextStep = forceStep + 1
        val nextClass = ComboCatalog.activityClass(comboSteps[nextStep - 1].type)
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

        if (forceStep >= 1 && forceStep < comboSteps.size) {
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

        if (forceStep == 0 && current < total) {
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
                        challengeType = if (forceStep > 0) ChallengeType.COMBO else getChallengeType(),
                        titleOverride = if (forceStep > 0) "${ComboCatalog.byId(comboId)?.name ?: "Combo"}  (step $forceStep/${comboSteps.size})" else null,
                        currentStage = if (forceStep > 0) forceStep else stage,
                        totalStages = if (forceStep > 0) comboSteps.size else getTotalStages(),
                        stepTitles = if (forceStep > 0) comboSteps.map { it.role.label } else listOf("Low", "Moderate", "Difficult"),
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
    titleOverride: String? = null,
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
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleOverride ?: challengeType.displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            if (isLoud) {
                Button(
                    onClick = onReSilence,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 96.dp)
                        .background(OceanBrush, RoundedCornerShape(12.dp))
                        .testTag("re_silence_button")
                ) {
                    Icon(Icons.Default.VolumeOff, contentDescription = "Silence", modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Re-Silence", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            } else if (totalStages > 1) {
                Text(
                    text = "$currentStage / $totalStages",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time-left bar (gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(OceanBrush)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = if (isUrgent) MaterialTheme.colorScheme.error else OceanCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${remainingSec}s left",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) MaterialTheme.colorScheme.error else OceanCyan,
                    modifier = Modifier.scale(if (isUrgent || isLoud) pulseScale else 1.0f)
                )
            }
            Text(
                text = "Total ${totalSec}s",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (totalStages > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                stepTitles.forEachIndexed { index, title ->
                    StageStepItem(
                        stageNumber = index + 1,
                        title = title,
                        isCurrent = currentStage == index + 1,
                        isPassed = currentStage > index + 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
    val shape = RoundedCornerShape(12.dp)
    val textColor = when {
        isPassed -> Color.White
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                when {
                    isPassed -> Modifier.background(OceanBrush)
                    isCurrent -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    else -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                }
            )
            .then(if (isCurrent) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
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
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = "S$stageNumber: $title",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ChallengeSuccessView(wakeSeconds: Int = 0, streak: Int = 0, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D2442)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A3A58)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(OceanBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = "Done",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Good Morning!",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Alarm turned off",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SuccessStatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    value = if (streak > 0) "$streak" else "0",
                    label = "Day streak"
                )
                SuccessStatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    value = if (wakeSeconds > 0) WakeStats.formatDuration(wakeSeconds) else "--",
                    label = "Time to wake"
                )
            }
        }

        OceanGradientButton(
            text = "Done",
            onClick = onDismiss,
            modifier = Modifier.testTag("dismiss_success_button")
        )
    }
}

@Composable
private fun SuccessStatTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = OceanCyan, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
