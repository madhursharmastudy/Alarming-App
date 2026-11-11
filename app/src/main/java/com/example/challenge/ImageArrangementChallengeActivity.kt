package com.example.challenge

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Space around a piece's cell (as a fraction of the cell) so the round tabs fit inside its box. */
private const val MARGIN_RATIO = 0.32f

/**
 * Jigsaw puzzle: loose pieces with real tabs and blanks sit in a tray. Drag a piece onto the board;
 * when it is close to its right place it snaps and locks. Stage 1 shows a faint picture on the
 * board, stage 2 only the piece outlines, stage 3 an empty board (use the reference picture).
 */
class ImageArrangementChallengeActivity : BaseChallengeActivity() {

    private val colsState = MutableStateFlow(2)
    private val rowsState = MutableStateFlow(3)
    private val fullImageState = MutableStateFlow<ImageBitmap?>(null)
    private val imageTitleState = MutableStateFlow("Artwork")
    private val stageModeState = MutableStateFlow(1)

    // Pieces still in the tray (ids = row * cols + col), and pieces already locked on the board
    private val trayState = MutableStateFlow<List<Int>>(emptyList())
    private val lockedState = MutableStateFlow<Set<Int>>(emptySet())

    // Tab direction of every inner edge (+1 tab outwards, -1 blank), shared by both neighbours
    private val vSignsState = MutableStateFlow<List<Int>>(emptyList())
    private val hSignsState = MutableStateFlow<List<Int>>(emptyList())

