package com.example.challenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.ChallengeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class CardArrangementChallengeActivity : BaseChallengeActivity() {

    private val gridState = MutableStateFlow<List<List<PlayingCard>>>(emptyList())
    val grid = gridState.asStateFlow()

    private val cardsPerSuitState = MutableStateFlow(3)
    val cardsPerSuit = cardsPerSuitState.asStateFlow()

    private val expectedRanksState = MutableStateFlow<List<CardRank>>(emptyList())
    val expectedRanks = expectedRanksState.asStateFlow()

    private val orderMattersState = MutableStateFlow(false)
    val orderMatters = orderMattersState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.CARD_ARRANGEMENT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60   // Low: 60s
        2 -> 90   // Moderate: 90s
        3 -> 120  // Difficult: 120s
        else -> 60
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        setFeedbackMessage(null)
        val deal = CardArrangementData.dealStage(stage)
        cardsPerSuitState.value = deal.cardsPerSuit
        expectedRanksState.value = deal.ranksUsed
        orderMattersState.value = deal.orderMatters
        gridState.value = deal.initialGrid
    }

    fun swapCards(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        if (fromRow == toRow && fromCol == toCol) return
        val currentGrid = gridState.value.map { it.toMutableList() }.toMutableList()
        val temp = currentGrid[fromRow][fromCol]
        currentGrid[fromRow][fromCol] = currentGrid[toRow][toCol]
        currentGrid[toRow][toCol] = temp
        gridState.value = currentGrid

        // Auto-accept check
        val solved = CardArrangementData.isStageSolved(
            grid = currentGrid,
            cardsPerSuit = cardsPerSuitState.value,
            orderMatters = orderMattersState.value
        )
        if (solved) {
            setFeedbackMessage("Cards Arranged Correctly! Stage Completed!", isSuccess = true)
            completeCurrentStage()
        }
    }

    fun onTaskCompletedClicked() {
        val currentGrid = gridState.value
        val perSuit = cardsPerSuitState.value
        val ordMatters = orderMattersState.value
        val solved = CardArrangementData.isStageSolved(currentGrid, perSuit, ordMatters)

        if (solved) {
            setFeedbackMessage("Cards Arranged Correctly! Stage Completed!", isSuccess = true)
            completeCurrentStage()
        } else {
            val total = perSuit * 4
            val correct = CardArrangementData.countCorrectCards(currentGrid, ordMatters, expectedRanksState.value)
            val msg = if (!ordMatters) {
                "Some cards are in the wrong row ($correct of $total correct)"
            } else {
                "Cards are not in the right row or order yet ($correct of $total correct)"
            }
            setFeedbackMessage(msg, isError = true)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val cardsGrid by grid.collectAsState()
        val perSuit by cardsPerSuit.collectAsState()
        val ordMatters by orderMatters.collectAsState()
        val ranks by expectedRanks.collectAsState()
        val stage by currentStage.collectAsState()

        CardArrangementScreen(
            grid = cardsGrid,
            cardsPerSuit = perSuit,
            orderMatters = ordMatters,
            expectedRanks = ranks,
            currentStage = stage,
            onSwap = { fr, fc, tr, tc -> swapCards(fr, fc, tr, tc) },
            onTaskCompleted = { onTaskCompletedClicked() },
            modifier = modifier
        )
    }
}

data class CardCoord(val row: Int, val col: Int)

