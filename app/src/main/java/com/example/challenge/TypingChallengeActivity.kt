package com.example.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 *
 * Strict single-screen non-scrollable layout with weight-based structure.
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
        setFeedbackMessage(null)
    }

    override fun onResetToStage1() {
        val selected = TypingChallengeData.pickRandomText(1, null)
        previousPickedText = selected
        targetTextState.value = selected
        userTypedState.value = ""
        setFeedbackMessage(null)
    }

    fun onUserInputChanged(input: String) {
        userTypedState.value = input
        val target = targetTextState.value

        // Exact match check (case-sensitive, spaces, punctuation, numbers)
        if (input == target) {
            setFeedbackMessage("Exact match! Stage Completed!", isSuccess = true)
            completeCurrentStage()
        } else if (input.isNotEmpty() && !target.startsWith(input)) {
            setFeedbackMessage("Mismatch! Check spelling, casing, or spaces.", isError = true)
        } else {
            setFeedbackMessage(null)
        }
    }

    fun onClear() {
        userTypedState.value = ""
        setFeedbackMessage(null)
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
            onClearText = { onClear() },
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

    LaunchedEffect(currentStage) {
        delay(300)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val isExactMatch = typedText == targetText
    val hasMismatch = typedText.isNotEmpty() && !targetText.startsWith(typedText)

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
            val totalHeight = maxHeight
            val totalWidth = maxWidth

            // Dynamic typography scaling based on text length and available space
            val fontSize = when {
                targetText.length > 35 || totalHeight < 280.dp -> 16.sp
                targetText.length > 22 -> 19.sp
                else -> 23.sp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Section 1: Target Text Display Card with Live Highlighting
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExactMatch) Color(0xFF065F46) else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            when {
                                isExactMatch -> Color(0xFF10B981)
                                hasMismatch -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            }
                        )
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TARGET TEXT (TYPE EXACTLY AS SHOWN)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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
                                    isCharMatched -> Color(0xFF10B981)
                                    isCharMismatch -> Color(0xFFEF4444)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                val charBg = if (isCharMismatch) Color(0xFFEF4444).copy(alpha = 0.2f) else Color.Transparent

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = if (char == ' ') 3.dp else 1.dp, vertical = 1.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(charBg)
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (char == ' ') " " else char.toString(),
                                        fontSize = fontSize,
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

                // Section 2: Input Text Field
                OutlinedTextField(
                    value = typedText,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("typing_input_field"),
                    placeholder = {
                        Text(
                            "Type exact sentence here...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    trailingIcon = {
                        if (typedText.isNotEmpty()) {
                            IconButton(onClick = onClearText) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (hasMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (typedText == targetText) onTextChanged(typedText)
                        }
                    )
                )
            }
        }

        // Fixed-Height Bottom Action Row (Never shifts or scrolls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onClearText,
                enabled = typedText.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_typed_text_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear Input", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Text", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
