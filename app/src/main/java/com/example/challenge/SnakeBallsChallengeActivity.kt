package com.example.challenge

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ChallengeType
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.OceanCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.random.Random

data class SnakeCell(val x: Int, val y: Int)

data class SnakeGameState(
    val snake: List<SnakeCell>,
    val ball: SnakeCell,
    val collected: Int,
    val target: Int
)

/**
 * Snake & Balls: steer the snake to the glowing ball. Each ball collected makes the snake longer and
 * faster, then a new ball appears. Crash into a wall or yourself and the balls start again from 0.
 * Low: 3 balls, Moderate: 6 balls, Difficult: 10 balls.
 */
class SnakeBallsChallengeActivity : BaseChallengeActivity() {

    companion object {
        const val COLS = 11
        const val ROWS = 15
    }

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0

    private val gameState = MutableStateFlow(SnakeGameState(emptyList(), SnakeCell(0, 0), 0, 3))
    val state = gameState.asStateFlow()

    private var dirX = 1
    private var dirY = 0
    private var pendingX = 1
    private var pendingY = 0
    private var baseTickMs = 380L

    override fun getChallengeType(): ChallengeType = ChallengeType.SNAKE_BALLS

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 60
        2 -> 80
        else -> 100
    }

    override fun onStageStarted(stage: Int) {
        beginStage(stage)
    }

    override fun onResetToStage1() {
        beginStage(1)
    }

    private fun beginStage(stage: Int) {
        val target = when (stage) {
            1 -> 3
            2 -> 6
            else -> 10
        }
        baseTickMs = when (stage) {
            1 -> 380L
            2 -> 300L
            else -> 230L
        }
        gameState.value = newState(target)
        dirX = 1
        dirY = 0
        pendingX = 1
        pendingY = 0
        setFeedbackMessage(null)
        token++
        scheduleTick()
    }

    private fun newState(target: Int): SnakeGameState {
        val midY = ROWS / 2
        val snake = listOf(SnakeCell(4, midY), SnakeCell(3, midY), SnakeCell(2, midY))
        return SnakeGameState(snake, randomBall(snake), 0, target)
    }

    private fun randomBall(snake: List<SnakeCell>): SnakeCell {
        val free = ArrayList<SnakeCell>()
        for (x in 0 until COLS) {
            for (y in 0 until ROWS) {
                val cell = SnakeCell(x, y)
                if (!snake.contains(cell)) free.add(cell)
            }
        }
        return free[Random.nextInt(free.size)]
    }

    private fun scheduleTick() {
        val myToken = token
        val collected = gameState.value.collected
        val delayMs = (baseTickMs - 12L * collected).coerceAtLeast(120L)
        handler.postDelayed({
            if (myToken == token && !isCompletedState.value) {
                tick()
                if (myToken == token && !isCompletedState.value) scheduleTick()
            }
        }, delayMs)
    }

    private fun tick() {
        val s = gameState.value
        if (s.snake.isEmpty()) return
        dirX = pendingX
        dirY = pendingY
        val head = s.snake.first()
        val next = SnakeCell(head.x + dirX, head.y + dirY)
        val hitWall = next.x < 0 || next.y < 0 || next.x >= COLS || next.y >= ROWS
        val willEat = next == s.ball
        val body = if (willEat) s.snake else s.snake.dropLast(1)

        if (hitWall || body.contains(next)) {
            setFeedbackMessage("Crash! Balls reset to 0", isError = true)
            gameState.value = newState(s.target)
            dirX = 1
            dirY = 0
            pendingX = 1
            pendingY = 0
            return
        }

        val newSnake = listOf(next) + body
        if (willEat) {
            val collected = s.collected + 1
            if (collected >= s.target) {
                gameState.value = SnakeGameState(newSnake, s.ball, collected, s.target)
                setFeedbackMessage("All balls collected!", isSuccess = true)
                completeCurrentStage()
            } else {
                gameState.value = SnakeGameState(newSnake, randomBall(newSnake), collected, s.target)
            }
        } else {
            gameState.value = SnakeGameState(newSnake, s.ball, s.collected, s.target)
        }
    }

    fun changeDirection(dx: Int, dy: Int) {
        // The snake cannot turn back on itself
        if (dx == -dirX && dy == -dirY) return
        pendingX = dx
        pendingY = dy
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val s by state.collectAsState()
        SnakeBallsScreen(
            state = s,
            onDirection = { dx, dy -> changeDirection(dx, dy) },
            modifier = modifier
        )
    }
}

@Composable
fun SnakeBallsScreen(
    state: SnakeGameState,
    onDirection: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cols = SnakeBallsChallengeActivity.COLS
    val rows = SnakeBallsChallengeActivity.ROWS

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        GameInfoPill(text = "Balls  ${state.collected} / ${state.target}")
        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val boardW = minOf(maxWidth, maxHeight * (cols.toFloat() / rows.toFloat()))
            val boardH = boardW * (rows.toFloat() / cols.toFloat())

            Canvas(
                modifier = Modifier
                    .size(boardW, boardH)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F2038))
                    .testTag("snake_board")
                    .pointerInput(Unit) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                if (abs(total.x) > 40f || abs(total.y) > 40f) {
                                    if (abs(total.x) > abs(total.y)) {
                                        onDirection(if (total.x > 0f) 1 else -1, 0)
                                    } else {
                                        onDirection(0, if (total.y > 0f) 1 else -1)
                                    }
                                    total = Offset.Zero
                                }
                            }
                        )
                    }
            ) {
                val cell = size.width / cols

                for (x in 0 until cols) {
                    for (y in 0 until rows) {
                        if ((x + y) % 2 == 0) {
                            drawRect(
                                color = Color(0xFF12294A),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }
                }

                val ballCenter = Offset((state.ball.x + 0.5f) * cell, (state.ball.y + 0.5f) * cell)
                drawCircle(color = Color(0x66FFB84D), radius = cell * 0.55f, center = ballCenter)
                drawCircle(color = Color(0xFFFFB84D), radius = cell * 0.36f, center = ballCenter)

                val count = state.snake.size.coerceAtLeast(1)
                state.snake.forEachIndexed { i, c ->
                    val t = (i.toFloat() / count.toFloat()).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = lerp(OceanCyan, OceanBlue, t),
                        topLeft = Offset(c.x * cell + cell * 0.07f, c.y * cell + cell * 0.07f),
                        size = Size(cell * 0.86f, cell * 0.86f),
                        cornerRadius = CornerRadius(cell * 0.3f)
                    )
                }
                if (state.snake.isNotEmpty()) {
                    val head = state.snake.first()
                    val hc = Offset((head.x + 0.5f) * cell, (head.y + 0.5f) * cell)
                    drawCircle(color = Color.White, radius = cell * 0.12f, center = Offset(hc.x - cell * 0.18f, hc.y - cell * 0.1f))
                    drawCircle(color = Color.White, radius = cell * 0.12f, center = Offset(hc.x + cell * 0.18f, hc.y - cell * 0.1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GameKey(label = "\u25C0") { onDirection(-1, 0) }
            GameKey(label = "\u25B2") { onDirection(0, -1) }
            GameKey(label = "\u25BC") { onDirection(0, 1) }
            GameKey(label = "\u25B6") { onDirection(1, 0) }
        }
        GameHint(text = "Swipe on the board or use the arrows. Hit a wall or yourself and you start again.")
    }
}
