package com.example.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Size
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

    // Card-game style move: the card is taken out and inserted at the new place,
    // every card in between shifts one block (across rows too).
    fun moveCard(from: Int, to: Int) {
        val perSuit = cardsPerSuitState.value
        val flat = gridState.value.flatten().toMutableList()
        if (from !in flat.indices || to !in flat.indices || from == to) return
        val card = flat.removeAt(from)
        flat.add(to, card)
        val newGrid = flat.chunked(perSuit)
        gridState.value = newGrid

        val solved = CardArrangementData.isStageSolved(
            grid = newGrid,
            cardsPerSuit = perSuit,
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
            onMove = { from, to -> moveCard(from, to) },
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
    onMove: (Int, Int) -> Unit,
    onTaskCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val flat = remember(grid) { grid.flatten() }
    val currentOnMove by rememberUpdatedState(onMove)
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestCount by rememberUpdatedState(flat.size)
    val slotRects = remember { mutableStateMapOf<Int, Rect>() }

    // Card being dragged (flat index), the slot it currently hovers over, and finger position
    var dragFrom by remember { mutableIntStateOf(-1) }
    var hoverIndex by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(grid) {
        dragFrom = -1
        hoverIndex = -1
    }

    // Slot under the finger; if the finger is outside every slot, the nearest slot is used,
    // so the card can be dropped anywhere on the board.
    fun slotAt(pos: Offset): Int {
        // pos is relative to the board; slot rectangles are stored in root coordinates
        val rootPos = pos + boardOrigin
        var best = -1
        var bestDist = Float.MAX_VALUE
        for ((index, rect) in slotRects) {
            if (index >= latestCount) continue // old slot from a bigger stage
            if (rect.contains(rootPos)) return index
            val d = (rect.center - rootPos).getDistance()
            if (d < bestDist) {
                bestDist = d
                best = index
            }
        }
        return best
    }

    // While dragging, show the cards already shifted into their new places
    val displayed = if (dragFrom in flat.indices && hoverIndex in flat.indices && hoverIndex != dragFrom) {
        flat.toMutableList().also {
            val moved = it.removeAt(dragFrom)
            it.add(hoverIndex, moved)
        }
    } else {
        flat
    }
    val dragCard = flat.getOrNull(dragFrom)

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
        modifier = modifier.fillMaxSize()
    ) {
        val totalW = maxWidth
        val totalH = maxHeight

        val availableH = (totalH - 84.dp).coerceAtLeast(160.dp)
        val rowH = (availableH / 4).coerceIn(36.dp, 76.dp)

        // Width left for the cards of one row (minus paddings, suit box and gaps), so no card is squeezed out
        val availableW = (totalW - 56.dp - 3.dp * (cardsPerSuit - 1).coerceAtLeast(0)).coerceAtLeast(120.dp)
        val cardW = (availableW / cardsPerSuit.coerceAtLeast(1)).coerceIn(22.dp, 64.dp)
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
                    text = if (!orderMatters) {
                        "Goal: Match Each Row to Its Suit"
                    } else {
                        "Goal: suit rows, left to right: " + expectedRanks.joinToString(" ") { it.label }
                    },
                    maxLines = 1,
                    fontSize = 10.sp,
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

            // 4 Suits Playing Board: pick a card, drag it anywhere, drop it; the other cards shift
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { boardOrigin = it.positionInRoot() }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val index = slotAt(offset)
                                if (index >= 0) {
                                    dragFrom = index
                                    hoverIndex = index
                                    dragPos = offset
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (dragFrom >= 0) {
                                    change.consume()
                                    dragPos += dragAmount
                                    val index = slotAt(dragPos)
                                    if (index >= 0) hoverIndex = index
                                }
                            },
                            onDragEnd = {
                                if (dragFrom >= 0 && hoverIndex >= 0 && hoverIndex != dragFrom) {
                                    currentOnMove(dragFrom, hoverIndex)
                                }
                                dragFrom = -1
                                hoverIndex = -1
                            },
                            onDragCancel = {
                                dragFrom = -1
                                hoverIndex = -1
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (r in 0 until 4) {
                        val rowSuit = CardArrangementData.SUIT_ROWS[r]

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
                                    val index = r * cardsPerSuit + c
                                    val card = displayed.getOrNull(index)
                                    if (card != null) {
                                        PlayingCardView(
                                            card = card,
                                            expectedSuit = rowSuit,
                                            isPlaceholder = dragFrom >= 0 && index == hoverIndex,
                                            modifier = Modifier
                                                .width(cardW)
                                                .height(cardH)
                                                .onGloballyPositioned { coords ->
                                                    if (coords.isAttached) {
                                                        slotRects[index] = Rect(
                                                            coords.positionInRoot(),
                                                            Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                                                        )
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // The card you are holding follows your finger
                if (dragCard != null) {
                    val cardWpx = with(density) { cardW.toPx() }
                    val cardHpx = with(density) { cardH.toPx() }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    (dragPos.x - cardWpx / 2f).roundToInt(),
                                    (dragPos.y - cardHpx / 2f).roundToInt()
                                )
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
                                text = dragCard.rank.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = dragCard.suit.color
                            )
                            Text(
                                text = dragCard.suit.symbol,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = dragCard.suit.color
                            )
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
    }
}

@Composable
fun PlayingCardView(
    card: PlayingCard,
    expectedSuit: CardSuit,
    isPlaceholder: Boolean,
    modifier: Modifier = Modifier
) {
    val isSuitCorrect = card.suit == expectedSuit
    val borderColor = if (isSuitCorrect) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        Color(0xFF666666).copy(alpha = 0.4f)
    }

    Card(
        modifier = modifier
            .alpha(if (isPlaceholder) 0.25f else 1f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .testTag("card_${card.suit.name}_${card.rank.label}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F3E8)),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
