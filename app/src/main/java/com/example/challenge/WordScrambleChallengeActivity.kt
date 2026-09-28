package com.example.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 3-Stage Progressive Word Scramble Challenge.
 * Unscramble English and Hindi words with large tactile letter tiles.
 * Low: 4 letters, 30s.
 * Moderate: 5-6 letters, 45s.
 * Difficult: 7+ letters, 60s.
 *
 * Strict single-screen non-scrollable layout with weight-based structure.
 */
class WordScrambleChallengeActivity : BaseChallengeActivity() {

    private val targetWordState = MutableStateFlow("")
    val targetWord = targetWordState.asStateFlow()

    private val languageState = MutableStateFlow("English")
    val language = languageState.asStateFlow()

    // Original letters/tiles list
    private val originalTilesState = MutableStateFlow<List<String>>(emptyList())
    val originalTiles = originalTilesState.asStateFlow()

    // Scrambled tiles available to tap: pair of (tile index, character)
    private val scrambledTilesState = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val scrambledTiles = scrambledTilesState.asStateFlow()

    // Indices of tiles that have been selected into the assembled word
    private val selectedTileIndicesState = MutableStateFlow<List<Int>>(emptyList())
    val selectedTileIndices = selectedTileIndicesState.asStateFlow()

    private var previousWord: String? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.WORD_SCRAMBLE

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 30  // Low: 30s
        2 -> 45  // Moderate: 45s
        3 -> 60  // Difficult: 60s
        else -> 30
    }

    override fun onStageStarted(stage: Int) {
        initWord(stage)
    }

    override fun onResetToStage1() {
        initWord(1)
    }

    private fun initWord(stage: Int) {
        val (entry, scrambled) = WordScrambleData.pickScrambledWord(stage, previousWord)
        previousWord = entry.word
        targetWordState.value = entry.word
        languageState.value = entry.language
        originalTilesState.value = entry.letterTiles

        // Scrambled letter tiles paired with a unique key
        scrambledTilesState.value = scrambled.mapIndexed { idx, char -> idx to char }
        selectedTileIndicesState.value = emptyList()
        setFeedbackMessage(null)
    }

    fun onTileTapped(tileIndex: Int) {
        val current = selectedTileIndicesState.value.toMutableList()
        if (current.contains(tileIndex)) return

        current.add(tileIndex)
        selectedTileIndicesState.value = current

        checkWordSolved(current)
    }

    fun onRemoveSelectedTile(selectionIndex: Int) {
        val current = selectedTileIndicesState.value.toMutableList()
        if (selectionIndex in current.indices) {
            current.removeAt(selectionIndex)
            selectedTileIndicesState.value = current
            setFeedbackMessage(null)
        }
    }

    fun onClearAll() {
        selectedTileIndicesState.value = emptyList()
        setFeedbackMessage(null)
    }

    private fun checkWordSolved(selected: List<Int>) {
        val tiles = scrambledTilesState.value
        val assembledString = selected.mapNotNull { selIdx ->
            tiles.firstOrNull { it.first == selIdx }?.second
        }.joinToString("")

        val target = targetWordState.value
        if (assembledString == target) {
            setFeedbackMessage("CORRECT WORD! Stage Passed!", isSuccess = true)
            completeCurrentStage()
        } else if (selected.size == tiles.size) {
            setFeedbackMessage("Incorrect word! Tap a tile to change or tap Clear.", isError = true)
        } else {
            setFeedbackMessage(null)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val target by targetWord.collectAsState()
        val lang by language.collectAsState()
        val scrambled by scrambledTiles.collectAsState()
        val selectedIndices by selectedTileIndices.collectAsState()
        val stage by currentStage.collectAsState()

        WordScrambleScreen(
            targetWord = target,
            language = lang,
            scrambledTiles = scrambled,
            selectedTileIndices = selectedIndices,
            currentStage = stage,
            onTileTapped = { onTileTapped(it) },
            onRemoveTile = { onRemoveSelectedTile(it) },
            onClear = { onClearAll() },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordScrambleScreen(
    targetWord: String,
    language: String,
    scrambledTiles: List<Pair<Int, String>>,
    selectedTileIndices: List<Int>,
    currentStage: Int,
    onTileTapped: (Int) -> Unit,
    onRemoveTile: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val assembledTiles = selectedTileIndices.mapNotNull { selIdx ->
        scrambledTiles.firstOrNull { it.first == selIdx }?.second
    }
    val assembledWord = assembledTiles.joinToString("")
    val isMatch = assembledWord == targetWord

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Main Play Area (weight 1f, strictly non-scrollable)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalWidth = maxWidth
            val totalHeight = maxHeight

            // Dynamically calculate tile sizes based on count
            val letterCount = scrambledTiles.size.coerceAtLeast(4)
            val availableTileW = ((totalWidth - 36.dp) / letterCount).coerceIn(36.dp, 58.dp)
            val availableTileH = (totalHeight * 0.16f).coerceIn(36.dp, 58.dp)
            val dynamicTileSize = minOf(availableTileW, availableTileH)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Subtle language & letter count pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Word in $language • ${scrambledTiles.size} Letters",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Section 1: User's Assembled Word Slot
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "YOUR ASSEMBLED WORD (TAP TO REMOVE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("assembled_word_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isMatch -> Color(0xFF065F46)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                when {
                                    isMatch -> Color(0xFF10B981)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                }
                            )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (assembledTiles.isEmpty()) {
                                Text(
                                    text = "Tap letters below in order",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    assembledTiles.forEachIndexed { index, letter ->
                                        Box(
                                            modifier = Modifier
                                                .padding(3.dp)
                                                .size(dynamicTileSize)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isMatch) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer)
                                                .border(
                                                    1.5.dp,
                                                    if (isMatch) Color.White else MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { onRemoveTile(index) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter,
                                                fontSize = (dynamicTileSize.value * 0.44f).sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isMatch) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Scrambled Letter Tiles
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AVAILABLE TILES (TAP TO SELECT)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center
                    ) {
                        scrambledTiles.forEach { (tileIndex, letter) ->
                            val isUsed = selectedTileIndices.contains(tileIndex)

                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(dynamicTileSize)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isUsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .border(
                                        width = if (isUsed) 1.dp else 2.dp,
                                        color = if (isUsed) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isUsed) { onTileTapped(tileIndex) }
                                    .testTag("scramble_tile_$tileIndex"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    fontSize = (dynamicTileSize.value * 0.44f).sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isUsed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fixed-Height Bottom Action Row (Never shifts or scrolls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = selectedTileIndices.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag("clear_word_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    if (selectedTileIndices.isNotEmpty()) {
                        onRemoveTile(selectedTileIndices.size - 1)
                    }
                },
                enabled = selectedTileIndices.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag("backspace_word_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Backspace, contentDescription = "Backspace", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Backspace", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
