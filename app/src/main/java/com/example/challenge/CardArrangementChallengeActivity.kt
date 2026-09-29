package com.example.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.hypot
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
    val density = LocalDensity.current
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val slotCoordinates = remember { mutableStateMapOf<CardCoord, LayoutCoordinates>() }

    var selectedCoord by remember { mutableStateOf<CardCoord?>(null) }
    var activeDragOrigin by remember { mutableStateOf<CardCoord?>(null) }
    var activeDragCard by remember { mutableStateOf<PlayingCard?>(null) }
    var dragOffsetInRoot by remember { mutableStateOf(Offset.Zero) }

    var buttonDisabledUntil by remember { mutableLongStateOf(0L) }
    var isButtonDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(buttonDisabledUntil) {
        if (buttonDisabledUntil > System.currentTimeMillis()) {
            isButtonDisabled = true
            delay(buttonDisabledUntil - System.currentTimeMillis())
            isButtonDisabled = false
        }
    }

    val correctCount = CardArrangementData.countCorrectCards(grid, orderMatters, expectedRanks)
    val totalCount = cardsPerSuit * 4

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootLayoutCoordinates = it }
    ) {
        val totalW = maxWidth
        val totalH = maxHeight

        val availableH = (totalH - 84.dp).coerceAtLeast(160.dp)
        val rowH = (availableH / 4).coerceIn(36.dp, 76.dp)

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
                                    val isBeingDragged = activeDragOrigin == coord

                                    if (card != null) {
                                        PlayingCardSlotView(
                                            card = card,
                                            expectedSuit = rowSuit,
                                            isSelected = isSelected,
                                            isBeingDragged = isBeingDragged,
                                            modifier = Modifier
                                                .width(cardW)
                                                .height(cardH)
                                                .onGloballyPositioned { layoutCoords ->
                                                    slotCoordinates[coord] = layoutCoords
                                                },
                                            onTap = {
                                                val sel = selectedCoord
                                                if (sel == null) {
                                                    selectedCoord = coord
                                                } else {
                                                    onSwap(sel.row, sel.col, coord.row, coord.col)
                                                    selectedCoord = null
                                                }
                                            },
                                            onDragStart = {
                                                activeDragOrigin = coord
                                                activeDragCard = card
                                                val root = rootLayoutCoordinates
                                                val slot = slotCoordinates[coord]
                                                if (root != null && slot != null && slot.isAttached) {
                                                    val localPos = root.localPositionOf(slot, Offset.Zero)
                                                    dragOffsetInRoot = localPos
                                                }
                                            },
                                            onDrag = { delta ->
                                                dragOffsetInRoot += delta
                                            },
                                            onDragRelease = {
                                                val root = rootLayoutCoordinates
                                                val origin = activeDragOrigin
                                                if (root != null && origin != null) {
                                                    val cardWidthPx = with(density) { cardW.toPx() }
                                                    val cardHeightPx = with(density) { cardH.toPx() }
                                                    val releaseCenter = dragOffsetInRoot + Offset(cardWidthPx / 2f, cardHeightPx / 2f)

                                                    // Find which slot was hovered or closest on release
                                                    var targetCoord: CardCoord? = null
                                                    var minDistance = Float.MAX_VALUE

                                                    slotCoordinates.forEach { (coord, slotCoords) ->
                                                        if (slotCoords.isAttached) {
                                                            val slotTopLeft = root.localPositionOf(slotCoords, Offset.Zero)
                                                            val slotRect = Rect(slotTopLeft, slotCoords.size.let { Offset(it.width.toFloat(), it.height.toFloat()) })
                                                            if (slotRect.contains(releaseCenter)) {
                                                                targetCoord = coord
                                                                minDistance = 0f
                                                            } else if (minDistance > 0f) {
                                                                val slotCenter = slotRect.center
                                                                val dist = hypot(releaseCenter.x - slotCenter.x, releaseCenter.y - slotCenter.y)
                                                                if (dist < minDistance && dist < cardWidthPx * 2.0f) {
                                                                    minDistance = dist
                                                                    targetCoord = coord
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (targetCoord != null && targetCoord != origin) {
                                                        onSwap(origin.row, origin.col, targetCoord!!.row, targetCoord!!.col)
                                                    }
                                                }

                                                // Always clean up dragged state so card snaps cleanly
                                                activeDragOrigin = null
                                                activeDragCard = null
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

            // Task Completed Button
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

        // Floating Dragged Card Preview (drawn above all content, snapped cleanly on release)
        activeDragCard?.let { card ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(dragOffsetInRoot.x.roundToInt(), dragOffsetInRoot.y.roundToInt())
                    }
                    .size(cardW, cardH)
                    .zIndex(100f)
                    .scale(1.10f)
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
fun PlayingCardSlotView(
    card: PlayingCard,
    expectedSuit: CardSuit,
    isSelected: Boolean,
    isBeingDragged: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragRelease: () -> Unit
) {
    val isSuitCorrect = card.suit == expectedSuit

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isSuitCorrect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color(0xFF666666).copy(alpha = 0.4f)
    }

    val borderWidth = if (isSelected) 2.5.dp else 1.dp
    val alpha = if (isBeingDragged) 0.20f else 1.0f

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .pointerInput(card.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    var isDragging = false
                    var totalDrag = Offset.Zero
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (isDragging) {
                                    onDragRelease()
                                } else {
                                    onTap()
                                }
                                break
                            }
                            val dragDelta = change.positionChange()
                            totalDrag += dragDelta
                            if (!isDragging && totalDrag.getDistance() > 8.dp.toPx()) {
                                isDragging = true
                                onDragStart()
                            }
                            if (isDragging) {
                                change.consume()
                                onDrag(dragDelta)
                            }
                        }
                    } finally {
                        if (isDragging) {
                            onDragRelease()
                        }
                    }
                }
            }
            .testTag("card_${card.suit.name}_${card.rank.label}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F3E8)),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .scale(alpha),
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
