package com.example.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class OddShapeType {
    CIRCLE,
    SQUARE,
    TRIANGLE,
    DIAMOND,
    STAR,
    HEXAGON
}

data class GridItemSpec(
    val shape: OddShapeType,
    val color: Color,
    val hasInnerDot: Boolean = false,
    val isOdd: Boolean = false
)

/**
 * 3-Stage Progressive Odd One Out Challenge.
 * Spot the single different item in a geometric shape/color grid.
 * Low: 4 items (2x2), 1 round, 30s.
 * Moderate: 6 items (2x3), 2 rounds, 45s.
 * Difficult: 9 items (3x3), 3 rounds, 60s.
 */
class OddOneOutChallengeActivity : BaseChallengeActivity() {

    private val gridItemsState = MutableStateFlow<List<GridItemSpec>>(emptyList())
    val gridItems = gridItemsState.asStateFlow()

    private val gridColumnsState = MutableStateFlow(2)
    val gridColumns = gridColumnsState.asStateFlow()

    private val currentRoundState = MutableStateFlow(1)
    val currentRound = currentRoundState.asStateFlow()

    private val totalRoundsState = MutableStateFlow(1)
    val totalRounds = totalRoundsState.asStateFlow()

    private val isWrongTapState = MutableStateFlow(false)
    val isWrongTap = isWrongTapState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.ODD_ONE_OUT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 30  // Low: 30s
        2 -> 45  // Moderate: 45s
        3 -> 60  // Difficult: 60s
        else -> 30
    }

    override fun onStageStarted(stage: Int) {
        val rounds = when (stage) {
            1 -> 1 // Low: 1 correct tap
            2 -> 2 // Moderate: 2 rounds
            3 -> 3 // Difficult: 3 rounds
            else -> 1
        }
        totalRoundsState.value = rounds
        currentRoundState.value = 1
        isWrongTapState.value = false
        setupRound(stage)
    }

    override fun onResetToStage1() {
        totalRoundsState.value = 1
        currentRoundState.value = 1
        isWrongTapState.value = false
        setupRound(1)
    }

    private fun setupRound(stage: Int) {
        val count = when (stage) {
            1 -> 4 // 2x2
            2 -> 6 // 2x3
            3 -> 9 // 3x3
            else -> 4
        }
        gridColumnsState.value = if (stage == 3) 3 else 2
        isWrongTapState.value = false

        val items = generateGridItems(stage, count)
        gridItemsState.value = items
    }

    private fun generateGridItems(stage: Int, count: Int): List<GridItemSpec> {
        val oddIndex = Random.nextInt(count)
        val allShapes = OddShapeType.values()

        return when (stage) {
            1 -> {
                // Low: Obvious difference (different shape AND different color)
                val baseShape = allShapes.random()
                val otherShapes = allShapes.filter { it != baseShape }
                val oddShape = otherShapes.random()

                val baseColor = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFF8B5CF6)).random()
                val oddColor = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFFEC4899)).random()

                List(count) { idx ->
                    if (idx == oddIndex) {
                        GridItemSpec(shape = oddShape, color = oddColor, isOdd = true)
                    } else {
                        GridItemSpec(shape = baseShape, color = baseColor, isOdd = false)
                    }
                }
            }
            2 -> {
                // Moderate: Medium difference (same shape with different color, OR same color with different shape)
                val isColorDifference = Random.nextBoolean()
                if (isColorDifference) {
                    val shape = allShapes.random()
                    val baseColor = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFF06B6D4)).random()
                    val oddColor = listOf(Color(0xFFEF4444), Color(0xFFF59E0B)).random()

                    List(count) { idx ->
                        if (idx == oddIndex) {
                            GridItemSpec(shape = shape, color = oddColor, isOdd = true)
                        } else {
                            GridItemSpec(shape = shape, color = baseColor, isOdd = false)
                        }
                    }
                } else {
                    val color = listOf(Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFF97316)).random()
                    val baseShape = allShapes.random()
                    val oddShape = allShapes.filter { it != baseShape }.random()

                    List(count) { idx ->
                        if (idx == oddIndex) {
                            GridItemSpec(shape = oddShape, color = color, isOdd = true)
                        } else {
                            GridItemSpec(shape = baseShape, color = color, isOdd = false)
                        }
                    }
                }
            }
            else -> {
                // Difficult: Subtle difference (slightly different shade OR subtle inner dot)
                val isSubtleShade = Random.nextBoolean()
                val shape = allShapes.random()

                if (isSubtleShade) {
                    // Subtle shade difference
                    val colorPair = listOf(
                        Color(0xFF2563EB) to Color(0xFF1D4ED8), // Subtle Blues
                        Color(0xFF059669) to Color(0xFF047857), // Subtle Emeralds
                        Color(0xFFD97706) to Color(0xFFB45309), // Subtle Ambers
                        Color(0xFF7C3AED) to Color(0xFF6D28D9)  // Subtle Purples
                    ).random()

                    List(count) { idx ->
                        if (idx == oddIndex) {
                            GridItemSpec(shape = shape, color = colorPair.second, isOdd = true)
                        } else {
                            GridItemSpec(shape = shape, color = colorPair.first, isOdd = false)
                        }
                    }
                } else {
                    // Subtle detail (inner dot)
                    val color = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B)).random()
                    List(count) { idx ->
                        if (idx == oddIndex) {
                            GridItemSpec(shape = shape, color = color, hasInnerDot = true, isOdd = true)
                        } else {
                            GridItemSpec(shape = shape, color = color, hasInnerDot = false, isOdd = false)
                        }
                    }
                }
            }
        }
    }

    fun onItemTapped(item: GridItemSpec) {
        if (item.isOdd) {
            isWrongTapState.value = false
            val currentR = currentRoundState.value
            val totalR = totalRoundsState.value

            if (currentR < totalR) {
                currentRoundState.value = currentR + 1
                setupRound(currentStageState.value)
            } else {
                // Stage passed!
                completeCurrentStage()
            }
        } else {
            // Wrong item: user simply retries (no alarm re-ring)
            isWrongTapState.value = true
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val items by gridItems.collectAsState()
        val cols by gridColumns.collectAsState()
        val round by currentRound.collectAsState()
        val totalR by totalRounds.collectAsState()
        val isWrong by isWrongTap.collectAsState()
        val stage by currentStage.collectAsState()

        OddOneOutScreen(
            items = items,
            columns = cols,
            currentRound = round,
            totalRounds = totalR,
            isWrong = isWrong,
            currentStage = stage,
            onItemTapped = { onItemTapped(it) },
            modifier = modifier
        )
    }
}

