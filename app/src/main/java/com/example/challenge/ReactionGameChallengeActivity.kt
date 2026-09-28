package com.example.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 3-Stage Progressive Reaction Game Challenge.
 * A moving target travels across the track, and user must tap when inside the highlighted hit zone.
 * Low: slow speed, big hit zone, 3 hits needed, 40s limit.
 * Moderate: faster speed, medium zone, 5 hits needed, 60s limit.
 * Difficult: fast speed, small zone, 7 hits needed, 90s limit.
 * Missed tap resets hit count for the current stage without sounding the alarm.
 */
class ReactionGameChallengeActivity : BaseChallengeActivity() {

    private val targetHitsState = MutableStateFlow(3)
    val targetHits = targetHitsState.asStateFlow()

    private val currentHitsState = MutableStateFlow(0)
    val currentHits = currentHitsState.asStateFlow()

    private val speedDurationMsState = MutableStateFlow(1800)
    val speedDurationMs = speedDurationMsState.asStateFlow()

    private val hitZoneWidthState = MutableStateFlow(0.32f) // Width percentage (0.0 to 1.0)
    val hitZoneWidth = hitZoneWidthState.asStateFlow()

    private val lastFeedbackState = MutableStateFlow<String?>(null)
    val lastFeedback = lastFeedbackState.asStateFlow()

    private val isLastHitSuccessState = MutableStateFlow(true)
    val isLastHitSuccess = isLastHitSuccessState.asStateFlow()

    override fun getChallengeType(): ChallengeType = ChallengeType.REACTION_GAME

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40  // Low: 40s
        2 -> 60  // Moderate: 60s
        3 -> 90  // Difficult: 90s
        else -> 40
    }

    override fun onStageStarted(stage: Int) {
        val (neededHits, duration, zoneWidth) = when (stage) {
            1 -> Triple(3, 1800, 0.32f) // Slow target, big zone, 3 hits
            2 -> Triple(5, 1200, 0.22f) // Faster, medium zone, 5 hits
            3 -> Triple(7, 750, 0.14f)  // Fast, small zone, 7 hits
            else -> Triple(3, 1800, 0.32f)
        }

        targetHitsState.value = neededHits
        speedDurationMsState.value = duration
        hitZoneWidthState.value = zoneWidth
        currentHitsState.value = 0
        lastFeedbackState.value = null
    }

    override fun onResetToStage1() {
        targetHitsState.value = 3
        speedDurationMsState.value = 1800
        hitZoneWidthState.value = 0.32f
        currentHitsState.value = 0
        lastFeedbackState.value = null
    }

    fun onUserTap(targetProgress: Float) {
        val zoneW = hitZoneWidthState.value
        val zoneStart = 0.5f - (zoneW / 2f)
        val zoneEnd = 0.5f + (zoneW / 2f)

        val isHit = targetProgress in zoneStart..zoneEnd

        if (isHit) {
            val nextHits = currentHitsState.value + 1
            currentHitsState.value = nextHits
            isLastHitSuccessState.value = true
            lastFeedbackState.value = "PERFECT HIT! ($nextHits/${targetHitsState.value})"

            if (nextHits >= targetHitsState.value) {
                completeCurrentStage()
            }
        } else {
            // Missed! Reset count for current stage (no alarm re-ring)
            currentHitsState.value = 0
            isLastHitSuccessState.value = false
            lastFeedbackState.value = "MISSED! Hit count reset to 0. Try again!"
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val hits by currentHits.collectAsState()
        val needed by targetHits.collectAsState()
        val duration by speedDurationMs.collectAsState()
        val zoneW by hitZoneWidth.collectAsState()
        val feedback by lastFeedback.collectAsState()
        val isSuccess by isLastHitSuccess.collectAsState()
        val stage by currentStage.collectAsState()

        ReactionGameScreen(
            currentHits = hits,
            targetHits = needed,
            durationMs = duration,
            hitZoneWidth = zoneW,
            feedback = feedback,
            isSuccess = isSuccess,
            currentStage = stage,
            onTapAction = { onUserTap(it) },
            modifier = modifier
        )
    }
}

@Composable
fun ReactionGameScreen(
    currentHits: Int,
    targetHits: Int,
    durationMs: Int,
    hitZoneWidth: Float,
    feedback: String?,
    isSuccess: Boolean,
    currentStage: Int,
    onTapAction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "targetMotion")
    val targetPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "targetPosition"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Stage Header & Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Stage $currentStage: $targetHits Consecutive Hits",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (currentStage) {
                                    1 -> "Slow speed • Wide hit zone"
                                    2 -> "Medium speed • Normal hit zone"
                                    else -> "Fast speed • Narrow hit zone"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$currentHits / $targetHits HITS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feedback Banner
            AnimatedVisibility(visible = feedback != null) {
                feedback?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess) Color(0xFF065F46) else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) Color(0xFFE6FFFA) else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Reaction Track Canvas Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("reaction_track_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TARGET HIT ZONE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF10B981),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // The Running Bar Canvas
                ReactionTrackCanvas(
                    targetProgress = targetPosition,
                    hitZoneWidth = hitZoneWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Tap when the glowing target enters the green zone!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Giant Tactile Action Button for Sleep-Inertia
        Button(
            onClick = { onTapAction(targetPosition) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .testTag("tap_reaction_button"),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TAP NOW!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ReactionTrackCanvas(
    targetProgress: Float,
    hitZoneWidth: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val trackHeight = 24.dp.toPx()
        val trackTop = (h - trackHeight) / 2f

        // 1. Background Track
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(0f, trackTop),
            size = Size(w, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // 2. Highlighted Green Hit Zone in Center
        val zoneStartPx = w * (0.5f - (hitZoneWidth / 2f))
        val zoneWidthPx = w * hitZoneWidth

        drawRoundRect(
            color = Color(0xFF10B981).copy(alpha = 0.35f),
            topLeft = Offset(zoneStartPx, trackTop - 6.dp.toPx()),
            size = Size(zoneWidthPx, trackHeight + 12.dp.toPx()),
            cornerRadius = CornerRadius(10.dp.toPx())
        )

        // Hit Zone Borders / Guides
        drawRoundRect(
            color = Color(0xFF10B981),
            topLeft = Offset(zoneStartPx, trackTop - 6.dp.toPx()),
            size = Size(zoneWidthPx, trackHeight + 12.dp.toPx()),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
        )

        // Center tick guide
        drawLine(
            color = Color(0xFF34D399),
            start = Offset(w * 0.5f, trackTop - 10.dp.toPx()),
            end = Offset(w * 0.5f, trackTop + trackHeight + 10.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )

        // 3. Moving Target Circle
        val targetX = w * targetProgress
        val targetY = h / 2f
        val targetRadius = 18.dp.toPx()

        // Outer glow
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.4f),
            radius = targetRadius * 1.5f,
            center = Offset(targetX, targetY)
        )

        // Core target
        drawCircle(
            color = Color(0xFF0284C7),
            radius = targetRadius,
            center = Offset(targetX, targetY)
        )

        // Target highlight center
        drawCircle(
            color = Color.White,
            radius = targetRadius * 0.45f,
            center = Offset(targetX, targetY)
        )
    }
}