    private var previousResId: Int? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.IMAGE_ARRANGEMENT

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 90
        2 -> 150
        3 -> 240
        else -> 90
    }

    override fun onStageStarted(stage: Int) {
        setupStage(stage)
    }

    override fun onResetToStage1() {
        setupStage(1)
    }

    private fun setupStage(stage: Int) {
        val (numCols, numRows) = when (stage) {
            1 -> 2 to 3   // 6 pieces
            2 -> 3 to 4   // 12 pieces
            3 -> 4 to 5   // 20 pieces
            else -> 2 to 3
        }

        colsState.value = numCols
        rowsState.value = numRows
        stageModeState.value = stage.coerceIn(1, 3)
        setFeedbackMessage(null)

        val item = ImageArrangementData.pickRandomImage(previousResId)
        previousResId = item.resId
        imageTitleState.value = item.title

        val unitTileSize = 180
        val fullBitmap = ImageArrangementData.renderDrawableToBitmap(
            this, item.resId, numCols * unitTileSize, numRows * unitTileSize
        )
        fullImageState.value = fullBitmap.asImageBitmap()

        val random = Random(System.currentTimeMillis())
        vSignsState.value = List((numCols - 1) * numRows) { if (random.nextBoolean()) 1 else -1 }
        hSignsState.value = List(numCols * (numRows - 1)) { if (random.nextBoolean()) 1 else -1 }

        lockedState.value = emptySet()
        trayState.value = (0 until numCols * numRows).shuffled(random)
    }

    fun onPieceSnapped(pieceId: Int) {
        lockedState.value = lockedState.value + pieceId
        trayState.value = trayState.value - pieceId
        setFeedbackMessage(null)
        if (trayState.value.isEmpty()) {
            setFeedbackMessage("Picture completed! Stage Passed!", isSuccess = true)
            completeCurrentStage()
        }
    }

    fun onPieceMissed() {
        setFeedbackMessage("That piece does not fit there. Try another spot.", isError = true)
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val cols by colsState.collectAsState()
        val rows by rowsState.collectAsState()
        val stage by stageModeState.collectAsState()
        val tray by trayState.collectAsState()
        val locked by lockedState.collectAsState()
        val vSigns by vSignsState.collectAsState()
        val hSigns by hSignsState.collectAsState()
        val fullImg by fullImageState.collectAsState()
        val title by imageTitleState.collectAsState()

        JigsawScreen(
            cols = cols,
            rows = rows,
            stage = stage,
            trayPieces = tray,
            lockedPieces = locked,
            vSigns = vSigns,
            hSigns = hSigns,
            fullImage = fullImg,
            imageTitle = title,
            onSnap = { onPieceSnapped(it) },
            onMiss = { onPieceMissed() },
            modifier = modifier
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Jigsaw piece shape
// ---------------------------------------------------------------------------------------------

private fun Path.cubic(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)

/** One edge from p0 to p1. sign 0 = flat border, +1 = round tab outwards, -1 = blank inwards. */
private fun Path.addEdge(p0: Offset, p1: Offset, normal: Offset, sign: Int) {
    if (sign == 0) {
        lineTo(p1.x, p1.y)
        return
    }
    val dx = p1.x - p0.x
    val dy = p1.y - p0.y
    val len = hypot(dx, dy)
    val ux = dx / len
    val uy = dy / len

    // t = position along the edge (0..1), d = depth of the tab (fraction of edge length)
    fun pt(t: Float, d: Float): Offset {
        val along = t * len
        val out = d * len * sign
        return Offset(p0.x + ux * along + normal.x * out, p0.y + uy * along + normal.y * out)
    }

    val a = pt(0.38f, 0f)
    lineTo(a.x, a.y)
    cubic(pt(0.42f, 0f), pt(0.46f, 0.04f), pt(0.46f, 0.09f))      // up into the neck
    cubic(pt(0.455f, 0.13f), pt(0.36f, 0.11f), pt(0.36f, 0.18f))  // flare to the left of the knob
    cubic(pt(0.36f, 0.25f), pt(0.43f, 0.28f), pt(0.50f, 0.28f))   // top left of the knob
    cubic(pt(0.57f, 0.28f), pt(0.64f, 0.25f), pt(0.64f, 0.18f))   // top right of the knob
    cubic(pt(0.64f, 0.11f), pt(0.545f, 0.13f), pt(0.54f, 0.09f))  // flare back to the neck
    cubic(pt(0.54f, 0.04f), pt(0.58f, 0f), pt(0.62f, 0f))         // down to the edge
    lineTo(p1.x, p1.y)
}

/**
 * Outline of one piece in its own box: the cell sits at (m, m) with size s, tabs stick out into the
 * margin m. Neighbouring pieces get opposite signs on the shared edge, so they fit exactly.
 */
private fun jigsawPiecePath(
    pieceId: Int,
    cols: Int,
    rows: Int,
    vSigns: List<Int>,
    hSigns: List<Int>,
    s: Float
): Path {
    val c = pieceId % cols
    val r = pieceId / cols
    val m = s * MARGIN_RATIO

    val tl = Offset(m, m)
    val tr = Offset(m + s, m)
    val br = Offset(m + s, m + s)
    val bl = Offset(m, m + s)

    val top = if (r == 0) 0 else -(hSigns.getOrElse((r - 1) * cols + c) { 1 })
    val right = if (c == cols - 1) 0 else vSigns.getOrElse(r * (cols - 1) + c) { 1 }
    val bottom = if (r == rows - 1) 0 else hSigns.getOrElse(r * cols + c) { 1 }
    val left = if (c == 0) 0 else -(vSigns.getOrElse(r * (cols - 1) + c - 1) { 1 })

    return Path().apply {
        moveTo(tl.x, tl.y)
        addEdge(tl, tr, Offset(0f, -1f), top)
        addEdge(tr, br, Offset(1f, 0f), right)
        addEdge(br, bl, Offset(0f, 1f), bottom)
        addEdge(bl, tl, Offset(-1f, 0f), left)
        close()
    }
}

@Composable
fun JigsawPieceView(
    pieceId: Int,
    cols: Int,
    rows: Int,
    vSigns: List<Int>,
    hSigns: List<Int>,
    image: ImageBitmap?,
    cellSize: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val density = LocalDensity.current
    val sPx = with(density) { cellSize.toPx() }
    val path = remember(pieceId, cols, rows, vSigns, hSigns, sPx) {
        jigsawPiecePath(pieceId, cols, rows, vSigns, hSigns, sPx)
    }
    val boxSize = cellSize * (1f + 2f * MARGIN_RATIO)
    val strokePx = with(density) { 1.5.dp.toPx() }

    Canvas(modifier = modifier.size(boxSize)) {
        val m = sPx * MARGIN_RATIO
        val c = pieceId % cols
        val r = pieceId / cols
        clipPath(path) {
            if (image != null) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset((m - c * sPx).roundToInt(), (m - r * sPx).roundToInt()),
                    dstSize = IntSize((cols * sPx).roundToInt(), (rows * sPx).roundToInt()),
                    alpha = alpha
                )
            }
        }
        drawPath(path, color = Color.Black.copy(alpha = 0.55f * alpha), style = Stroke(width = strokePx))
    }
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

private class JigsawGeometry {
    var outerOrigin = Offset.Zero   // root position of the whole screen box
    var boardOrigin = Offset.Zero   // root position of the board's top-left cell corner
    var trayOrigin = Offset.Zero    // root position of the tray box
    var cellPx = 0f
    var boardRows = 1
    var trayBoxPx = 0f
    var trayPadPx = 0f
    var trayCols = 1
}

@Composable
fun JigsawScreen(
    cols: Int,
    rows: Int,
    stage: Int,
    trayPieces: List<Int>,
    lockedPieces: Set<Int>,
    vSigns: List<Int>,
    hSigns: List<Int>,
    fullImage: ImageBitmap?,
    imageTitle: String,
    onSnap: (Int) -> Unit,
    onMiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val geo = remember { JigsawGeometry() }
    val latestTray by rememberUpdatedState(trayPieces)
    val latestOnSnap by rememberUpdatedState(onSnap)
    val latestOnMiss by rememberUpdatedState(onMiss)
    val latestCols by rememberUpdatedState(cols)

    var dragPiece by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var showFullPreview by remember { mutableStateOf(false) }

    LaunchedEffect(cols, rows, fullImage) { dragPiece = -1 }

    val totalPieces = cols * rows
    val boxRatio = 1f + 2f * MARGIN_RATIO

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { geo.outerOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                fun trayPieceAt(pos: Offset): Int {
                    val root = pos + geo.outerOrigin
                    val relX = root.x - geo.trayOrigin.x - geo.trayPadPx
                    val relY = root.y - geo.trayOrigin.y - geo.trayPadPx
                    if (relX < 0f || relY < 0f || geo.trayBoxPx <= 0f) return -1
                    val col = (relX / geo.trayBoxPx).toInt()
                    val row = (relY / geo.trayBoxPx).toInt()
                    if (col >= geo.trayCols) return -1
                    return latestTray.getOrNull(row * geo.trayCols + col) ?: -1
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        val piece = trayPieceAt(offset)
                        if (piece >= 0) {
                            dragPiece = piece
                            dragPos = offset
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (dragPiece >= 0) {
                            change.consume()
                            dragPos += dragAmount
                        }
                    },
                    onDragEnd = {
                        val piece = dragPiece
                        if (piece >= 0 && geo.cellPx > 0f) {
                            val root = dragPos + geo.outerOrigin
                            val c = piece % latestCols
                            val r = piece / latestCols
                            val center = Offset(
                                geo.boardOrigin.x + (c + 0.5f) * geo.cellPx,
                                geo.boardOrigin.y + (r + 0.5f) * geo.cellPx
                            )
                            val distance = hypot(root.x - center.x, root.y - center.y)
                            val snapRadius = max(geo.cellPx * 0.6f, 24.dp.toPx())
                            if (distance <= snapRadius) {
                                latestOnSnap(piece)
                            } else {
                                val insideBoard = root.x >= geo.boardOrigin.x - geo.cellPx * 0.3f &&
                                    root.y >= geo.boardOrigin.y - geo.cellPx * 0.3f &&
                                    root.x <= geo.boardOrigin.x + geo.cellPx * (latestCols + 0.3f) &&
                                    root.y <= geo.boardOrigin.y + geo.cellPx * (geo.boardRows + 0.3f)
                                if (insideBoard) latestOnMiss()
                            }
                        }
                        dragPiece = -1
                    },
                    onDragCancel = { dragPiece = -1 }
                )
            }
    ) {
        val density = LocalDensity.current
        val totalW = maxWidth
        val totalH = maxHeight

        val trayH = (totalH * 0.34f).coerceIn(96.dp, 200.dp)
        val boardAreaH = (totalH - 48.dp - trayH - 24.dp).coerceAtLeast(120.dp)
        val cell = minOf((totalW - 16.dp) / cols, boardAreaH / rows).coerceIn(24.dp, 90.dp)

        // Biggest tray piece size so that ALL pieces fit in the tray without scrolling
        val trayW = totalW - 8.dp
        var trayCellDp = minOf(cell.value, 54f)
        var trayCols = 1
        while (trayCellDp > 14f) {
            val box = trayCellDp * boxRatio
            trayCols = max(1, ((trayW.value - 8f) / box).toInt())
            val trayRows = ceil(totalPieces / trayCols.toFloat()).toInt()
            if (trayRows * box <= trayH.value - 8f) break
            trayCellDp -= 2f
        }
        val trayCell = trayCellDp.dp
        val trayBox = trayCell * boxRatio

        geo.cellPx = with(density) { cell.toPx() }
        geo.boardRows = rows
        geo.trayBoxPx = with(density) { trayBox.toPx() }
        geo.trayPadPx = with(density) { 4.dp.toPx() }
        geo.trayCols = trayCols

        val ghostPaths = remember(cols, rows, vSigns, hSigns, geo.cellPx) {
            (0 until cols * rows).map { jigsawPiecePath(it, cols, rows, vSigns, hSigns, geo.cellPx) }
        }
        val ghostLine = MaterialTheme.colorScheme.onSurfaceVariant
        val boardBg = MaterialTheme.colorScheme.surfaceVariant
        val borderColor = MaterialTheme.colorScheme.primary

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
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
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
                            text = when (stage) {
                                1 -> "Drag pieces onto the faint picture"
                                2 -> "Drag pieces to the outlines. Tap picture for help"
                                else -> "Empty board: tap the picture to see it"
                            },
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = "Placed: ${lockedPieces.size} / $totalPieces",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Board
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(cell * cols, cell * rows)
                        .testTag("image_puzzle_grid_card")
                        .onGloballyPositioned { geo.boardOrigin = it.positionInRoot() }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = boardBg.copy(alpha = 0.45f),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )
                        if (stage == 1 && fullImage != null) {
                            drawImage(
                                image = fullImage,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                alpha = 0.28f
                            )
                        }
                        if (stage <= 2) {
                            val m = geo.cellPx * MARGIN_RATIO
                            for (p in ghostPaths.indices) {
                                val c = p % cols
                                val r = p / cols
                                translate(left = c * geo.cellPx - m, top = r * geo.cellPx - m) {
                                    drawPath(
                                        path = ghostPaths[p],
                                        color = ghostLine.copy(alpha = 0.35f),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                        }
                        drawRoundRect(
                            color = borderColor.copy(alpha = 0.45f),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Pieces that are already locked in place
                    val margin = cell * MARGIN_RATIO
                    for (p in lockedPieces) {
                        val c = p % cols
                        val r = p / cols
                        JigsawPieceView(
                            pieceId = p,
                            cols = cols,
                            rows = rows,
                            vSigns = vSigns,
                            hSigns = hSigns,
                            image = fullImage,
                            cellSize = cell,
                            modifier = Modifier.offset(x = cell * c - margin, y = cell * r - margin)
                        )
                    }
                }
            }

            // Tray with all loose pieces (nothing scrolls)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trayH)
                    .onGloballyPositioned { geo.trayOrigin = it.positionInRoot() }
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .testTag("jigsaw_tray")
            ) {
                trayPieces.forEachIndexed { i, pieceId ->
                    val col = i % trayCols
                    val row = i / trayCols
                    JigsawPieceView(
                        pieceId = pieceId,
                        cols = cols,
                        rows = rows,
                        vSigns = vSigns,
                        hSigns = hSigns,
                        image = fullImage,
                        cellSize = trayCell,
                        alpha = if (pieceId == dragPiece) 0.2f else 1f,
                        modifier = Modifier.offset(x = 4.dp + trayBox * col, y = 4.dp + trayBox * row)
                    )
                }
            }
        }

        // The piece you are holding: bigger (board size), centered on your finger
        if (dragPiece >= 0) {
            val box = cell * boxRatio
            val boxPx = with(density) { box.toPx() }
            JigsawPieceView(
                pieceId = dragPiece,
                cols = cols,
                rows = rows,
                vSigns = vSigns,
                hSigns = hSigns,
                image = fullImage,
                cellSize = cell,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (dragPos.x - boxPx / 2f).roundToInt(),
                            (dragPos.y - boxPx / 2f).roundToInt()
                        )
                    }
                    .zIndex(100f)
            )
        }

        // Full image reference dialog
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
                                    .aspectRatio(cols.toFloat() / rows.toFloat())
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
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
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Close Preview", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
