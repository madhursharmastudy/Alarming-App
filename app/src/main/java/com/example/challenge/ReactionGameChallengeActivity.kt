package com.example.challenge

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive

/**
 * 3-Stage Progressive Reaction Game Challenge.
 * Low: 3 hits needed, slower speed (1800ms per pass), wider zone (32%), 40s.
 * Moderate: 5 hits needed, medium speed (1200ms per pass), medium zone (22%), 60s.
 * Difficult: 7 hits needed, fast speed (800ms per pass), narrow zone (16%), 90s.
 *
 * Real-time continuous clock synchronization guarantees 100% accurate hit detection at the moment of tap.
 */
class ReactionGameChallengeActivity : BaseChallengeActivity() {

    private val targetHitsState = MutableStateFlow(3)
    val targetHits = targetHitsState.asStateFlow()

    private val currentHitsState = MutableStateFlow(0)
    val currentHits = currentHitsState.asStateFlow()

    private val speedDurationMsState = MutableStateFlow(1800)
    val speedDurationMs = speedDurationMsState.asStateFlow()

    private val hitZoneWidthState = MutableStateFlow(0.32f)
    val hitZoneWidth = hitZoneWidthState.asStateFlow()

    private var animationStartTimeMs = SystemClock.uptimeMillis()

    override fun getChallengeType(): ChallengeType = ChallengeType.REACTION_GAME

    override fun getStageTimeLimit(stage: Int): Int = when (stage) {
        1 -> 40  // Low: 40s
        2 -> 60  // Moderate: 60s
        3 -> 90  // Difficult: 90s
        else -> 40
    }

    override fun onStageStarted(stage: Int) {
        val (neededHits, duration, zoneWidth) = when (stage) {
            1 -> Triple(3, 1800, 0.32f) // Low: 3 hits
            2 -> Triple(5, 1200, 0.22f) // Moderate: 5 hits
            3 -> Triple(7, 800, 0.16f)  // Difficult: 7 hits
            else -> Triple(3, 1800, 0.32f)
        }

        targetHitsState.value = neededHits
        speedDurationMsState.value = duration
        hitZoneWidthState.value = zoneWidth
        currentHitsState.value = 0
        animationStartTimeMs = SystemClock.uptimeMillis()
        setFeedbackMessage(null)
    }

    override fun onResetToStage1() {
        targetHitsState.value = 3
        speedDurationMsState.value = 1800
        hitZoneWidthState.value = 0.32f
        currentHitsState.value = 0
        animationStartTimeMs = SystemClock.uptimeMillis()
        setFeedbackMessage(null)
    }

    fun onUserTap(targetProgress: Float) {
        val zoneW = hitZoneWidthState.value
        val zoneStart = 0.5f - (zoneW / 2f)
        val zoneEnd = 0.5f + (zoneW / 2f)
        val tolerance = 0.05f // Includes circle radius allowance

        val isHit = targetProgress in (zoneStart - tolerance)..(zoneEnd + tolerance)

        if (isHit) {
            val nextHits = currentHitsState.value + 1
            currentHitsState.value = nextHits

            if (nextHits >= targetHitsState.value) {
                setFeedbackMessage("TARGET HIT! ($nextHits/${targetHitsState.value}) - Stage Completed!", isSuccess = true)
                completeCurrentStage()
            } else {
                setFeedbackMessage("TARGET HIT! ($nextHits/${targetHitsState.value})", isSuccess = true)
            }
        } else {
            currentHitsState.value = 0
            setFeedbackMessage("MISSED! Hit count reset to 0. Try again!", isError = true)
        }
    }

    @Composable
    override fun ChallengeContent(modifier: Modifier) {
        val hits by currentHits.collectAsState()
        val needed by targetHits.collectAsState()
        val duration by speedDurationMs.collectAsState()
        val zoneW by hitZoneWidth.collectAsState()
        val stage by currentStage.collectAsState()

        ReactionGameScreen(
            currentHits = hits,
            targetHits = needed,
            durationMs = duration,
            hitZoneWidth = zoneW,
            startTimeMs = animationStartTimeMs,
            currentStage = stage,
            onTapAction = { pos -> onUserTap(pos) },
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
    startTimeMs: Long,
    currentStage: Int,
    onTapAction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Produce continuous frame-accurate position synchronized with clock
    val targetPosition by produceState(initialValue = 0.5f, key1 = durationMs, key2 = startTimeMs) {
        while (isActive) {
            withFrameMillis { now ->
                val cycleMs = durationMs * 2
                val phase = (now - startTimeMs) % cycleMs
                val rawProgress = if (phase < durationMs) {
                    phase.toFloat() / durationMs.toFloat()
                } else {
                    2f - (phase.toFloat() / durationMs.toFloat())
                }
                value = 0.05f + rawProgress * 0.90f
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hits Progress Counter Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target: $currentHits of $targetHits Consecutive Hits",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Main Track Area (weight 1f, strictly non-scrollable)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val totalHeight = maxHeight
            val cardPadding = if (totalHeight < 240.dp) 10.dp else 16.dp
            val trackHeight = if (totalHeight < 240.dp) 60.dp else 76.dp

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .testTag("reaction_track_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "TARGET HIT ZONE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // The Running Bar Canvas
                    ReactionTrackCanvas(
                        targetProgress = targetPosition,
                        hitZoneWidth = hitZoneWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Tap button below when circle enters the center hit zone!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Tactile Action Button (Fixed height, strictly 2-color themed)
        Button(
            onClick = { onTapAction(targetPosition) },
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .testTag("tap_reaction_button"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TAP NOW!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val trackHeight = 22.dp.toPx()
        val trackTop = (h - trackHeight) / 2f

        // 1. Background Track
        drawRoundRect(
            color = trackBgColor,
            topLeft = Offset(0f, trackTop),
            size = Size(w, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // 2. Highlighted Hit Zone in Center (Green Target Zone)
        val zoneStartPx = w * (0.5f - (hitZoneWidth / 2f))
        val zoneWidthPx = w * hitZoneWidth
        val hitZoneGreenBg = Color(0xFF2E7D32).copy(alpha = 0.35f)
        val hitZoneGreenBorder = Color(0xFF4CAF50)

        drawRoundRect(
            color = hitZoneGreenBg,
            topLeft = Offset(zoneStartPx, trackTop - 6.dp.toPx()),
            size = Size(zoneWidthPx, trackHeight + 12.dp.toPx()),
            cornerRadius = CornerRadius(10.dp.toPx())
        )

        // Hit Zone Borders / Guides
        drawRoundRect(
            color = hitZoneGreenBorder,
            topLeft = Offset(zoneStartPx, trackTop - 6.dp.toPx()),
            size = Size(zoneWidthPx, trackHeight + 12.dp.toPx()),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
        )

        // Center tick guide
        drawLine(
            color = hitZoneGreenBorder,
            start = Offset(w * 0.5f, trackTop - 10.dp.toPx()),
            end = Offset(w * 0.5f, trackTop + trackHeight + 10.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )

        // 3. Moving Target Circle
        val targetX = w * targetProgress
        val targetY = h / 2f
        val targetRadius = 15.dp.toPx()

        // Outer glow
        drawCircle(
            color = primaryColor.copy(alpha = 0.35f),
            radius = targetRadius * 1.4f,
            center = Offset(targetX, targetY)
        )

        // Core target
        drawCircle(
            color = primaryColor,
            radius = targetRadius,
            center = Offset(targetX, targetY)
        )

        // Target center dot
        drawCircle(
            color = Color.White,
            radius = targetRadius * 0.40f,
            center = Offset(targetX, targetY)
        )
    }
}
