package com.example.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 3-Stage Progressive Typing Task Challenge.
 * Requires exact case-sensitive, punctuation, space, and number matching.
 * Time limits: Low 40s, Moderate 60s, Difficult 90s.
 */
class TypingChallengeActivity : BaseChallengeActivity() {

    private val targetTextState = MutableStateFlow("")
    val targetText = targetTextState.asStateFlow()

    private val userTypedState = MutableStateFlow("")
    val userTyped = userTypedState.asStateFlow()

    private var previousPickedText: String? = null

    override fun getChallengeType(): ChallengeType = ChallengeType.TYPING

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40 // Low: 40 sec
        2 -> 60 // Moderate: 60 sec
        3 -> 90 // Difficult: 90 sec
        else -> 40
    }

    override fun onStageStarted(stage: Int) {
        val selected = TypingChallengeData.pickRandomText(stage, previousPickedText)
        previousPickedText = selected
        targetTextState.value = selected
        userTypedState.value = ""
    }

    override fun onResetToStage1() {
        val selected = TypingChallengeData.pickRandomText(1, null)
        previousPickedText = selected
        targetTextState.value = selected
        userTypedState.value = ""
    }

    fun onUserInputChanged(input: String) {
        userTypedState.value = input
        val target = targetTextState.value

        // Exact match check (case-sensitive, spaces, punctuation, numbers)
        if (input == target) {
            completeCurrentStage()
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val target by targetText.collectAsState()
        val typed by userTyped.collectAsState()
        val stage by currentStage.collectAsState()

        TypingChallengeScreen(
            targetText = target,
            typedText = typed,
            currentStage = stage,
            onTextChanged = { onUserInputChanged(it) },
            onClearText = { userTypedState.value = "" },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypingChallengeScreen(
    targetText: String,
    typedText: String,
    currentStage: Int,
    onTextChanged: (String) -> Unit,
    onClearText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // Request keyboard focus immediately for quick wake-up typing
    LaunchedEffect(currentStage) {
        delay(300)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Determine character match feedback
    val isExactMatch = typedText == targetText
    val hasMismatch = typedText.isNotEmpty() && !targetText.startsWith(typedText)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stage instruction banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Typing Task",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (currentStage) {
                                1 -> "STAGE 1: LOW (2-4 Words)"
                                2 -> "STAGE 2: MODERATE (4-6 Words)"
                                else -> "STAGE 3: DIFFICULT (Punctuation & Numbers)"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Type text exactly as shown (case-sensitive & exact spaces).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large Target Text Display Card with Live Comparison Highlighting
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExactMatch) {
                        Color(0xFF065F46) // Green match container
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isExactMatch) Color(0xFF10B981)
                        else if (hasMismatch) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TARGET TEXT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Highlighted Characters Flow
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center
                    ) {
                        targetText.forEachIndexed { index, char ->
                            val userChar = typedText.getOrNull(index)
                            val isCharMatched = userChar != null && userChar == char
                            val isCharMismatch = userChar != null && userChar != char
                            val charColor = when {
                                isCharMatched -> Color(0xFF10B981) // Crisp Green
                                isCharMismatch -> Color(0xFFEF4444) // Error Red
                                else -> MaterialTheme.colorScheme.onSurface // Untyped
                            }
                            val charBg = if (isCharMismatch) Color(0xFFEF4444).copy(alpha = 0.2f) else Color.Transparent

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = if (char == ' ') 3.dp else 1.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(charBg)
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (char == ' ') " " else char.toString(),
                                    fontSize = 26.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = charColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status & Feedback Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasMismatch) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Mismatch",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Character mismatch! Check case or symbols.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (typedText.isNotEmpty() && !isExactMatch) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Matching",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Matching so far... keep going!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                } else if (isExactMatch) {
                    Text(
                        text = "PERFECT MATCH! Advancing...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF10B981)
                    )
                } else {
                    Text(
                        text = "Type using the on-screen keyboard below",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Progress counter (chars typed / total chars)
                Text(
                    text = "${typedText.length} / ${targetText.length}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sleep-Inertia Friendly Large Text Input Field
            OutlinedTextField(
                value = typedText,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("typing_input_field"),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                placeholder = {
                    Text("Start typing here...", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                },
                trailingIcon = {
                    if (typedText.isNotEmpty()) {
                        IconButton(
                            onClick = onClearText,
                            modifier = Modifier.testTag("clear_typed_text_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Input", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                isError = hasMismatch,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isExactMatch) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // User pressed Done on software keyboard
                        if (typedText == targetText) {
                            onTextChanged(typedText)
                        }
                    }
                ),
                singleLine = false,
                maxLines = 3
            )
        }

        // Action Buttons at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Button(
                onClick = {
                    if (typedText == targetText) {
                        onTextChanged(typedText)
                    }
                },
                enabled = typedText.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_typing_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isExactMatch) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExactMatch) Icons.Default.Check else Icons.Default.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExactMatch) "STAGE CLEARED!" else "Verify Match",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