@Composable
fun CardArrangementScreen(
    grid: List<List<PlayingCard>>,
    cardsPerSuit: Int,
    orderMatters: Boolean,
    expectedRanks: List<CardRank>,
    currentStage: Int,
    onSwap: (Int, Int, Int, Int) -> Unit,
    onTaskCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBounds = remember { mutableStateMapOf<CardCoord, Rect>() }

    var selectedCoord by remember { mutableStateOf<CardCoord?>(null) }
    var activeDragOrigin by remember { mutableStateOf<CardCoord?>(null) }
    var activeDragCard by remember { mutableStateOf<PlayingCard?>(null) }
    var dragGlobalPosition by remember { mutableStateOf(Offset.Zero) }
    var hoveredCoord by remember { mutableStateOf<CardCoord?>(null) }

    var buttonDisabledUntil by remember { mutableLongStateOf(0L) }
    var isButtonDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(buttonDisabledUntil) {
        if (buttonDisabledUntil > System.currentTimeMillis()) {
            isButtonDisabled = true
            delay(buttonDisabledUntil - System.currentTimeMillis())
            isButtonDisabled = false
        }
    }

    fun findHoveredCoord(pos: Offset): CardCoord? {
        cardBounds.forEach { (coord, rect) ->
            if (rect.contains(pos)) return coord
        }
        return null
    }

    val correctCount = CardArrangementData.countCorrectCards(grid, orderMatters, expectedRanks)
    val totalCount = cardsPerSuit * 4

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalW = maxWidth
        val totalH = maxHeight

        // Fixed reserved heights:
        // Top instruction pill: 32dp
        // Bottom task completed button: 44dp
        // Bottom spacing: 8dp
        // Total fixed overhead: ~84dp
        val availableH = (totalH - 84.dp).coerceAtLeast(160.dp)
        val rowH = (availableH / 4).coerceIn(36.dp, 76.dp)

        // Card width: available width minus suit column (32dp) minus spacing
        val availableW = (totalW - 40.dp).coerceAtLeast(120.dp)
        val cardW = (availableW / cardsPerSuit.coerceAtLeast(1)).coerceIn(24.dp, 64.dp)
        val cardH = (rowH - 6.dp).coerceAtLeast(30.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stage Goal & Stats Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (!orderMatters) "Goal: Match Each Row to Its Suit" else "Goal: Match Suit + Ascending Rank",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Correct: $correctCount / $totalCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 4 Suits Playing Board (weight 1f, strictly non-scrollable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (r in 0 until 4) {
                        val rowSuit = CardArrangementData.SUIT_ROWS[r]
                        val rowCards = grid.getOrNull(r) ?: emptyList()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowH)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Row Suit Indicator Header (Fixed 30dp width)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF7F3E8))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rowSuit.symbol,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = rowSuit.color
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Cards in this row
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (c in 0 until cardsPerSuit) {
                                    val card = rowCards.getOrNull(c)
                                    val coord = CardCoord(r, c)
                                    val isSelected = selectedCoord == coord
                                    val isHovered = hoveredCoord == coord
                                    val isBeingDragged = activeDragOrigin == coord

                                    if (card != null) {
                                        PlayingCardView(
                                            card = card,
                                            expectedSuit = rowSuit,
                                            isSelected = isSelected,
                                            isHovered = isHovered,
                                            isBeingDragged = isBeingDragged,
                                            modifier = Modifier
                                                .width(cardW)
                                                .height(cardH)
                                                .onGloballyPositioned { cardBounds[coord] = it.boundsInRoot() },
                                            onTap = {
                                                val sel = selectedCoord
                                                if (sel == null) {
                                                    selectedCoord = coord
                                                } else {
                                                    onSwap(sel.row, sel.col, coord.row, coord.col)
                                                    selectedCoord = null
                                                }
                                            },
                                            onDragStart = { offset ->
                                                activeDragOrigin = coord
                                                activeDragCard = card
                                                cardBounds[coord]?.let {
                                                    dragGlobalPosition = it.topLeft + offset
                                                }
                                            },
                                            onDrag = { amount ->
                                                dragGlobalPosition += amount
                                                hoveredCoord = findHoveredCoord(dragGlobalPosition)
                                            },
                                            onDragEnd = {
                                                activeDragOrigin?.let { origin ->
                                                    hoveredCoord?.let { target ->
                                                        onSwap(origin.row, origin.col, target.row, target.col)
                                                    }
                                                }
                                                activeDragOrigin = null
                                                activeDragCard = null
                                                hoveredCoord = null
                                                selectedCoord = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Big Always-Visible Task Completed Button
            Button(
                onClick = {
                    if (!isButtonDisabled) {
                        buttonDisabledUntil = System.currentTimeMillis() + 1000L
                        onTaskCompleted()
                    }
                },
                enabled = !isButtonDisabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("card_task_completed_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TASK COMPLETED",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Floating Dragged Card Preview (drawn above all content)
        activeDragCard?.let { card ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragGlobalPosition.x - cardW.toPx() / 2).roundToInt(),
                            (dragGlobalPosition.y - cardH.toPx() / 2).roundToInt()
                        )
                    }
                    .size(cardW, cardH)
                    .zIndex(100f)
                    .scale(1.12f)
                    .shadow(12.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF7F3E8))
                    .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = card.rank.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = card.suit.color
                    )
                    Text(
                        text = card.suit.symbol,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = card.suit.color
                    )
                }
            }
        }
    }
}

@Composable
fun PlayingCardView(
    card: PlayingCard,
    expectedSuit: CardSuit,
    isSelected: Boolean,
    isHovered: Boolean,
    isBeingDragged: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val isSuitCorrect = card.suit == expectedSuit

    val borderColor = when {
        isHovered || isSelected -> MaterialTheme.colorScheme.primary
        isSuitCorrect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color(0xFF666666).copy(alpha = 0.4f)
    }

    val borderWidth = if (isHovered || isSelected) 2.5.dp else 1.dp
    val alpha = if (isBeingDragged) 0.25f else 1.0f

    // Ivory/cream face: #F7F3E8
    Card(
        modifier = modifier
            .scale(alpha)
            .clip(RoundedCornerShape(6.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable { onTap() }
            .pointerInput(card.id) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
            .testTag("card_${card.suit.name}_${card.rank.label}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F3E8)),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = card.rank.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = card.suit.color,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = card.suit.symbol,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = card.suit.color,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
