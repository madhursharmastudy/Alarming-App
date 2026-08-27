package com.example.challenge

import android.os.Bundle
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class PuzzleChallengeActivity : BaseChallengeActivity() {

    private val gridSizeState = MutableStateFlow(2) // 2 for Low (2x2), 3 for Mod (3x3), 4 for Adv (4x4)
    val gridSize = gridSizeState.asStateFlow()

    private val tilesState = MutableStateFlow<List<Int>>(emptyList())
    val tiles = tilesState.asStateFlow()

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
        initPuzzle(size)
    }

    override fun onResetToStage1() {
        gridSizeState.value = 2
        moveCountState.value = 0
        initPuzzle(2)
    }

    private fun initPuzzle(size: Int) {
        val totalTiles = size * size
        // 0 represents blank tile, 1..(totalTiles - 1) represent numbered tiles
        var list = (1 until totalTiles).toList() + listOf(0)

        // Shuffle by making valid random moves from solved state to guarantee solvability
        var blankIndex = totalTiles - 1
        val movesToShuffle = when (size) {
            2 -> 8
            3 -> 20
            else -> 35
        }

        val mutable = list.toMutableList()
        var lastMovedIndex = -1

        for (i in 0 until movesToShuffle) {
            val neighbors = getValidNeighbors(blankIndex, size).filter { it != lastMovedIndex }
            if (neighbors.isNotEmpty()) {
                val chosen = neighbors.random()
                mutable[blankIndex] = mutable[chosen]
                mutable[chosen] = 0
                lastMovedIndex = blankIndex
                blankIndex = chosen
            }
        }

        // If by chance it is already solved, make 1 swap
        if (isSolved(mutable, size)) {
            val neighbors = getValidNeighbors(blankIndex, size)
            if (neighbors.isNotEmpty()) {
                val chosen = neighbors.first()
                mutable[blankIndex] = mutable[chosen]
                mutable[chosen] = 0
            }
        }

        tilesState.value = mutable
    }

    private fun getValidNeighbors(index: Int, size: Int): List<Int> {
        val row = index / size
        val col = index % size
        val neighbors = mutableListOf<Int>()

        if (row > 0) neighbors.add((row - 1) * size + col) // Up
        if (row < size - 1) neighbors.add((row + 1) * size + col) // Down
        if (col > 0) neighbors.add(row * size + (col - 1)) // Left
        if (col < size - 1) neighbors.add(row * size + (col + 1)) // Right

        return neighbors
    }

    fun onTileClicked(index: Int) {
        val size = gridSizeState.value
        val list = tilesState.value.toMutableList()
        val blankIndex = list.indexOf(0)

        if (blankIndex != -1 && isAdjacent(index, blankIndex, size)) {
            list[blankIndex] = list[index]
            list[index] = 0
            tilesState.value = list
            moveCountState.value += 1

            if (isSolved(list, size)) {
                completeCurrentStage()
            }
        }
    }

    private fun isAdjacent(i1: Int, i2: Int, size: Int): Boolean {
        val r1 = i1 / size
        val c1 = i1 % size
        val r2 = i2 / size
        val c2 = i2 % size
        return (kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2)) == 1
    }

    private fun isSolved(list: List<Int>, size: Int): Boolean {
        val total = size * size
        for (i in 0 until total - 1) {
            if (list[i] != i + 1) return false
        }
        return list[total - 1] == 0
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val size by gridSize.collectAsState()
        val tilesList by tiles.collectAsState()
        val moves by moveCount.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Objective Card
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
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${size}x${size} High-Contrast Grid",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Slide numbers into sequential order (1..${size * size - 1})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$moves Moves",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Big High-Contrast Puzzle Grid (Optimized for blurry morning eyes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E293B))
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(size),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(tilesList) { index, value ->
                        PuzzleTileItem(
                            number = value,
                            gridSize = size,
                            onClick = { onTileClicked(index) }
                        )
                    }
                }
            }

            // Reset / Help info
            Text(
                text = "Tap any tile adjacent to the empty dark slot to slide it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun PuzzleTileItem(
    number: Int,
    gridSize: Int,
    onClick: () -> Unit
) {
    if (number == 0) {
        // Blank slot
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
        )
    } else {
        // High contrast vibrant tile colors
        val tileColors = listOf(
            Color(0xFF2563EB), // Blue
            Color(0xFFD97706), // Amber
            Color(0xFF059669), // Emerald
            Color(0xFFDC2626), // Red
            Color(0xFF7C3AED), // Purple
            Color(0xFF0891B2), // Cyan
            Color(0xFFDB2777), // Pink
            Color(0xFF4F46E5), // Indigo
            Color(0xFFEA580C), // Orange
            Color(0xFF16A34A), // Green
            Color(0xFF9333EA), // Violet
            Color(0xFF0284C7), // Sky
            Color(0xFFB91C1C), // Deep Red
            Color(0xFFC026D3), // Fuchsia
            Color(0xFF0D9488)  // Teal
        )
        val color = tileColors[(number - 1) % tileColors.size]

        val fontSize = when (gridSize) {
            2 -> 48.sp // Extra large for 2x2!
            3 -> 32.sp
            else -> 24.sp
        }

        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(color)
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { onClick() }
                .testTag("puzzle_tile_$number"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}
