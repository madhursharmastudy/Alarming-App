package com.example.challenge

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.filled.PanTool
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.data.ChallengeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 3-Stage Progressive Image Arrangement Challenge.
 * Completely fits on ONE screen without scrolling.
 * Slices an artwork into:
 *  - Stage 1 (Low): 6 pieces (2 cols x 3 rows) with 1 Holding slot
 *  - Stage 2 (Moderate): 12 pieces (3 cols x 4 rows) with 2 Holding slots
 *  - Stage 3 (Difficult): 20 pieces (4 cols x 5 rows) with 2 Holding slots
 */
class ImageArrangementChallengeActivity : BaseChallengeActivity() {

    private val colsState = MutableStateFlow(2)
    val cols = colsState.asStateFlow()

    private val rowsState = MutableStateFlow(3)
    val rows = rowsState.asStateFlow()

    private val slicesState = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val slices = slicesState.asStateFlow()

    private val boardSlotsState = MutableStateFlow<List<Int?>>(emptyList())
    val boardSlots = boardSlotsState.asStateFlow()

    private val holdingSlotsState = MutableStateFlow<List<Int?>>(emptyList())
    val holdingSlots = holdingSlotsState.asStateFlow()

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

        val item = ImageArrangementData.pickRandomImage(previousResId)
        previousResId = item.resId
        imageTitleState.value = item.title

        val unitTileSize = 180
        val targetWidth = numCols * unitTileSize
        val targetHeight = numRows * unitTileSize
        val fullBitmap = ImageArrangementData.renderDrawableToBitmap(this, item.resId, targetWidth, targetHeight)
        fullImageState.value = fullBitmap.asImageBitmap()

        val pieceBitmaps = ImageArrangementData.sliceBitmap(fullBitmap, numCols, numRows)
        slicesState.value = pieceBitmaps.map { it.asImageBitmap() }

        val total = numCols * numRows
        val shuffled = (0 until total).toMutableList()
        do {
            shuffled.shuffle(Random(System.currentTimeMillis() + Random.nextLong()))
        } while (isBoardSolved(shuffled))

        boardSlotsState.value = shuffled.map { it as Int? }
        holdingSlotsState.value = emptyList() // no holding area any more: pieces just shift
    }

    private fun isBoardSolved(board: List<Int?>): Boolean {
        for (i in board.indices) {
            if (board[i] != i) return false
        }
        return true
    }

    // Card-game style move: the piece is taken out and inserted at the new place,
    // every piece in between shifts one block (across rows too).
    fun moveBoardPiece(from: Int, to: Int) {
        val list = boardSlotsState.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val piece = list.removeAt(from)
        list.add(to, piece)
        boardSlotsState.value = list
        checkAutoAccept()
    }

    private fun checkAutoAccept() {
        val b = boardSlotsState.value
        val h = holdingSlotsState.value
        val holdingEmpty = h.all { it == null }
        if (holdingEmpty && isBoardSolved(b)) {
            setFeedbackMessage("Artwork completed! Stage Passed!", isSuccess = true)
            completeCurrentStage()
        }
    }

    fun onTaskCompletedClicked() {
        val b = boardSlotsState.value
        val h = holdingSlotsState.value
        val holdingHasPieces = h.any { it != null }
        val matched = b.filterIndexed { index, pieceId -> pieceId == index }.size
        val total = colsState.value * rowsState.value

        if (!holdingHasPieces && isBoardSolved(b)) {
            setFeedbackMessage("Artwork completed! Stage Passed!", isSuccess = true)
            completeCurrentStage()
        } else {
            val message = if (holdingHasPieces) {
                "Image is not correct yet ($matched of $total pieces placed). Place all holding pieces on the board."
            } else {
                "Image is not correct yet ($matched of $total pieces placed correctly)."
            }
            setFeedbackMessage(message, isError = true)
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
            slices = slicesList,
            fullImage = fullImg,
            imageTitle = title,
            onMove = { from, to -> moveBoardPiece(from, to) },
            onTaskCompleted = { onTaskCompletedClicked() },
            modifier = modifier
        )
    }
}

