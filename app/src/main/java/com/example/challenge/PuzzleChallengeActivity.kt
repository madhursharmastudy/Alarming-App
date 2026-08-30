package com.example.challenge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

class PuzzleChallengeActivity : BaseChallengeActivity() {

    private val gridSizeState = MutableStateFlow(2) // 2 for Low (2x2), 3 for Mod (3x3), 4 for Adv (4x4)
    val gridSize = gridSizeState.asStateFlow()

    // Current tile permutation: list of slice indices currently sitting in grid positions 0..N-1
    private val tilesState = MutableStateFlow<List<Int>>(emptyList())
    val tiles = tilesState.asStateFlow()

    // Sliced ImageBitmaps for each slice index 0..N-1
    private val tileSlicesState = MutableStateFlow<List<ImageBitmap>>(emptyList())
    val tileSlices = tileSlicesState.asStateFlow()

    // Full reference ImageBitmap
    private val fullImageState = MutableStateFlow<ImageBitmap?>(null)
    val fullImage = fullImageState.asStateFlow()

    private val moveCountState = MutableStateFlow(0)
    val moveCount = moveCountState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.PUZZLE

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 120
        3 -> 180
        else -> 60
    }

    override fun onStageStarted(stage: Int) {
        val size = when (stage) {
            1 -> 2 // 2x2 grid Low
            2 -> 3 // 3x3 grid Moderate
            3 -> 4 // 4x4 grid Advanced
            else -> 2
        }
        gridSizeState.value = size
        moveCountState.value = 0
        initImagePuzzle(stage, size)
    }

    override fun onResetToStage1() {
        gridSizeState.value = 2
        moveCountState.value = 0
        initImagePuzzle(1, 2)
    }

    private fun initImagePuzzle(stage: Int, size: Int) {
        // Generate high-contrast bold morning illustration for this stage
        val sourceBitmap = createHighContrastArtwork(stage)
        fullImageState.value = sourceBitmap.asImageBitmap()

        // Slice into size x size grid tiles
        val slices = sliceBitmap(sourceBitmap, size)
        tileSlicesState.value = slices.map { it.asImageBitmap() }

        // Generate shuffled order (guaranteed not solved initially)
        val total = size * size
        val list = (0 until total).toMutableList()
        do {
            list.shuffle(Random(System.currentTimeMillis() + Random.nextLong()))
        } while (isSolved(list))

        tilesState.value = list
    }

    private fun sliceBitmap(source: Bitmap, size: Int): List<Bitmap> {
        val pieceWidth = source.width / size
        val pieceHeight = source.height / size
        val slices = mutableListOf<Bitmap>()

        for (row in 0 until size) {
            for (col in 0 until size) {
                val slice = Bitmap.createBitmap(
                    source,
                    col * pieceWidth,
                    row * pieceHeight,
                    pieceWidth,
                    pieceHeight
                )
                slices.add(slice)
            }
        }
        return slices
    }

    private fun isSolved(list: List<Int>): Boolean {
        if (list.isEmpty()) return false
        for (i in list.indices) {
            if (list[i] != i) return false
        }
        return true
    }

    fun onTileDropped(fromGridIndex: Int, targetSlot: Int) {
        val currentList = tilesState.value.toMutableList()
        val sliceIndex = currentList.getOrNull(fromGridIndex) ?: return

        if (targetSlot == sliceIndex) {
            // Correct position: swap into targetSlot and lock!
            val temp = currentList[targetSlot]
            currentList[targetSlot] = currentList[fromGridIndex]
            currentList[fromGridIndex] = temp

            tilesState.value = currentList
            moveCountState.value += 1

            if (isSolved(currentList)) {
                completeCurrentStage()
            }
        } else {
            // Incorrect position drop -> record attempt
            moveCountState.value += 1
        }
    }

    /**
     * Creates vibrant, bold, high-contrast morning artworks tailored for blurry morning vision.
     */
    private fun createHighContrastArtwork(stage: Int): Bitmap {
        val sizePx = 600
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (stage) {
            1 -> {
                // Stage 1 (2x2): "Ringing Alarm & Radiant Sun"
                // Background Gradient
                val bgGradient = LinearGradient(
                    0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                    intArrayOf(
                        AndroidColor.rgb(15, 23, 42),  // Deep Slate Navy
                        AndroidColor.rgb(88, 28, 135), // Royal Purple
                        AndroidColor.rgb(180, 83, 9)   // Warm Amber
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                paint.shader = bgGradient
                canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
                paint.shader = null

                // Radiant Sun Rays (distinct quadrants)
                paint.color = AndroidColor.argb(70, 255, 215, 0)
                paint.strokeWidth = 24f
                paint.style = Paint.Style.STROKE
                for (angle in 0 until 360 step 30) {
                    val rad = Math.toRadians(angle.toDouble())
                    val cx = sizePx / 2f
                    val cy = sizePx / 2f
                    val x2 = cx + (260 * Math.cos(rad)).toFloat()
                    val y2 = cy + (260 * Math.sin(rad)).toFloat()
                    canvas.drawLine(cx, cy, x2, y2, paint)
                }

                // Bright Glowing Sun Disc
                paint.style = Paint.Style.FILL
                val sunGradient = RadialGradient(
                    sizePx / 2f, sizePx / 2f, 180f,
                    AndroidColor.rgb(255, 230, 0),
                    AndroidColor.rgb(249, 115, 22),
                    Shader.TileMode.CLAMP
                )
                paint.shader = sunGradient
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, 160f, paint)
                paint.shader = null

                // Alarm Clock Legs
                paint.color = AndroidColor.rgb(203, 213, 225)
                paint.strokeWidth = 22f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(180f, 440f, 130f, 520f, paint)
                canvas.drawLine(420f, 440f, 470f, 520f, paint)

                // Alarm Clock Bells
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(239, 68, 68) // Bright Crimson
                canvas.drawCircle(180f, 160f, 50f, paint)
                canvas.drawCircle(420f, 160f, 50f, paint)
                paint.color = AndroidColor.WHITE
                paint.strokeWidth = 6f
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(180f, 160f, 50f, paint)
                canvas.drawCircle(420f, 160f, 50f, paint)

                // Alarm Hammer
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(226, 232, 240)
                canvas.drawRect(280f, 110f, 320f, 160f, paint)

                // Alarm Clock Body
                paint.color = AndroidColor.rgb(220, 38, 38)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(sizePx / 2f, 310f, 140f, paint)

                // Clock Body Outline
                paint.color = AndroidColor.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f
                canvas.drawCircle(sizePx / 2f, 310f, 140f, paint)

                // Clock Inner Face
                paint.color = AndroidColor.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawCircle(sizePx / 2f, 310f, 105f, paint)

                // Clock Hands (Bold contrast)
                paint.color = AndroidColor.rgb(15, 23, 42)
                paint.strokeWidth = 14f
                paint.strokeCap = Paint.Cap.ROUND
                paint.style = Paint.Style.STROKE
                // Hour hand pointing to 7
                canvas.drawLine(sizePx / 2f, 310f, 240f, 350f, paint)
                // Minute hand pointing to 12
                canvas.drawLine(sizePx / 2f, 310f, sizePx / 2f, 235f, paint)
                // Center pin
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(239, 68, 68)
                canvas.drawCircle(sizePx / 2f, 310f, 14f, paint)

                // Vibrating Action Sound Waves
                paint.style = Paint.Style.STROKE
                paint.color = AndroidColor.rgb(254, 240, 138)
                paint.strokeWidth = 8f
                canvas.drawArc(RectF(60f, 60f, 240f, 240f), 130f, 80f, false, paint)
                canvas.drawArc(RectF(360f, 60f, 540f, 240f), 330f, 80f, false, paint)
            }
            2 -> {
                // Stage 2 (3x3): "Morning Rooster & Steaming Coffee"
                val bgGradient = LinearGradient(
                    0f, 0f, 0f, sizePx.toFloat(),
                    intArrayOf(
                        AndroidColor.rgb(2, 132, 199),  // Sky Blue
                        AndroidColor.rgb(245, 158, 11), // Golden Amber
                        AndroidColor.rgb(225, 29, 72)   // Rose Crimson
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                paint.shader = bgGradient
                canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
                paint.shader = null

                // Large Morning Sun on horizon
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(254, 240, 138)
                canvas.drawCircle(sizePx / 2f, 200f, 130f, paint)

                // Rolling Green Hill
                paint.color = AndroidColor.rgb(22, 163, 74)
                val hillPath = Path()
                hillPath.moveTo(0f, 440f)
                hillPath.cubicTo(150f, 380f, 350f, 490f, sizePx.toFloat(), 400f)
                hillPath.lineTo(sizePx.toFloat(), sizePx.toFloat())
                hillPath.lineTo(0f, sizePx.toFloat())
                hillPath.close()
                canvas.drawPath(hillPath, paint)

                // Rooster Body (Left side)
                paint.color = AndroidColor.rgb(185, 28, 28) // Deep Crimson
                canvas.drawCircle(190f, 340f, 65f, paint)

                // Rooster Head & Beak
                paint.color = AndroidColor.rgb(220, 38, 38)
                canvas.drawCircle(150f, 260f, 40f, paint)
                // Yellow Beak
                paint.color = AndroidColor.rgb(250, 204, 21)
                val beakPath = Path()
                beakPath.moveTo(115f, 255f)
                beakPath.lineTo(75f, 270f)
                beakPath.lineTo(120f, 285f)
                beakPath.close()
                canvas.drawPath(beakPath, paint)

                // Rooster Comb (Top red spikes)
                paint.color = AndroidColor.rgb(239, 68, 68)
                canvas.drawCircle(135f, 220f, 18f, paint)
                canvas.drawCircle(155f, 210f, 20f, paint)
                canvas.drawCircle(175f, 220f, 18f, paint)

                // Rooster Eye
                paint.color = AndroidColor.WHITE
                canvas.drawCircle(135f, 255f, 9f, paint)
                paint.color = AndroidColor.BLACK
                canvas.drawCircle(133f, 255f, 5f, paint)

                // Rooster Tail Feathers (Vibrant Emerald & Gold)
                val tailPath = Path()
                tailPath.moveTo(220f, 320f)
                tailPath.cubicTo(310f, 220f, 290f, 160f, 260f, 180f)
                tailPath.cubicTo(290f, 230f, 250f, 300f, 210f, 350f)
                tailPath.close()
                paint.color = AndroidColor.rgb(5, 150, 105)
                canvas.drawPath(tailPath, paint)

                // Steaming Coffee Cup (Right side)
                paint.color = AndroidColor.rgb(14, 116, 144) // Teal Mug
                val mugRect = RectF(370f, 320f, 520f, 460f)
                canvas.drawRoundRect(mugRect, 24f, 24f, paint)
                // Mug Handle
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 18f
                paint.color = AndroidColor.rgb(14, 116, 144)
                canvas.drawArc(RectF(480f, 340f, 570f, 430f), 270f, 180f, false, paint)

                // Mug Logo Star
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(250, 204, 21)
                canvas.drawCircle(445f, 390f, 22f, paint)

                // Coffee Steam Swirls
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = AndroidColor.argb(220, 255, 255, 255)
                val steam1 = Path()
                steam1.moveTo(410f, 300f)
                steam1.cubicTo(395f, 260f, 430f, 230f, 415f, 190f)
                canvas.drawPath(steam1, paint)

                val steam2 = Path()
                steam2.moveTo(475f, 300f)
                steam2.cubicTo(490f, 255f, 455f, 225f, 470f, 180f)
                canvas.drawPath(steam2, paint)
            }
            else -> {
                // Stage 3 (4x4): "Dawn Mountain Peak & Golden Sunburst"
                val bgGradient = LinearGradient(
                    0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                    intArrayOf(
                        AndroidColor.rgb(17, 24, 39),   // Night Sky
                        AndroidColor.rgb(79, 70, 229),  // Indigo
                        AndroidColor.rgb(234, 88, 12),  // Sunrise Orange
                        AndroidColor.rgb(250, 204, 21)  // Dawn Gold
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                paint.shader = bgGradient
                canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
                paint.shader = null

                // Rotating 16-Ray Sunburst in Center
                val cx = sizePx / 2f
                val cy = 250f
                val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                rayPaint.style = Paint.Style.FILL
                for (i in 0 until 16) {
                    val angle1 = i * 22.5
                    val angle2 = angle1 + 11.25
                    val path = Path()
                    path.moveTo(cx, cy)
                    val r = 380.0
                    val x1 = cx + (r * Math.cos(Math.toRadians(angle1))).toFloat()
                    val y1 = cy + (r * Math.sin(Math.toRadians(angle1))).toFloat()
                    val x2 = cx + (r * Math.cos(Math.toRadians(angle2))).toFloat()
                    val y2 = cy + (r * Math.sin(Math.toRadians(angle2))).toFloat()
                    path.lineTo(x1, y1)
                    path.lineTo(x2, y2)
                    path.close()

                    rayPaint.color = if (i % 2 == 0) {
                        AndroidColor.argb(130, 255, 238, 88)
                    } else {
                        AndroidColor.argb(100, 255, 112, 67)
                    }
                    canvas.drawPath(path, rayPaint)
                }

                // Central Radiant Sun Core
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.rgb(254, 240, 138)
                canvas.drawCircle(cx, cy, 100f, paint)
                paint.color = AndroidColor.WHITE
                canvas.drawCircle(cx, cy, 65f, paint)

                // Background Mountain Range (Dark Violet)
                val mtnBack = Path()
                mtnBack.moveTo(0f, 480f)
                mtnBack.lineTo(160f, 320f)
                mtnBack.lineTo(340f, 460f)
                mtnBack.lineTo(500f, 300f)
                mtnBack.lineTo(sizePx.toFloat(), 450f)
                mtnBack.lineTo(sizePx.toFloat(), sizePx.toFloat())
                mtnBack.lineTo(0f, sizePx.toFloat())
                mtnBack.close()
                paint.color = AndroidColor.rgb(67, 56, 202)
                canvas.drawPath(mtnBack, paint)

                // Foreground Main Mountain (High contrast Deep Teal/Slate)
                val mtnMain = Path()
                mtnMain.moveTo(100f, sizePx.toFloat())
                mtnMain.lineTo(sizePx / 2f, 290f)
                mtnMain.lineTo(520f, sizePx.toFloat())
                mtnMain.close()
                paint.color = AndroidColor.rgb(15, 118, 110)
                canvas.drawPath(mtnMain, paint)

                // Snow Cap on Mountain Peak
                val snowCap = Path()
                snowCap.moveTo(sizePx / 2f, 290f)
                snowCap.lineTo(260f, 360f)
                snowCap.lineTo(285f, 345f)
                snowCap.lineTo(300f, 370f)
                snowCap.lineTo(325f, 350f)
                snowCap.lineTo(340f, 365f)
                snowCap.close()
                paint.color = AndroidColor.WHITE
                canvas.drawPath(snowCap, paint)

                // Flying Wake Birds in sky
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = AndroidColor.rgb(15, 23, 42)

                // Bird 1
                val bird1 = Path()
                bird1.moveTo(120f, 130f)
                bird1.quadTo(140f, 110f, 160f, 130f)
                bird1.quadTo(180f, 110f, 200f, 130f)
                canvas.drawPath(bird1, paint)

                // Bird 2
                val bird2 = Path()
                bird2.moveTo(420f, 110f)
                bird2.quadTo(435f, 95f, 450f, 110f)
                bird2.quadTo(465f, 95f, 480f, 110f)
                canvas.drawPath(bird2, paint)
            }
        }

        return bitmap
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val size by gridSize.collectAsState()
        val tilesList by tiles.collectAsState()
        val slices by tileSlices.collectAsState()
        val fullRefImage by fullImage.collectAsState()
        val moves by moveCount.collectAsState()

        val totalTiles = size * size
        val solvedCount = tilesList.indices.count { tilesList.getOrNull(it) == it }
        val cellBounds = remember { mutableStateMapOf<Int, Rect>() }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Objective & Reference Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${size}x${size} Picture Drag & Drop",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "Drag unlocked tiles to where they belong",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Matched: $solvedCount / $totalTiles",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (solvedCount == totalTiles) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "•  $moves Moves",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Target Reference Thumbnail
                    if (fullRefImage != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .shadow(2.dp, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = fullRefImage!!,
                                    contentDescription = "Target Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = "Target",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Big High-Contrast Image Rearrangement Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A))
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (slices.isNotEmpty() && tilesList.size == totalTiles) {
                    val gridSpacing = when (size) {
                        2 -> 8.dp
                        3 -> 6.dp
                        else -> 4.dp
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(size),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(gridSpacing),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(tilesList) { gridIndex, sliceIndex ->
                            val sliceBitmap = slices.getOrNull(sliceIndex)
                            val isCorrect = sliceIndex == gridIndex

                            ImagePuzzleTileItem(
                                sliceBitmap = sliceBitmap,
                                gridIndex = gridIndex,
                                sliceIndex = sliceIndex,
                                isCorrect = isCorrect,
                                gridSize = size,
                                onPositioned = { rect ->
                                    cellBounds[gridIndex] = rect
                                },
                                findTargetSlot = { dropCenter ->
                                    val exact = cellBounds.entries.find { (_, rect) -> rect.contains(dropCenter) }?.key
                                    if (exact != null) {
                                        exact
                                    } else {
                                        cellBounds.entries.minByOrNull { (_, rect) ->
                                            (rect.center - dropCenter).getDistanceSquared()
                                        }?.takeIf { (_, rect) ->
                                            val maxDist = rect.width * 0.9f
                                            (rect.center - dropCenter).getDistance() <= maxDist
                                        }?.key
                                    }
                                },
                                onDropped = { targetSlot ->
                                    onTileDropped(gridIndex, targetSlot)
                                }
                            )
                        }
                    }
                }
            }

            // Status Bar & Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (solvedCount == totalTiles) Icons.Default.Check else Icons.Default.PanTool,
                        contentDescription = null,
                        tint = if (solvedCount == totalTiles) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (solvedCount == totalTiles) {
                            "All tiles locked! Stage complete!"
                        } else {
                            "Drag a tile to its target spot to lock it in place"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ImagePuzzleTileItem(
    sliceBitmap: ImageBitmap?,
    gridIndex: Int,
    sliceIndex: Int,
    isCorrect: Boolean,
    gridSize: Int,
    onPositioned: (Rect) -> Unit,
    findTargetSlot: (Offset) -> Int?,
    onDropped: (Int) -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var itemBounds by remember { mutableStateOf(Rect.Zero) }

    val cornerRadius = when (gridSize) {
        2 -> 14.dp
        3 -> 10.dp
        else -> 8.dp
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1.0f,
        animationSpec = spring(),
        label = "tileScale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isDragging -> Color(0xFFFACC15) // Bright Gold while dragging
            isCorrect -> Color(0xFF22C55E).copy(alpha = 0.9f) // Green when locked
            else -> Color.White.copy(alpha = 0.35f)
        },
        label = "tileBorder"
    )

    val borderWidth = when {
        isDragging -> 3.5.dp
        isCorrect -> 2.5.dp
        else -> 1.5.dp
    }

    val dragModifier = if (!isCorrect) {
        Modifier.pointerInput(gridIndex, sliceIndex, isCorrect) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                    dragOffset = Offset.Zero
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                },
                onDragEnd = {
                    val dropCenter = itemBounds.center + dragOffset
                    val targetSlot = findTargetSlot(dropCenter)
                    if (targetSlot != null) {
                        onDropped(targetSlot)
                    }
                    isDragging = false
                    dragOffset = Offset.Zero
                },
                onDragCancel = {
                    isDragging = false
                    dragOffset = Offset.Zero
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 100f else 1f)
            .offset {
                if (isDragging) {
                    IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                } else {
                    IntOffset.Zero
                }
            }
            .scale(animatedScale)
            .shadow(if (isDragging) 16.dp else 0.dp, RoundedCornerShape(cornerRadius))
            .aspectRatio(1f)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInParent()
                itemBounds = bounds
                onPositioned(bounds)
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF1E293B))
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .then(dragModifier)
            .testTag("puzzle_tile_$gridIndex"),
        contentAlignment = Alignment.Center
    ) {
        if (sliceBitmap != null) {
            Image(
                bitmap = sliceBitmap,
                contentDescription = "Puzzle Tile $sliceIndex",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Active Dragging Glow Overlay
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFACC15).copy(alpha = 0.15f))
            )
        }

        // Green Checkmark & Lock Badge when correctly placed
        if (isCorrect) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(if (gridSize <= 3) 20.dp else 16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Locked",
                    tint = Color.White,
                    modifier = Modifier.size(if (gridSize <= 3) 14.dp else 10.dp)
                )
            }
        }
    }
}

