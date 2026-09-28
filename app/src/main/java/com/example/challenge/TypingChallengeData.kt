package com.example.challenge

import kotlin.random.Random

/**
 * Built-in lists of typing challenge texts for ForceAlarm.
 * Separated by difficulty level with at least 15 texts per level.
 * Simple Kotlin file so it can be easily edited or expanded.
 */
object TypingChallengeData {

    // Stage 1 (Low): 2-4 words per prompt (at least 15 entries)
    val LOW_STAGE_TEXTS: List<String> = listOf(
        "Wake up now",
        "Rise and shine",
        "Good morning sun",
        "Time to thrive",
        "Start fresh today",
        "Seize the day",
        "Eyes wide open",
        "Focus your mind",
        "Ready for action",
        "Move with purpose",
        "Greet the dawn",
        "Energy flows now",
        "Step out today",
        "Clear your head",
        "Jump into life",
        "Embrace this day"
    )

    // Stage 2 (Moderate): 4-6 words per prompt (at least 15 entries)
    val MODERATE_STAGE_TEXTS: List<String> = listOf(
        "The early bird catches sunrise",
        "Every new morning brings opportunity",
        "Awake alert and feeling energized",
        "Leave the warm bed behind",
        "Consistency creates long term success",
        "Today is full of promise",
        "Stand tall and breathe deeply",
        "Take control of your morning",
        "Your future starts right here",
        "Push past morning sleep inertia",
        "Make today an absolute masterpiece",
        "Discipline today equals freedom tomorrow",
        "Drink cold water and move",
        "Conquer the day with confidence",
        "Sharp focus and calm breathing",
        "Step forward with steady resolve"
    )

    // Stage 3 (Difficult): 6-10 words, including capital letters, punctuation and numbers (at least 15 entries)
    val DIFFICULT_STAGE_TEXTS: List<String> = listOf(
        "Alarm 07:30 AM: Mind awake, body ready to conquer 100%!",
        "Rule #1: Wake up, drink 2 glasses of H2O & smile!",
        "Day 45: Focus + Energy = 24 hours of great achievement.",
        "Stop sleeping! It is 06:45 AM, time to reach Level 10.",
        "Success requires 100% effort: No snooze, No delays, Just action!",
        "Start Goal #3: Drink 500ml water, stretch & run 5 km!",
        "Focus: 10 deep breaths, 0 excuses, and 100% determination.",
        "Mission 2026: Wake up at 06:00, win the day early!",
        "Power up: Battery at 100%, 7 key tasks waiting today!",
        "Action item #4: Jump up, wash face, drink coffee & conquer!",
        "Discipline Check: It's 07:00 AM; clear 3 goals before noon!",
        "Today's Score: 10/10 wake-up speed; 0 snooze taps allowed!",
        "Victory starts NOW: 5 minutes of stretching, 0 regrets!",
        "Code 99-AWAKE: Break sleep inertia, seize 8 productive hours!",
        "Step 1: Get out of bed; Step 2: Conquer 3 priorities!",
        "Alert: Time = 06:15 AM! Don't let 1 excuse stop you."
    )

    /**
     * Fully random selection every time:
     * - Sometimes picks a fresh text
     * - Sometimes reuses an earlier text
     * - Sometimes applies a variation in arrangement (e.g. alternate case or order for Low/Moderate)
     * The resulting string is what's displayed, and exact match is required.
     */
    fun pickRandomText(stage: Int, previousText: String? = null): String {
        val baseList = when (stage) {
            1 -> LOW_STAGE_TEXTS
            2 -> MODERATE_STAGE_TEXTS
            else -> DIFFICULT_STAGE_TEXTS
        }

        // Selection is fully random: sometimes fresh, sometimes reuse
        val reuseRoll = Random.nextFloat()
        val raw = if (previousText != null && reuseRoll < 0.25f && baseList.contains(previousText)) {
            previousText
        } else {
            val candidates = if (previousText != null && baseList.size > 1) {
                baseList.filter { it != previousText }
            } else {
                baseList
            }
            candidates.random()
        }

        // Random arrangement variation (15% chance to vary casing or layout while keeping exact match)
        val variationRoll = Random.nextFloat()
        return when {
            // Difficult stage already has strict casing & punctuation, preserve as is
            stage == 3 -> raw
            // For stage 1 or 2, occasionally uppercase or capitalize words
            variationRoll < 0.15f -> raw.uppercase()
            variationRoll < 0.30f -> raw.split(" ").reversed().joinToString(" ")
            else -> raw
        }
    }
}
