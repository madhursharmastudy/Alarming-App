package com.example.challenge

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 3-Stage Progressive Image Arrangement Challenge.
 * Slices a random built-in drawable artwork into:
 *  - Stage 1 (Low): 6 pieces (2 cols x 3 rows) with 1 Holding slot
 *  - Stage 2 (Moderate): 12 pieces (3 cols x 4 rows) with 2 Holding slots
 *  - Stage 3 (Difficult): 20 pieces (4 cols x 5 rows) with 2 Holding slots
 *
 * Supports drag-and-drop piece swapping and temporary storage in the Holding Area.
 */
class ImageArrangementChallengeActivity : BaseChallengeActivity() {

    // Board dimensions
    private val colsState = MutableStateFlow(2)
    val cols = colsState.asStateFlow()

    private val rowsState = MutableStateFlow(3)
    val rows = rowsState.asStateFlow()

    // Sliced pieces bitmaps indexed by piece ID (0..totalPieces-1)
    private val slicesState = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val slices = slicesState.asStateFlow()

    // Current board slots: contains piece ID (0..totalPieces-1) or null if moved to holding
    private val boardSlotsState = MutableStateFlow<List<Int?>>(emptyList())
    val boardSlots = boardSlotsState.asStateFlow()

    // Holding area slots: contains piece ID or null
    private val holdingSlotsState = MutableStateFlow<List<Int?>>(emptyList())
    val holdingSlots = holdingSlotsState.asStateFlow()

    // Full target image preview
    private val fullImageState = MutableStateFlow<ImageBitmap?>(null)
    val fullImage = fullImageState.asStateFlow()

    private val imageTitleState = MutableStateFlow("Artwork")
    val imageTitle = imageTitleState.asStateFlow()

    private var previousResId: Int? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.IMAGE_ARRANGEMENT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60   // Low: 60 sec
        2 -> 120  // Moderate: 120 sec
        3 -> 180  // Difficult: 180 sec
        else -> 60
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        val (numCols, numRows, holdingCapacity) = when (stage) {
            1 -> Triple(2, 3, 1)  // 6 pieces (2x3), 1 holding slot
            2 -> Triple(3, 4, 2)  // 12 pieces (3x4), 2 holding slots
            3 -> Triple(4, 5, 2)  // 20 pieces (4x5), 2 holding slots
            else -> Triple(2, 3, 1)
        }

        colsState.value = numCols
        rowsState.value = numRows
        setFeedbackMessage(null)

        // Randomly pick an image from the built-in list
        val item = ImageArrangementData.pickRandomImage(previousResId)
        previousResId = item.resId
        imageTitleState.value = item.title

        // Render drawable to bitmap sized exactly for grid tiles (unit size 180px per tile)
        val unitTileSize = 180
        val targetWidth = numCols * unitTileSize
        val targetHeight = numRows * unitTileSize
        val fullBitmap = ImageArrangementData.renderDrawableToBitmap(this, item.resId, targetWidth, targetHeight)
        fullImageState.value = fullBitmap.asImageBitmap()

        // Slice into pieces
        val pieceBitmaps = ImageArrangementData.sliceBitmap(fullBitmap, numCols, numRows)
        slicesState.value = pieceBitmaps.map { it.asImageBitmap() }

        // Shuffle board pieces (guarantee not already solved)
        val total = numCols * numRows
        val shuffled = (0 until total).toMutableList()
        do {
            shuffled.shuffle(Random(System.currentTimeMillis() + Random.nextLong()))
        } while (isBoardSolved(shuffled))

