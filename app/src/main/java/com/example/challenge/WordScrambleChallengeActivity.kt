package com.example.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        }
    }

    fun onClearAll() {
        selectedTileIndicesState.value = emptyList()
    }

    private fun checkWordSolved(selected: List<Int>) {
        val tiles = scrambledTilesState.value
        val assembledString = selected.mapNotNull { selIdx ->
            tiles.firstOrNull { it.first == selIdx }?.second
        }.joinToString("")

        val target = targetWordState.value
        if (assembledString == target) {
            completeCurrentStage()
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
    val scrollState = rememberScrollState()

    // Formed word string
    val assembledTiles = selectedTileIndices.mapNotNull { selIdx ->
        scrambledTiles.firstOrNull { it.first == selIdx }?.second
    }
    val assembledWord = assembledTiles.joinToString("")
    val isCompleteLength = assembledTiles.size == scrambledTiles.size
    val isMatch = assembledWord == targetWord
    val isWrongComplete = isCompleteLength && !isMatch

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stage and Language Info Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stage $currentStage • ${scrambledTiles.size} Letters",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = language,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Assembled Word Box (Current User Construction)
            Text(
                text = "YOUR ASSEMBLED WORD (TAP TO REMOVE)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assembled_word_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isMatch -> Color(0xFF065F46)
                        isWrongComplete -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        when {
                            isMatch -> Color(0xFF10B981)
                            isWrongComplete -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        }
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (assembledTiles.isEmpty()) {
                        Text(
                            text = "Tap letters below in order",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.Center
                        ) {
                            assembledTiles.forEachIndexed { index, letter ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isMatch) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer)
                                        .border(
                                            2.dp,
                                            if (isMatch) Color.White else MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onRemoveTile(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isMatch) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Live verification feedback
                    if (isWrongComplete) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Incorrect word! Tap letters to change or tap Clear.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isMatch) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "CORRECT WORD! Advancing...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scrambled Letter Tiles to Choose From
            Text(
                text = "AVAILABLE TILES (TAP TO SELECT)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center
            ) {
                scrambledTiles.forEach { (tileIndex, letter) ->
                    val isUsed = selectedTileIndices.contains(tileIndex)

                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isUsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.primary
                            )
                            .border(
                                width = if (isUsed) 1.dp else 2.dp,
                                color = if (isUsed) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = !isUsed) { onTileTapped(tileIndex) }
                            .testTag("scramble_tile_$tileIndex"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isUsed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else Color.White
                        )
                    }
                }
            }
        }

        // Action Buttons (Clear & Backspace)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = selectedTileIndices.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("clear_word_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear", fontWeight = FontWeight.Bold)
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
                    .height(52.dp)
                    .testTag("backspace_word_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Backspace", fontWeight = FontWeight.Bold)
            }
        }
    }
}
