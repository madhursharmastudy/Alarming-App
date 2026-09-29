package com.example.challenge

import android.os.CountDownTimer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.random.Random

/**
 * 3-Stage Progressive Pattern Lock Memory Challenge.
 * Shows a generated 3x3 pattern for 5 seconds, then hides it.
 * User must redraw it from memory.
 * Low: 4 dots, 45s.
 * Moderate: 6 dots, 60s.
 * Difficult: 8-9 dots, 90s.
 *
 * Strict single-screen non-scrollable layout with weight-based structure.
 */
class PatternLockChallengeActivity : BaseChallengeActivity() {

    // Target sequence of dot indices (0..8)
    private val targetPatternState = MutableStateFlow<List<Int>>(emptyList())
    val targetPattern = targetPatternState.asStateFlow()

    // Current user drawn sequence
    private val userPatternState = MutableStateFlow<List<Int>>(emptyList())
    val userPattern = userPatternState.asStateFlow()

    // Preview state: true while pattern is being revealed to memorize
    private val isPreviewActiveState = MutableStateFlow(true)
    val isPreviewActive = isPreviewActiveState.asStateFlow()

    private val previewSecondsLeftState = MutableStateFlow(5)
    val previewSecondsLeft = previewSecondsLeftState.asStateFlow()

    // Can only use "Show again" once per stage
    private val canShowAgainState = MutableStateFlow(true)
    val canShowAgain = canShowAgainState.asStateFlow()

    private var previewTimer: CountDownTimer? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.PATTERN_LOCK

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 45  // Low: 45s
        2 -> 60  // Moderate: 60s
        3 -> 90  // Difficult: 90s
        else -> 45
    }

    override fun onStageStarted(stage: Int) {
        canShowAgainState.value = true
        setupPattern(stage)
    }

    override fun onResetToStage1() {
        canShowAgainState.value = true
        setupPattern(1)
    }

    private fun setupPattern(stage: Int) {
        val dotCount = when (stage) {
            1 -> 4
            2 -> 6
            3 -> if (Random.nextBoolean()) 8 else 9
            else -> 4
        }

        val pattern = generateValidPattern(dotCount)
        targetPatternState.value = pattern
        userPatternState.value = emptyList()

        startPreviewCountdown(5)
    }

    private fun startPreviewCountdown(seconds: Int) {
        previewTimer?.cancel()
        isPreviewActiveState.value = true
        previewSecondsLeftState.value = seconds
        setFeedbackMessage("Memorize pattern! Hiding in ${seconds}s")

        previewTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = ((millisUntilFinished / 1000) + 1).toInt()
                previewSecondsLeftState.value = secLeft
                setFeedbackMessage("Memorize pattern! Hiding in ${secLeft}s")
            }

            override fun onFinish() {
                isPreviewActiveState.value = false
                previewSecondsLeftState.value = 0
                setFeedbackMessage("Redraw the pattern from memory")
            }
        }.start()
    }

    fun requestShowAgain() {
        if (!canShowAgainState.value || isPreviewActiveState.value) return
        canShowAgainState.value = false
        userPatternState.value = emptyList()
        startPreviewCountdown(4)
    }

    private fun generateValidPattern(length: Int): List<Int> {
        val pattern = mutableListOf<Int>()
        val startDot = Random.nextInt(9)
        pattern.add(startDot)

        fun areAdjacentOrDirect(a: Int, b: Int): Boolean {
            val ax = a % 3
            val ay = a / 3
            val bx = b % 3
            val by = b / 3
            val dx = kotlin.math.abs(ax - bx)
            val dy = kotlin.math.abs(ay - by)
            return (dx <= 1 && dy <= 1) || (dx == 1 && dy == 2) || (dx == 2 && dy == 1)
        }

        var current = startDot
        while (pattern.size < length) {
            val candidates = (0..8).filter { it !in pattern && areAdjacentOrDirect(current, it) }
            if (candidates.isNotEmpty()) {
                current = candidates.random()
                pattern.add(current)
            } else {
                val remaining = (0..8).filter { it !in pattern }
                if (remaining.isNotEmpty()) {
                    current = remaining.random()
                    pattern.add(current)
                } else {
                    break
                }
            }
        }
        return pattern
    }

    fun onUserPatternUpdated(pattern: List<Int>) {
        userPatternState.value = pattern
    }

    fun onUserPatternCompleted(pattern: List<Int>) {
        val target = targetPatternState.value
        if (pattern == target) {
            setFeedbackMessage("Pattern verified! Stage Completed!", isSuccess = true)
            completeCurrentStage()
        } else {
            // Wrong pattern -> regenerate fresh pattern to prevent guessing, let user retry
            setFeedbackMessage("Pattern mismatch! Memorize new pattern to retry.", isError = true)
            userPatternState.value = emptyList()
            val dotCount = target.size
            val freshPattern = generateValidPattern(dotCount)
            targetPatternState.value = freshPattern
            startPreviewCountdown(4)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        previewTimer?.cancel()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val target by targetPattern.collectAsState()
        val user by userPattern.collectAsState()
        val isPreview by isPreviewActive.collectAsState()
        val previewSec by previewSecondsLeft.collectAsState()
        val canShow by canShowAgain.collectAsState()
        val stage by currentStage.collectAsState()

        PatternLockScreen(
            targetPattern = target,
            userPattern = user,
            isPreview = isPreview,
            previewSec = previewSec,
            canShowAgain = canShow,
            currentStage = stage,
            onPatternUpdate = { onUserPatternUpdated(it) },
            onPatternComplete = { onUserPatternCompleted(it) },
            onShowAgain = { requestShowAgain() },
            modifier = modifier
        )
    }
}