@Composable
fun OddOneOutScreen(
    items: List<GridItemSpec>,
    columns: Int,
    currentRound: Int,
    totalRounds: Int,
    isWrong: Boolean,
    currentStage: Int,
    onItemTapped: (GridItemSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Stage Header & Round Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Stage $currentStage: ${items.size} Items Grid",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (currentStage) {
                                1 -> "Spot the obvious different item"
                                2 -> "Spot the medium difference"
                                else -> "Spot the subtle shade or detail"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Round $currentRound of $totalRounds",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Wrong feedback banner
            AnimatedVisibility(visible = isWrong) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "That item matches the others! Keep looking for the odd one.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Shape Grid
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
            )
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(items.size) { index ->
                    val spec = items[index]
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { onItemTapped(spec) }
                            .testTag("odd_item_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        OddShapeItemCanvas(spec = spec)
                    }
                }
            }
        }

        // Prompt helper at bottom
        Text(
            text = "Tap the single item that is different from all the rest",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

@Composable
fun OddShapeItemCanvas(spec: GridItemSpec) {
    Canvas(modifier = Modifier.fillMaxSize(0.72f)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) / 2.2f

        when (spec.shape) {
            OddShapeType.CIRCLE -> {
                drawCircle(color = spec.color, radius = radius, center = Offset(cx, cy))
            }
            OddShapeType.SQUARE -> {
                drawRect(
                    color = spec.color,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }
            OddShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(cx, cy - radius)
                    lineTo(cx + radius, cy + radius)
                    lineTo(cx - radius, cy + radius)
                    close()
                }
                drawPath(path = path, color = spec.color)
            }
            OddShapeType.DIAMOND -> {
                val path = Path().apply {
                    moveTo(cx, cy - radius)
                    lineTo(cx + radius, cy)
                    lineTo(cx, cy + radius)
                    lineTo(cx - radius, cy)
                    close()
                }
                drawPath(path = path, color = spec.color)
            }
            OddShapeType.HEXAGON -> {
                val path = Path().apply {
                    for (i in 0 until 6) {
                        val angle = Math.toRadians((60 * i - 30).toDouble())
                        val px = cx + (radius * Math.cos(angle)).toFloat()
                        val py = cy + (radius * Math.sin(angle)).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path = path, color = spec.color)
            }
            OddShapeType.STAR -> {
                val path = Path().apply {
                    val outer = radius
                    val inner = radius * 0.45f
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) outer else inner
                        val angle = Math.toRadians((36 * i - 90).toDouble())
                        val px = cx + (r * Math.cos(angle)).toFloat()
                        val py = cy + (r * Math.sin(angle)).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path = path, color = spec.color)
            }
        }

        // Optional subtle inner dot for subtle difficulty stage
        if (spec.hasInnerDot) {
            drawCircle(color = Color.White, radius = radius * 0.22f, center = Offset(cx, cy))
        }
    }
}
