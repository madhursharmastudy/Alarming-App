package com.example.challenge

import android.app.Activity
import com.example.data.ChallengeType

/** What a step mainly trains: used for the little step names in the header. */
enum class StepRole(val label: String) {
    BODY("Body"), MIND("Mind"), PLACE("Place"), PROOF("Proof")
}

/** One step of a combo: which task, and how hard (1 = easy, 2 = medium, 3 = hard). */
data class ComboStep(val type: ChallengeType, val difficulty: Int) {
    val role: StepRole
        get() = when (type) {
            ChallengeType.STEPS, ChallengeType.SHAKE, ChallengeType.TURN_WALK,
            ChallengeType.WALK_MATH, ChallengeType.SQUAT -> StepRole.BODY
            ChallengeType.QR, ChallengeType.NFC_TAP, ChallengeType.OBJECT_PHOTO,
            ChallengeType.LIGHT -> StepRole.PLACE
            ChallengeType.FACE_CHECK -> StepRole.PROOF
            else -> StepRole.MIND
        }
}

data class Combo(
    val id: Int,
    val name: String,
    val level: String,
    val force: Int, // 1..5: how strongly it pulls the person out of bed
    val note: String,
    val steps: List<ComboStep>
) {
    val needsQr: Boolean get() = steps.any { it.type == ChallengeType.QR }
    val needsNfc: Boolean get() = steps.any { it.type == ChallengeType.NFC_TAP }
    val needsObject: Boolean get() = steps.any { it.type == ChallengeType.OBJECT_PHOTO }
}

object ComboCatalog {
    private fun s(type: ChallengeType, difficulty: Int) = ComboStep(type, difficulty)

    val all: List<Combo> = listOf(
        Combo(1, "Gentle Start", "Easy", 2, "Light, no camera",
            listOf(s(ChallengeType.STEPS, 1), s(ChallengeType.MATH, 1), s(ChallengeType.PATTERN_LOCK, 1))),
        Combo(2, "Walk & Think", "Easy", 2, "Walk, type, jigsaw",
            listOf(s(ChallengeType.STEPS, 1), s(ChallengeType.TYPING, 1), s(ChallengeType.IMAGE_ARRANGEMENT, 1))),
        Combo(3, "Walk & Scan", "Normal", 3, "Needs a QR in another room",
            listOf(s(ChallengeType.STEPS, 2), s(ChallengeType.PATTERN_LOCK, 2), s(ChallengeType.QR, 3))),
        Combo(4, "Shake, Math, QR", "Normal", 3, "The old Force Mode",
            listOf(s(ChallengeType.SHAKE, 1), s(ChallengeType.MATH, 2), s(ChallengeType.QR, 3))),
        Combo(5, "Walk the Sum", "Normal", 4, "Sum, turns, then QR",
            listOf(s(ChallengeType.WALK_MATH, 2), s(ChallengeType.TURN_WALK, 2), s(ChallengeType.QR, 3))),
        Combo(6, "Proof of Waking", "Hard", 5, "Walk, QR, then face check",
            listOf(s(ChallengeType.WALK_MATH, 3), s(ChallengeType.QR, 3), s(ChallengeType.FACE_CHECK, 3))),
        Combo(7, "Body Builder", "Hard", 5, "Squats, speak, QR",
            listOf(s(ChallengeType.SQUAT, 2), s(ChallengeType.VOICE_MATH, 2), s(ChallengeType.QR, 3))),
        Combo(8, "No Escape", "Extreme", 5, "Needs NFC tag and an object",
            listOf(
                s(ChallengeType.TURN_WALK, 3), s(ChallengeType.OBJECT_PHOTO, 3),
                s(ChallengeType.NFC_TAP, 3), s(ChallengeType.FACE_CHECK, 3)
            )),
        Combo(9, "Easy on the Eyes", "Special", 3, "Little screen time",
            listOf(s(ChallengeType.STEPS, 2), s(ChallengeType.VOICE_MATH, 1), s(ChallengeType.LIGHT, 2))),
        Combo(10, "Small Room", "Special", 4, "Camera, little walking",
            listOf(s(ChallengeType.SQUAT, 2), s(ChallengeType.MATH, 2), s(ChallengeType.FACE_CHECK, 2))),
        Combo(11, "No Camera", "Special", 2, "Works without camera",
            listOf(s(ChallengeType.STEPS, 1), s(ChallengeType.MATH, 2), s(ChallengeType.PATTERN_LOCK, 2))),
        Combo(12, "Quiet Room", "Special", 3, "No sound needed",
            listOf(s(ChallengeType.STEPS, 2), s(ChallengeType.PATTERN_LOCK, 2), s(ChallengeType.QR, 3)))
    )

    fun byId(id: Int): Combo? = all.firstOrNull { it.id == id }

    /** The screen (activity) that runs a given task. */
    fun activityClass(type: ChallengeType): Class<out Activity> = when (type) {
        ChallengeType.TYPING -> TypingChallengeActivity::class.java
        ChallengeType.IMAGE_ARRANGEMENT -> ImageArrangementChallengeActivity::class.java
        ChallengeType.CHARGER -> ChargerChallengeActivity::class.java
        ChallengeType.CARD_ARRANGEMENT -> CardArrangementChallengeActivity::class.java
        ChallengeType.PATTERN_LOCK -> PatternLockChallengeActivity::class.java
        ChallengeType.ODD_ONE_OUT -> OddOneOutChallengeActivity::class.java
        ChallengeType.REACTION_GAME -> ReactionGameChallengeActivity::class.java
        ChallengeType.MATH -> MathChallengeActivity::class.java
        ChallengeType.SHAKE -> ShakeChallengeActivity::class.java
        ChallengeType.STEPS -> StepsChallengeActivity::class.java
        ChallengeType.QR -> QrChallengeActivity::class.java
        ChallengeType.WALK_MATH -> WalkMathChallengeActivity::class.java
        ChallengeType.TURN_WALK -> TurnWalkChallengeActivity::class.java
        ChallengeType.FACE_CHECK -> MathChallengeActivity::class.java // replaced below in a later step
        ChallengeType.SQUAT -> MathChallengeActivity::class.java
        ChallengeType.VOICE_MATH -> MathChallengeActivity::class.java
        ChallengeType.LIGHT -> MathChallengeActivity::class.java
        ChallengeType.OBJECT_PHOTO -> MathChallengeActivity::class.java
        ChallengeType.NFC_TAP -> QrChallengeActivity::class.java
        ChallengeType.COMBO -> MathChallengeActivity::class.java // never used directly
    }
}