@Composable
fun PatternLockScreen(
    targetPattern: List<Int>,
    userPattern: List<Int>,
    isPreview: Boolean,
    previewSec: Int,
    canShowAgain: Boolean,
    currentStage: Int,
    onPatternUpdate: (List<Int>) -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    onShowAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    val activeDrawnPattern = remember { mutableStateListOf<Int>() }

    // Synchronize drawn pattern when userPattern resets
    LaunchedEffect(userPattern) {
        if (userPattern.isEmpty()) {
            activeDrawnPattern.clear()
            touchPoint = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pattern Dots Target Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPreview) "MEMORIZE: ${targetPattern.size} Dots (${previewSec}s left)" else "CONNECT: ${targetPattern.size} Dots Required",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPreview) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
            )
        }

        // 3x3 Dot Grid Pattern Canvas Area (weight 1f, strictly non-scrollable, calculated from BoxWithConstraints)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalW = maxWidth - 16.dp
            val totalH = maxHeight - 12.dp
            val patternSize = minOf(totalW, totalH).coerceAtLeast(160.dp)

            Card(
                modifier = Modifier
                    .size(patternSize)
                    .testTag("pattern_lock_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isPreview) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PatternGridCanvas(
                        patternToShow = if (isPreview) targetPattern else activeDrawnPattern.toList(),
                        isPreview = isPreview,
                        currentTouch = if (!isPreview) touchPoint else null,
                        onTouchStart = { offset ->
                            if (!isPreview) {
                                activeDrawnPattern.clear()
                                touchPoint = offset
                            }
                        },
                        onTouchMove = { offset, hitDot ->
                            if (!isPreview) {
                                touchPoint = offset
                                if (hitDot != null && !activeDrawnPattern.contains(hitDot)) {
                                    activeDrawnPattern.add(hitDot)
                                    onPatternUpdate(activeDrawnPattern.toList())
                                }
                            }
                        },
                        onTouchEnd = {
                            if (!isPreview && activeDrawnPattern.isNotEmpty()) {
                                touchPoint = null
                                onPatternComplete(activeDrawnPattern.toList())
                            }
                        }
                    )
                }
            }
        }

        // Fixed-Height Bottom Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPreview) "Revealing..." else "Connected: ${activeDrawnPattern.size} / ${targetPattern.size} dots",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onShowAgain,
                enabled = canShowAgain && !isPreview,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("show_pattern_again_button")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (canShowAgain) "Show Again (1 left)" else "Show Used",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PatternGridCanvas(
    patternToShow: List<Int>,
    isPreview: Boolean,
    currentTouch: Offset?,
    onTouchStart: (Offset) -> Unit,
    onTouchMove: (Offset, Int?) -> Unit,
    onTouchEnd: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val strokeColor = primaryColor
    val dotActiveColor = primaryColor
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isPreview) {
                if (!isPreview) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onTouchStart(offset)
                            val dot = findDotUnderOffset(offset, size.width.toFloat(), size.height.toFloat())
                            onTouchMove(offset, dot)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val dot = findDotUnderOffset(change.position, size.width.toFloat(), size.height.toFloat())
                            onTouchMove(change.position, dot)
                        },
                        onDragEnd = onTouchEnd,
                        onDragCancel = onTouchEnd
                    )
                }
            }
    ) {
        val width = size.width
        val height = size.height

        val dotCenters = List(9) { index ->
            val col = index % 3
            val row = index / 3
            Offset(
                x = width * (col + 0.5f) / 3f,
                y = height * (row + 0.5f) / 3f
            )
        }

        // Draw connecting path
        if (patternToShow.size > 1) {
            val path = Path().apply {
                val start = dotCenters[patternToShow.first()]
                moveTo(start.x, start.y)
                for (i in 1 until patternToShow.size) {
                    val p = dotCenters[patternToShow[i]]
                    lineTo(p.x, p.y)
                }
                // If actively dragging, connect to finger
                currentTouch?.let {
                    lineTo(it.x, it.y)
                }
            }

            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else if (patternToShow.size == 1 && currentTouch != null) {
            val start = dotCenters[patternToShow.first()]
            drawLine(
                color = strokeColor,
                start = start,
                end = currentTouch,
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw 9 dots
        val baseRadius = 14.dp.toPx()
        val activeRadius = 18.dp.toPx()

        for (i in 0 until 9) {
            val center = dotCenters[i]
            val isDotInPattern = patternToShow.contains(i)

            // Outer ring
            drawCircle(
                color = if (isDotInPattern) dotActiveColor.copy(alpha = 0.35f) else Color.Transparent,
                radius = if (isDotInPattern) activeRadius * 1.5f else baseRadius,
                center = center
            )

            // Inner circle
            drawCircle(
                color = if (isDotInPattern) dotActiveColor else dotInactiveColor,
                radius = if (isDotInPattern) activeRadius else baseRadius,
                center = center
            )
        }
    }
}

private fun findDotUnderOffset(offset: Offset, width: Float, height: Float): Int? {
    val hitRadius = width / 6f // Tolerant touch target for morning fingers
    for (i in 0 until 9) {
        val col = i % 3
        val row = i / 3
        val cx = width * (col + 0.5f) / 3f
        val cy = height * (row + 0.5f) / 3f
        val dist = hypot(offset.x - cx, offset.y - cy)
        if (dist <= hitRadius) {
            return i
        }
    }
    return null
}