@Composable
fun ImageArrangementScreen(
    cols: Int,
    rows: Int,
    boardSlots: List<Int?>,
    slices: List<ImageBitmap>,
    fullImage: ImageBitmap?,
    imageTitle: String,
    onMove: (Int, Int) -> Unit,
    onTaskCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val latestCols by rememberUpdatedState(cols)
    val latestRows by rememberUpdatedState(rows)
    val pitchHolder = remember { FloatArray(1) } // tile size in px, written during layout, read by gestures

    // Piece being dragged (board index), the slot it hovers over, and the finger position
    var dragFrom by remember { mutableIntStateOf(-1) }
    var hoverIndex by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    var showFullPreview by remember { mutableStateOf(false) }
    var buttonDisabledUntil by remember { mutableLongStateOf(0L) }
    var isButtonDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(boardSlots) {
        dragFrom = -1
        hoverIndex = -1
    }

    LaunchedEffect(buttonDisabledUntil) {
        if (buttonDisabledUntil > System.currentTimeMillis()) {
            isButtonDisabled = true
            delay(buttonDisabledUntil - System.currentTimeMillis())
            isButtonDisabled = false
        }
    }

    val matchedCount = boardSlots.filterIndexed { index, pieceId -> pieceId == index }.size
    val totalPieces = cols * rows

    // While dragging, show the pieces already shifted into their new places
    val displayed = if (dragFrom in boardSlots.indices && hoverIndex in boardSlots.indices && hoverIndex != dragFrom) {
        boardSlots.toMutableList().also {
            val moved = it.removeAt(dragFrom)
            it.add(hoverIndex, moved)
        }
    } else {
        boardSlots
    }
    val dragPieceId = boardSlots.getOrNull(dragFrom)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        val availableBoardW = (totalWidth - 16.dp).coerceAtLeast(100.dp)
        val availableBoardH = (totalHeight - 112.dp).coerceAtLeast(100.dp)

        val tileSize = minOf(availableBoardW / cols, availableBoardH / rows).coerceIn(30.dp, 96.dp)
        pitchHolder[0] = with(density) { tileSize.toPx() }

        val actualBoardW = tileSize * cols
        val actualBoardH = tileSize * rows

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: reference thumbnail and progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .clickable { showFullPreview = true }
                            .testTag("preview_thumbnail_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (fullImage != null) {
                            Image(
                                bitmap = fullImage,
                                contentDescription = "Artwork reference",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = imageTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = "Drag a piece anywhere. Tap picture to enlarge.",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = "Placed: $matchedCount / $totalPieces",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Play board: pick a piece, drag it anywhere, drop it; the other pieces shift
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = actualBoardW + 8.dp, height = actualBoardH + 8.dp)
                        .testTag("image_puzzle_grid_card")
                        .pointerInput(Unit) {
                            val padPx = 4.dp.toPx()

                            // Slot under the finger; outside the board the nearest slot is used,
                            // so a piece can be dropped anywhere.
                            fun slotAt(pos: Offset): Int {
                                val pitch = pitchHolder[0]
                                if (pitch <= 0f) return -1
                                val c = ((pos.x - padPx) / pitch).toInt().coerceIn(0, latestCols - 1)
                                val r = ((pos.y - padPx) / pitch).toInt().coerceIn(0, latestRows - 1)
                                return r * latestCols + c
                            }

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
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (r in 0 until rows) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (c in 0 until cols) {
                                        val slotIndex = r * cols + c
                                        val pieceId = displayed.getOrNull(slotIndex)
                                        ImageTile(
                                            sliceBitmap = pieceId?.let { slices.getOrNull(it) },
                                            isMatched = pieceId != null && pieceId == slotIndex,
                                            isPlaceholder = dragFrom >= 0 && slotIndex == hoverIndex,
                                            modifier = Modifier.size(tileSize - 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // The piece you are holding follows your finger
                    val heldBitmap = dragPieceId?.let { slices.getOrNull(it) }
                    if (heldBitmap != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        (dragPos.x - pitchHolder[0] / 2f).roundToInt(),
                                        (dragPos.y - pitchHolder[0] / 2f).roundToInt()
                                    )
                                }
                                .size(tileSize)
                                .zIndex(100f)
                                .scale(1.08f)
                                .shadow(12.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Image(
                                bitmap = heldBitmap,
                                contentDescription = "Dragging piece",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
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
                    .testTag("task_completed_button"),
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
                                contentDescription = "Full reference artwork",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showFullPreview = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close Preview", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageTile(
    sliceBitmap: ImageBitmap?,
    isMatched: Boolean,
    isPlaceholder: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isMatched) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }
    Box(
        modifier = modifier
            .alpha(if (isPlaceholder) 0.25f else 1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (isMatched) 2.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
    ) {
        if (sliceBitmap != null) {
            Image(
                bitmap = sliceBitmap,
                contentDescription = "Puzzle piece",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
