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
 * 3-Stage Pattern Lock Challenge:
 * - Low: 6 dots (2 cols x 3 rows), 4-dot pattern, 45s.
 * - Moderate: 9 dots (3 cols x 3 rows), 6-dot pattern, 60s.
 * - Difficult: 12 dots (3 cols x 4 rows), 8-dot pattern, 90s.
 * Reference pattern card remains ALWAYS visible at top.
 * Precise segment-based hit detection without false neighbor triggers.
 */
class PatternLockChallengeActivity : BaseChallengeActivity() {

    private val gridColsState = MutableStateFlow(2)
    val gridCols = gridColsState.asStateFlow()

    private val gridRowsState = MutableStateFlow(3)
    val gridRows = gridRowsState.asStateFlow()

    private val targetPatternState = MutableStateFlow<List<Int>>(emptyList())
    val targetPattern = targetPatternState.asStateFlow()

    private val userPatternState = MutableStateFlow<List<Int>>(emptyList())
    val userPattern = userPatternState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.PATTERN_LOCK

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 45  // Low: 45s
        2 -> 60  // Moderate: 60s
        3 -> 90  // Difficult: 90s
        else -> 45
    }

    override fun onStageStarted(stage: Int) {
        setupPattern(stage)
    }

    override fun onResetToStage1() {
        setupPattern(1)
    }

    private fun setupPattern(stage: Int) {
        val (cols, rows, patternLen) = when (stage) {
            1 -> Triple(2, 3, 4) // Low: 2x3 = 6 dots, length 4
            2 -> Triple(3, 3, 6) // Moderate: 3x3 = 9 dots, length 6
            3 -> Triple(3, 4, 8) // Difficult: 3x4 = 12 dots, length 8
            else -> Triple(2, 3, 4)
        }

        gridColsState.value = cols
        gridRowsState.value = rows
        val pattern = generateValidPattern(cols, rows, patternLen)
        targetPatternState.value = pattern
        userPatternState.value = emptyList()
        setFeedbackMessage("Follow the reference pattern above")
    }

    private fun generateValidPattern(cols: Int, rows: Int, length: Int): List<Int> {
        val totalDots = cols * rows
        val pattern = mutableListOf<Int>()
        var current = Random.nextInt(totalDots)
        pattern.add(current)

        fun canConnect(a: Int, b: Int): Boolean {
            val ax = a % cols
            val ay = a / cols
            val bx = b % cols
            val by = b / cols
            val dx = kotlin.math.abs(ax - bx)
            val dy = kotlin.math.abs(ay - by)
            return (dx <= 1 && dy <= 1) || (dx == 1 && dy == 2) || (dx == 2 && dy == 1)
        }

        while (pattern.size < length) {
            val candidates = (0 until totalDots).filter { it !in pattern && canConnect(current, it) }
            if (candidates.isNotEmpty()) {
                current = candidates.random()
                pattern.add(current)
            } else {
                val remaining = (0 until totalDots).filter { it !in pattern }
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
            // Keep the same pattern visible and let user retry immediately
            setFeedbackMessage("Pattern mismatch! Follow the reference to retry.", isError = true)
            userPatternState.value = emptyList()
        }
    }

    fun clearDrawnPattern() {
        userPatternState.value = emptyList()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val cols by gridCols.collectAsState()
        val rows by gridRows.collectAsState()
        val target by targetPattern.collectAsState()
        val user by userPattern.collectAsState()
        val stage by currentStage.collectAsState()

        PatternLockScreen(
            cols = cols,
            rows = rows,
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
    cols: Int,
    rows: Int,
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

        val availableCanvasH = (totalH - 128.dp).coerceAtLeast(160.dp)
        val canvasW = (totalW - 16.dp).coerceIn(160.dp, 320.dp)
        val canvasH = availableCanvasH.coerceIn(160.dp, 340.dp)

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
                            text = "Grid: ${cols}×${rows} • Connect 1 → ${targetPattern.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                            cols = cols,
                            rows = rows,
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
                        .size(width = canvasW, height = canvasH)
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
                            cols = cols,
                            rows = rows,
                            activePattern = activeDrawnPattern.toList(),
                            currentTouch = touchPoint,
                            onDotConnected = { dot ->
                                if (!activeDrawnPattern.contains(dot)) {
                                    activeDrawnPattern.add(dot)
                                    onPatternUpdate(activeDrawnPattern.toList())
                                }
                            },
                            onTouchPointUpdate = { pt ->
                                touchPoint = pt
                            },
                            onResetTouch = {
                                activeDrawnPattern.clear()
                                touchPoint = null
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

            // Bottom Action Row
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
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("clear_pattern_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MiniPatternReferenceCanvas(
    cols: Int,
    rows: Int,
    targetPattern: List<Int>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        val totalDots = cols * rows
        val width = size.width
        val height = size.height

        val dotCenters = List(totalDots) { index ->
            val c = index % cols
            val r = index / cols
            Offset(
                x = width * (c + 0.5f) / cols.toFloat(),
                y = height * (r + 0.5f) / rows.toFloat()
            )
        }

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

        val radius = 3.5.dp.toPx()
        for (i in 0 until totalDots) {
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

@Composable
fun InteractivePatternGridCanvas(
    cols: Int,
    rows: Int,
    activePattern: List<Int>,
    currentTouch: Offset?,
    onDotConnected: (Int) -> Unit,
    onTouchPointUpdate: (Offset?) -> Unit,
    onResetTouch: () -> Unit,
    onTouchEnd: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(cols, rows) {
                val totalDots = cols * rows
                val hitRadiusPx = 20.dp.toPx()

                detectDragGestures(
                    onDragStart = { offset ->
                        onResetTouch()
                        onTouchPointUpdate(offset)
                        // Check if touched directly on any dot
                        val dotCenters = computeDotCenters(size.width.toFloat(), size.height.toFloat(), cols, rows)
                        for (i in 0 until totalDots) {
                            if (hypot(offset.x - dotCenters[i].x, offset.y - dotCenters[i].y) <= hitRadiusPx) {
                                onDotConnected(i)
                                break
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val currentPos = change.position
                        onTouchPointUpdate(currentPos)

                        val dotCenters = computeDotCenters(size.width.toFloat(), size.height.toFloat(), cols, rows)
                        val lastDot = activePattern.lastOrNull()

                        if (lastDot == null) {
                            for (i in 0 until totalDots) {
                                if (hypot(currentPos.x - dotCenters[i].x, currentPos.y - dotCenters[i].y) <= hitRadiusPx) {
                                    onDotConnected(i)
                                    break
                                }
                            }
                        } else {
                            val lastCenter = dotCenters[lastDot]
                            // Precise Segment-to-Dot detection: find all unvisited dots crossed by the segment
                            val candidates = mutableListOf<Pair<Int, Float>>()
                            for (i in 0 until totalDots) {
                                if (!activePattern.contains(i)) {
                                    val (dist, t) = distAndTToSegment(dotCenters[i], lastCenter, currentPos)
                                    if (dist <= hitRadiusPx) {
                                        candidates.add(Pair(i, t))
                                    }
                                }
                            }
                            // Add in the exact order they are intersected along the line segment
                            candidates.sortBy { it.second }
                            for (candidate in candidates) {
                                onDotConnected(candidate.first)
                            }
                        }
                    },
                    onDragEnd = onTouchEnd,
                    onDragCancel = onTouchEnd
                )
            }
    ) {
        val totalDots = cols * rows
        val dotCenters = computeDotCenters(size.width, size.height, cols, rows)

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
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        } else if (activePattern.size == 1 && currentTouch != null) {
            val start = dotCenters[activePattern.first()]
            drawLine(
                color = primaryColor,
                start = start,
                end = currentTouch,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw dots
        val baseRadius = 10.dp.toPx()
        val activeRadius = 14.dp.toPx()

        for (i in 0 until totalDots) {
            val center = dotCenters[i]
            val isDotInPattern = activePattern.contains(i)

            drawCircle(
                color = if (isDotInPattern) primaryColor.copy(alpha = 0.25f) else Color.Transparent,
                radius = if (isDotInPattern) activeRadius * 1.5f else baseRadius,
                center = center
            )

            drawCircle(
                color = if (isDotInPattern) primaryColor else dotInactiveColor,
                radius = if (isDotInPattern) activeRadius else baseRadius,
                center = center
            )
        }
    }
}

private fun computeDotCenters(width: Float, height: Float, cols: Int, rows: Int): List<Offset> {
    val total = cols * rows
    return List(total) { index ->
        val c = index % cols
        val r = index / cols
        Offset(
            x = width * (c + 0.5f) / cols.toFloat(),
            y = height * (r + 0.5f) / rows.toFloat()
        )
    }
}

private fun distAndTToSegment(p: Offset, a: Offset, b: Offset): Pair<Float, Float> {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 == 0f) return Pair(hypot(p.x - a.x, p.y - a.y), 0f)
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
    val projX = a.x + t * (b.x - a.x)
    val projY = a.y + t * (b.y - a.y)
    return Pair(hypot(p.x - projX, p.y - projY), t)
}