        boardSlotsState.value = shuffled.map { it as Int? }
        holdingSlotsState.value = List(holdingCapacity) { null }
    }

    private fun isBoardSolved(board: List<Int?>): Boolean {
        for (i in board.indices) {
            if (board[i] != i) return false
        }
        return true
    }

    fun swapBoardWithBoard(slotA: Int, slotB: Int) {
        if (slotA == slotB) return
        val list = boardSlotsState.value.toMutableList()
        val temp = list[slotA]
        list[slotA] = list[slotB]
        list[slotB] = temp
        boardSlotsState.value = list
        checkCompletion()
    }

    fun swapBoardWithHolding(boardSlot: Int, holdingSlot: Int) {
        val bList = boardSlotsState.value.toMutableList()
        val hList = holdingSlotsState.value.toMutableList()
        val temp = bList[boardSlot]
        bList[boardSlot] = hList[holdingSlot]
        hList[holdingSlot] = temp
        boardSlotsState.value = bList
        holdingSlotsState.value = hList
        checkCompletion()
    }

    fun swapHoldingWithHolding(slotA: Int, slotB: Int) {
        if (slotA == slotB) return
        val list = holdingSlotsState.value.toMutableList()
        val temp = list[slotA]
        list[slotA] = list[slotB]
        list[slotB] = temp
        holdingSlotsState.value = list
        checkCompletion()
    }

    private fun checkCompletion() {
        val b = boardSlotsState.value
        val h = holdingSlotsState.value
        // Solved if every holding slot is empty and board pieces match their slot indices
        val holdingEmpty = h.all { it == null }
        if (holdingEmpty && isBoardSolved(b)) {
            setFeedbackMessage("Artwork completed! Stage Passed!", isSuccess = true)
            completeCurrentStage()
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val colsVal by cols.collectAsState()
        val rowsVal by rows.collectAsState()
        val board by boardSlots.collectAsState()
        val holding by holdingSlots.collectAsState()
        val slicesList by slices.collectAsState()
        val fullImg by fullImage.collectAsState()
        val title by imageTitle.collectAsState()
        val stage by currentStage.collectAsState()

        ImageArrangementScreen(
            cols = colsVal,
            rows = rowsVal,
            boardSlots = board,
            holdingSlots = holding,
            slices = slicesList,
            fullImage = fullImg,
            imageTitle = title,
            currentStage = stage,
            onSwapBoardBoard = { a, b -> swapBoardWithBoard(a, b) },
            onSwapBoardHolding = { b, h -> swapBoardWithHolding(b, h) },
            onSwapHoldingHolding = { a, b -> swapHoldingWithHolding(a, b) },
            modifier = modifier
        )
    }
}

// Sealed class to identify slot targets for drag and drop
sealed class SlotTarget {
    data class Board(val index: Int) : SlotTarget()
    data class Holding(val index: Int) : SlotTarget()
}

