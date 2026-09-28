package com.example.challenge

import kotlin.random.Random

/**
 * Word Bank for Word Scramble Challenge.
 * Contains both English and Hindi words for each difficulty level (at least 20 words per level).
 * Low: 4-letter words
 * Moderate: 5-6 letters
 * Difficult: 7+ letters
 */
object WordScrambleData {

    data class WordEntry(
        val word: String,
        val language: String,
        // Individual grapheme / character tiles for clean assembly
        val letterTiles: List<String>
    )

    // Helper to build an English word entry
    private fun en(w: String) = WordEntry(
        word = w.uppercase(),
        language = "English",
        letterTiles = w.uppercase().map { it.toString() }
    )

    // Helper to build a Hindi word entry (each unit is a syllable / akshara / character)
    private fun hi(w: String, tiles: List<String>) = WordEntry(
        word = w,
        language = "हिंदी (Hindi)",
        letterTiles = tiles
    )

    // STAGE 1 (LOW): 4-letter words (12 English + 12 Hindi = 24 entries)
    val LOW_WORDS: List<WordEntry> = listOf(
        // English (4 letters)
        en("WAKE"),
        en("RISE"),
        en("DAWN"),
        en("TIME"),
        en("MIND"),
        en("CALM"),
        en("BOLD"),
        en("FAST"),
        en("LEAP"),
        en("MOVE"),
        en("GLOW"),
        en("STEP"),
        // Hindi (4 character units)
        hi("कमल", listOf("क", "म", "ल")),
        hi("समय", listOf("स", "म", "य")),
        hi("नमक", listOf("न", "म", "क")),
        hi("सड़क", listOf("स", "ड़", "क")),
        hi("शहर", listOf("श", "ह", "र")),
        hi("पवन", listOf("प", "व", "न")),
        hi("गगन", listOf("ग", "ग", "न")),
        hi("कलम", listOf("क", "ल", "म")),
        hi("भवन", listOf("भ", "व", "न")),
        hi("शहद", listOf("श", "ह", "द")),
        hi("अमर", listOf("अ", "म", "र")),
        hi("नहर", listOf("न", "ह", "र"))
    )

    // STAGE 2 (MODERATE): 5-6 letters (12 English + 12 Hindi = 24 entries)
    val MODERATE_WORDS: List<WordEntry> = listOf(
        // English (5-6 letters)
        en("ALERT"),
        en("FOCUS"),
        en("SHINE"),
        en("ENERGY"),
        en("ACTIVE"),
        en("BRIGHT"),
        en("ACTION"),
        en("STRONG"),
        en("SPRING"),
        en("SILENT"),
        en("REVIVE"),
        en("BREATH"),
        // Hindi (4-5 units)
        hi("सुबह", listOf("सु", "ब", "ह")),
        hi("जागो", listOf("जा", "गो")),
        hi("सूरज", listOf("सू", "र", "ज")),
        hi("किरण", listOf("कि", "र", "ण")),
        hi("रोशनी", listOf("रो", "श", "नी")),
        hi("ताजगी", listOf("ता", "ज", "गी")),
        hi("शक्ति", listOf("श", "क्", "ति")),
        hi("हिम्मत", listOf("हि", "म्", "म", "त")),
        hi("मेहनत", listOf("मे", "ह", "न", "त")),
        hi("सफलता", listOf("स", "फ", "ल", "ता")),
        hi("उमंग", listOf("उ", "मं", "ग")),
        hi("उजाला", listOf("उ", "जा", "ला"))
    )

    // STAGE 3 (DIFFICULT): 7+ letters (12 English + 12 Hindi = 24 entries)
    val DIFFICULT_WORDS: List<WordEntry> = listOf(
        // English (7+ letters)
        en("AWAKENED"),
        en("SUNSHINE"),
        en("DAYBREAK"),
        en("VICTORY"),
        en("WARRIOR"),
        en("RESOLVE"),
        en("TRIUMPH"),
        en("CHAMPION"),
        en("STRENGTH"),
        en("BRILLIANT"),
        en("PROGRESS"),
        en("CONQUER"),
        // Hindi (multi-syllable wake-up and positive words)
        hi("आत्मविश्वास", listOf("आ", "त्म", "वि", "श्वा", "स")),
        hi("अनुशासन", listOf("अ", "नु", "शा", "स", "न")),
        hi("जागरूकता", listOf("जा", "ग", "रू", "क", "ता")),
        hi("दृढ़निश्चय", listOf("दृ", "ढ़", "नि", "श्च", "य")),
        hi("कर्मयोगी", listOf("क", "र्म", "यो", "गी")),
        hi("सकारात्मक", listOf("स", "का", "रा", "त्म", "क")),
        hi("प्रातःकाल", listOf("प्रा", "तः", "का", "ल")),
        hi("प्रसन्नता", listOf("प्र", "स", "न्न", "ता")),
        hi("ऊर्जावान", listOf("ऊ", "र्जा", "वा", "न")),
        hi("विजयीभव", listOf("वि", "ज", "यी", "भ", "व")),
        hi("स्वावलंबन", listOf("स्वा", "व", "लं", "ब", "न")),
        hi("शुभप्रभात", listOf("शु", "भ", "प्र", "भा", "त"))
    )

    /**
     * Picks a random word entry for the given stage and returns it with a scrambled letter order.
     * Guaranteed that the scrambled order never equals the original order.
     */
    fun pickScrambledWord(stage: Int, previousWord: String? = null): Pair<WordEntry, List<String>> {
        val pool = when (stage) {
            1 -> LOW_WORDS
            2 -> MODERATE_WORDS
            else -> DIFFICULT_WORDS
        }

        val candidates = if (previousWord != null && pool.size > 1) {
            pool.filter { it.word != previousWord }
        } else {
            pool
        }
        val entry = candidates.random()

        val originalTiles = entry.letterTiles
        val shuffledTiles = originalTiles.toMutableList()

        if (shuffledTiles.size > 1) {
            // Keep shuffling until order is strictly different from original
            var attempts = 0
            do {
                shuffledTiles.shuffle(Random(System.currentTimeMillis() + Random.nextLong()))
                attempts++
            } while (shuffledTiles == originalTiles && attempts < 20)

            // If by chance still equal (e.g. repeated letters), swap first two
            if (shuffledTiles == originalTiles && shuffledTiles.size >= 2) {
                val temp = shuffledTiles[0]
                shuffledTiles[0] = shuffledTiles[1]
                shuffledTiles[1] = temp
            }
        }

        return entry to shuffledTiles
    }
}
