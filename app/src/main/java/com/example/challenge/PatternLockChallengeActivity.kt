package com.example.challenge

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
import androidx.compose.material.icons.filled.Refresh
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
 * 3-Stage Pattern Lock Challenge.
 * The target pattern is ALWAYS visible as a reference card at the top.
 * User connects the dots on the interactive canvas below.
 * On error, user can retry the same pattern without regeneration.
 * Fits on a single fixed screen with NO scrolling.
 */
class PatternLockChallengeActivity : BaseChallengeActivity() {

    private val targetPatternState = MutableStateFlow<List<Int>>(emptyList())
    val targetPattern = targetPatternState.asStateFlow()

    private val userPatternState = MutableStateFlow<List<Int>>(emptyList())
    val userPattern = userPatternState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.PATTERN_LOCK

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 45  // Low: 4 dots, 45s
        2 -> 60  // Moderate: 6 dots, 60s
        3 -> 90  // Difficult: 8-9 dots, 90s
        else -> 45
    }

    override fun onStageStarted(stage: Int) {
        setupPattern(stage)
    }

    override fun onResetToStage1() {
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
        setFeedbackMessage("Follow the reference pattern above")
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
            // Keep the SAME target pattern visible, allow retry!
            setFeedbackMessage("Pattern mismatch! Follow the reference pattern to retry.", isError = true)
            userPatternState.value = emptyList()
        }
    }

    fun clearDrawnPattern() {
        userPatternState.value = emptyList()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val target by targetPattern.collectAsState()
        val user by userPattern.collectAsState()
        val stage by currentStage.collectAsState()

        PatternLockScreen(
            targetPattern = target,
            userPattern = user,
            currentStage = stage,
            onPatternUpdate = { onUserPatternUpdated(it) },
            onPatternComplete = { onUserPatternCompleted(it) },
            onClear = { clearDrawnPattern() },
            modifier = modifier
        )
    }
}

@Composable
fun PatternLockScreen(
    targetPattern: List<Int>,
    userPattern: List<Int>,
    currentStage: Int,
    onPatternUpdate: (List<Int>) -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    val activeDrawnPattern = remember { mutableStateListOf<Int>() }

    LaunchedEffect(userPattern) {
        if (userPattern.isEmpty()) {
            activeDrawnPattern.clear()
            touchPoint = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalW = maxWidth
        val totalH = maxHeight

        // Fixed heights:
        // Reference card: ~72dp
        // Bottom bar: ~40dp
        // Spacing: ~12dp
        // Total fixed overhead: ~124dp
        val availableCanvasH = (totalH - 124.dp).coerceAtLeast(150.dp)
        val canvasSize = minOf(totalW - 16.dp, availableCanvasH).coerceIn(150.dp, 280.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Reference Card: ALWAYS visible at the top showing target pattern
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .testTag("pattern_reference_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TARGET PATTERN (${targetPattern.size} DOTS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Connect in sequence 1 → ${targetPattern.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Miniature Reference Pattern View
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniPatternReferenceCanvas(
                            targetPattern = targetPattern,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Interactive Drawing Grid (weight 1f, strictly non-scrollable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .size(canvasSize)
                        .testTag("pattern_lock_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InteractivePatternGridCanvas(
                            activePattern = activeDrawnPattern.toList(),
                            currentTouch = touchPoint,
                            onTouchStart = { offset ->
                                activeDrawnPattern.clear()
                                touchPoint = offset
                            },
                            onTouchMove = { offset, hitDot ->
                                touchPoint = offset
                                if (hitDot != null && !activeDrawnPattern.contains(hitDot)) {
                                    activeDrawnPattern.add(hitDot)
                                    onPatternUpdate(activeDrawnPattern.toList())
                                }
                            },
                            onTouchEnd = {
                                if (activeDrawnPattern.isNotEmpty()) {
                                    touchPoint = null
                                    onPatternComplete(activeDrawnPattern.toList())
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Action Row (Fixed 40dp height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected: ${activeDrawnPattern.size} / ${targetPattern.size} dots",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedButton(
                    onClick = {
                        activeDrawnPattern.clear()
                        touchPoint = null
                        onClear()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp).testTag("clear_pattern_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Miniature reference canvas showing connected path with numbered sequence
 */
@Composable
fun MiniPatternReferenceCanvas(
    targetPattern: List<Int>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
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

        // Draw target path connecting dots in order
        if (targetPattern.size > 1) {
            val path = Path().apply {
                val start = dotCenters[targetPattern.first()]
                moveTo(start.x, start.y)
                for (i in 1 until targetPattern.size) {
                    val p = dotCenters[targetPattern[i]]
                    lineTo(p.x, p.y)
                }
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw 9 dots
        val radius = 4.dp.toPx()
        for (i in 0 until 9) {
            val center = dotCenters[i]
            val isTarget = targetPattern.contains(i)
            drawCircle(
                color = if (isTarget) primaryColor else dotInactiveColor,
                radius = if (isTarget) radius * 1.3f else radius,
                center = center
            )
        }
    }
}

/**
 * Interactive 3x3 pattern drawing canvas
 */
@Composable
fun InteractivePatternGridCanvas(
    activePattern: List<Int>,
    currentTouch: Offset?,
    onTouchStart: (Offset) -> Unit,
    onTouchMove: (Offset, Int?) -> Unit,
    onTouchEnd: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
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
        if (activePattern.size > 1) {
            val path = Path().apply {
                val start = dotCenters[activePattern.first()]
                moveTo(start.x, start.y)
                for (i in 1 until activePattern.size) {
                    val p = dotCenters[activePattern[i]]
                    lineTo(p.x, p.y)
                }
                currentTouch?.let {
                    lineTo(it.x, it.y)
                }
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else if (activePattern.size == 1 && currentTouch != null) {
            val start = dotCenters[activePattern.first()]
            drawLine(
                color = primaryColor,
                start = start,
                end = currentTouch,
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw 9 dots
        val baseRadius = 12.dp.toPx()
        val activeRadius = 16.dp.toPx()

        for (i in 0 until 9) {
            val center = dotCenters[i]
            val isDotInPattern = activePattern.contains(i)

            // Outer ring
            drawCircle(
                color = if (isDotInPattern) primaryColor.copy(alpha = 0.3f) else Color.Transparent,
                radius = if (isDotInPattern) activeRadius * 1.5f else baseRadius,
                center = center
            )

            // Inner circle
            drawCircle(
                color = if (isDotInPattern) primaryColor else dotInactiveColor,
                radius = if (isDotInPattern) activeRadius else baseRadius,
                center = center
            )
        }
    }
}

private fun findDotUnderOffset(offset: Offset, width: Float, height: Float): Int? {
    val hitRadius = width / 6f
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