@Composable
fun ImageArrangementScreen(
    cols: Int,
    rows: Int,
    boardSlots: List<Int?>,
    holdingSlots: List<Int?>,
    slices: List<ImageBitmap>,
    fullImage: ImageBitmap?,
    imageTitle: String,
    currentStage: Int,
    onSwapBoardBoard: (Int, Int) -> Unit,
    onSwapBoardHolding: (Int, Int) -> Unit,
    onSwapHoldingHolding: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Coordinate maps for hit detection
    val boardBounds = remember { mutableStateMapOf<Int, Rect>() }
    val holdingBounds = remember { mutableStateMapOf<Int, Rect>() }

    // Drag-and-drop state
    var activeDragOrigin by remember { mutableStateOf<SlotTarget?>(null) }
    var activeDragPieceId by remember { mutableStateOf<Int?>(null) }
    var dragGlobalPosition by remember { mutableStateOf(Offset.Zero) }
    var dragOffsetByTouch by remember { mutableStateOf(Offset.Zero) }
    var hoveredTarget by remember { mutableStateOf<SlotTarget?>(null) }

    // Tap-to-swap selection fallback (sleep-inertia friendly)
    var selectedSlot by remember { mutableStateOf<SlotTarget?>(null) }

    // Reference image dialog
    var showFullPreview by remember { mutableStateOf(false) }

    // Count matched pieces
    val matchedCount = boardSlots.filterIndexed { index, pieceId -> pieceId == index }.size
    val totalPieces = cols * rows

    fun findHoveredTarget(pos: Offset): SlotTarget? {
        boardBounds.forEach { (index, rect) ->
            if (rect.contains(pos)) return SlotTarget.Board(index)
        }
        holdingBounds.forEach { (index, rect) ->
            if (rect.contains(pos)) return SlotTarget.Holding(index)
        }
        return null
    }

    fun handleDrop(origin: SlotTarget, target: SlotTarget) {
        when {
            origin is SlotTarget.Board && target is SlotTarget.Board -> onSwapBoardBoard(origin.index, target.index)
            origin is SlotTarget.Board && target is SlotTarget.Holding -> onSwapBoardHolding(origin.index, target.index)
            origin is SlotTarget.Holding && target is SlotTarget.Board -> onSwapBoardHolding(target.index, origin.index)
            origin is SlotTarget.Holding && target is SlotTarget.Holding -> onSwapHoldingHolding(origin.index, target.index)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        // Available vertical space for the board: totalHeight minus top bar (36dp) minus holding bar (72dp) minus padding (12dp)
        val availableBoardW = totalWidth - 16.dp
        val availableBoardH = (totalHeight - 120.dp).coerceAtLeast(100.dp)

        val tileW = availableBoardW / cols
        val tileH = availableBoardH / rows
        val tileSize = minOf(tileW, tileH).coerceIn(34.dp, 84.dp)

        val actualBoardW = tileSize * cols
        val actualBoardH = tileSize * rows

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header stats & Target reference thumbnail bar (fixed height 36dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stage $currentStage: ${totalPieces} Pieces (${cols}x${rows})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = "Placed Correctly: $matchedCount / $totalPieces",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (matchedCount == totalPieces) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Reference Thumbnail Preview (Tap to Enlarge)
                fullImage?.let { bmp ->
                    Card(
                        onClick = { showFullPreview = true },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("preview_target_thumbnail"),
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Target Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Main Puzzle Board Grid (weight 1f, strictly non-scrollable, centered)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.size(actualBoardW + 8.dp, actualBoardH + 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier.size(actualBoardW, tileSize),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (c in 0 until cols) {
                                    val slotIndex = r * cols + c
                                    val pieceId = boardSlots.getOrNull(slotIndex)
                                    val isMatched = pieceId != null && pieceId == slotIndex
                                    val isSelected = selectedSlot == SlotTarget.Board(slotIndex)
                                    val isHovered = hoveredTarget == SlotTarget.Board(slotIndex)
                                    val isBeingDragged = activeDragOrigin == SlotTarget.Board(slotIndex)

                                    BoardSlotCell(
                                        slotIndex = slotIndex,
                                        pieceId = pieceId,
                                        sliceBitmap = pieceId?.let { slices.getOrNull(it) },
                                        isMatched = isMatched,
                                        isSelected = isSelected,
                                        isHovered = isHovered,
                                        isBeingDragged = isBeingDragged,
                                        modifier = Modifier.size(tileSize - 2.dp),
                                        onPositioned = { rect -> boardBounds[slotIndex] = rect },
                                        onDragStart = { offset, piece ->
                                            activeDragOrigin = SlotTarget.Board(slotIndex)
                                            activeDragPieceId = piece
                                            dragOffsetByTouch = Offset.Zero
                                            boardBounds[slotIndex]?.let { b ->
                                                dragGlobalPosition = b.topLeft + offset
                                            }
                                        },
                                        onDrag = { dragAmount ->
                                            dragOffsetByTouch += dragAmount
                                            dragGlobalPosition += dragAmount
                                            hoveredTarget = findHoveredTarget(dragGlobalPosition)
                                        },
                                        onDragEnd = {
                                            activeDragOrigin?.let { origin ->
                                                hoveredTarget?.let { target ->
                                                    handleDrop(origin, target)
                                                }
                                            }
                                            activeDragOrigin = null
                                            activeDragPieceId = null
                                            hoveredTarget = null
                                            selectedSlot = null
                                        },
                                        onTap = {
                                            val currentSel = selectedSlot
                                            if (currentSel == null) {
                                                if (pieceId != null) selectedSlot = SlotTarget.Board(slotIndex)
                                            } else {
                                                handleDrop(currentSel, SlotTarget.Board(slotIndex))
                                                selectedSlot = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dedicated Holding Area Box (fixed height ~68dp)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📦 HOLDING AREA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = "Temporary park slots",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        holdingSlots.forEachIndexed { slotIndex, pieceId ->
                            val isSelected = selectedSlot == SlotTarget.Holding(slotIndex)
                            val isHovered = hoveredTarget == SlotTarget.Holding(slotIndex)
                            val isBeingDragged = activeDragOrigin == SlotTarget.Holding(slotIndex)

                            HoldingSlotCell(
                                slotIndex = slotIndex,
                                pieceId = pieceId,
                                sliceBitmap = pieceId?.let { slices.getOrNull(it) },
                                isSelected = isSelected,
                                isHovered = isHovered,
                                isBeingDragged = isBeingDragged,
                                modifier = Modifier.size(54.dp),
                                onPositioned = { rect -> holdingBounds[slotIndex] = rect },
                                onDragStart = { offset, piece ->
                                    activeDragOrigin = SlotTarget.Holding(slotIndex)
                                    activeDragPieceId = piece
                                    dragOffsetByTouch = Offset.Zero
                                    holdingBounds[slotIndex]?.let { b ->
                                        dragGlobalPosition = b.topLeft + offset
                                    }
                                },
                                onDrag = { dragAmount ->
                                    dragOffsetByTouch += dragAmount
                                    dragGlobalPosition += dragAmount
                                    hoveredTarget = findHoveredTarget(dragGlobalPosition)
                                },
                                onDragEnd = {
                                    activeDragOrigin?.let { origin ->
                                        hoveredTarget?.let { target ->
                                            handleDrop(origin, target)
                                        }
                                    }
                                    activeDragOrigin = null
                                    activeDragPieceId = null
                                    hoveredTarget = null
                                    selectedSlot = null
                                },
                                onTap = {
                                    val currentSel = selectedSlot
                                    if (currentSel == null) {
                                        if (pieceId != null) selectedSlot = SlotTarget.Holding(slotIndex)
                                    } else {
                                        handleDrop(currentSel, SlotTarget.Holding(slotIndex))
                                        selectedSlot = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Dragged Piece Preview (drawn above all content with high zIndex)
        activeDragPieceId?.let { pieceId ->
            slices.getOrNull(pieceId)?.let { bmp ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragGlobalPosition.x - 60).roundToInt(),
                                (dragGlobalPosition.y - 60).roundToInt()
                            )
                        }
                        .size(110.dp)
                        .zIndex(100f)
                        .scale(1.12f)
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .border(3.dp, Color(0xFFFBBF24), RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Dragging piece",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Full Image Target Preview Dialog
        if (showFullPreview) {
            Dialog(onDismissRequest = { showFullPreview = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Reference: $imageTitle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        fullImage?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = "Full Reference Picture",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Tap anywhere outside to close",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoardSlotCell(
    slotIndex: Int,
    pieceId: Int?,
    sliceBitmap: ImageBitmap?,
    isMatched: Boolean,
    isSelected: Boolean,
    isHovered: Boolean,
    isBeingDragged: Boolean,
    modifier: Modifier = Modifier,
    onPositioned: (Rect) -> Unit,
    onDragStart: (Offset, Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit
) {
    val borderColor = when {
        isHovered -> Color(0xFFF59E0B) // Golden drop target highlight
        isSelected -> MaterialTheme.colorScheme.primary
        isMatched -> Color(0xFF10B981) // Crisp green solved indicator
        else -> Color.White.copy(alpha = 0.25f)
    }

    val borderWidth = if (isHovered || isSelected) 2.5.dp else if (isMatched) 2.dp else 1.dp
    val alpha = if (isBeingDragged) 0.3f else 1.0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .background(if (pieceId == null) Color(0xFF1E293B) else Color.DarkGray)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable { onTap() }
            .pointerInput(pieceId) {
                if (pieceId != null) {
                    detectDragGestures(
                        onDragStart = { offset -> onDragStart(offset, pieceId) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
            }
            .testTag("board_slot_$slotIndex"),
        contentAlignment = Alignment.Center
    ) {
        if (sliceBitmap != null && !isBeingDragged) {
            Image(
                bitmap = sliceBitmap,
                contentDescription = "Piece ${pieceId ?: slotIndex}",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(alpha),
                contentScale = ContentScale.Crop
            )
        }

        // Solved check badge in corner
        if (isMatched && !isBeingDragged) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Matched",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        // Empty slot placeholder indicator
        if (pieceId == null) {
            Text(
                text = "${slotIndex + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun HoldingSlotCell(
    slotIndex: Int,
    pieceId: Int?,
    sliceBitmap: ImageBitmap?,
    isSelected: Boolean,
    isHovered: Boolean,
    isBeingDragged: Boolean,
    modifier: Modifier = Modifier,
    onPositioned: (Rect) -> Unit,
    onDragStart: (Offset, Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit
) {
    val borderColor = when {
        isHovered -> Color(0xFFF59E0B)
        isSelected -> MaterialTheme.colorScheme.primary
        pieceId != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    val borderWidth = if (isHovered || isSelected) 3.dp else 1.5.dp
    val alpha = if (isBeingDragged) 0.3f else 1.0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .background(if (pieceId == null) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else Color.DarkGray)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .pointerInput(pieceId) {
                if (pieceId != null) {
                    detectDragGestures(
                        onDragStart = { offset -> onDragStart(offset, pieceId) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
            }
            .testTag("holding_slot_$slotIndex"),
        contentAlignment = Alignment.Center
    ) {
        if (sliceBitmap != null && !isBeingDragged) {
            Image(
                bitmap = sliceBitmap,
                contentDescription = "Holding piece $pieceId",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(alpha),
                contentScale = ContentScale.Crop
            )
        } else if (pieceId == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PanTool,
                    contentDescription = "Drop here",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Slot ${slotIndex + 1}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
