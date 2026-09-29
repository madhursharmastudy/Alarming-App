package com.example.challenge

import androidx.compose.ui.graphics.Color

enum class CardSuit(val symbol: String, val displayName: String, val color: Color) {
    SPADES("♠", "Spades", Color(0xFF111111)),
    HEARTS("♥", "Hearts", Color(0xFFC62828)),
    DIAMONDS("♦", "Diamonds", Color(0xFFC62828)),
    CLUBS("♣", "Clubs", Color(0xFF111111))
}

data class CardRank(val label: String, val value: Int)

data class PlayingCard(
    val id: Int,
    val suit: CardSuit,
    val rank: CardRank
)

object CardArrangementData {

    val ALL_RANKS = listOf(
        CardRank("A", 1),
        CardRank("2", 2),
        CardRank("3", 3),
        CardRank("4", 4),
        CardRank("5", 5),
        CardRank("6", 6),
        CardRank("7", 7),
        CardRank("8", 8),
        CardRank("9", 9),
        CardRank("10", 10),
        CardRank("J", 11),
        CardRank("Q", 12),
        CardRank("K", 13)
    )

    val SUIT_ROWS = listOf(CardSuit.SPADES, CardSuit.HEARTS, CardSuit.DIAMONDS, CardSuit.CLUBS)

    data class DealResult(
        val cardsPerSuit: Int,
        val ranksUsed: List<CardRank>,
        val initialGrid: List<List<PlayingCard>>, // 4 rows
        val orderMatters: Boolean
    )

    fun dealStage(stage: Int): DealResult {
        val (cardsPerSuit, orderMatters) = when (stage) {
            1 -> Pair(3, false) // Low: 3 cards per suit, order doesn't matter
            2 -> Pair(5, true)  // Moderate: 5 cards per suit, ascending order
            3 -> Pair(7, true)  // Difficult: 7 cards per suit, ascending order
            else -> Pair(3, false)
        }

        // Pick distinct ranks randomly
        val selectedRanks = ALL_RANKS.shuffled().take(cardsPerSuit).sortedBy { it.value }

        // Generate full deck for these ranks
        val allCards = mutableListOf<PlayingCard>()
        var cardId = 0
        for (suit in SUIT_ROWS) {
            for (rank in selectedRanks) {
                allCards.add(PlayingCard(id = cardId++, suit = suit, rank = rank))
            }
        }

        // Shuffle all cards until not solved
        var shuffled: List<PlayingCard>
        var grid: List<List<PlayingCard>>
        do {
            shuffled = allCards.shuffled()
            grid = (0 until 4).map { r ->
                shuffled.subList(r * cardsPerSuit, (r + 1) * cardsPerSuit)
            }
        } while (isStageSolved(grid, cardsPerSuit, orderMatters))

        return DealResult(
            cardsPerSuit = cardsPerSuit,
            ranksUsed = selectedRanks,
            initialGrid = grid,
            orderMatters = orderMatters
        )
    }

    fun isStageSolved(grid: List<List<PlayingCard>>, cardsPerSuit: Int, orderMatters: Boolean): Boolean {
        if (grid.size != 4) return false
        for (r in 0 until 4) {
            val targetSuit = SUIT_ROWS[r]
            val row = grid[r]
            if (row.size != cardsPerSuit) return false

            // Check suit
            if (row.any { it.suit != targetSuit }) return false

            // Check order if required
            if (orderMatters) {
                for (c in 0 until row.size - 1) {
                    if (row[c].rank.value >= row[c + 1].rank.value) return false
                }
            }
        }
        return true
    }

    fun countCorrectCards(grid: List<List<PlayingCard>>, orderMatters: Boolean, expectedRanks: List<CardRank>): Int {
        var correct = 0
        for (r in 0 until minOf(4, grid.size)) {
            val targetSuit = SUIT_ROWS[r]
            val row = grid[r]
            for (c in row.indices) {
                val card = row[c]
                val suitCorrect = card.suit == targetSuit
                if (!orderMatters) {
                    if (suitCorrect) correct++
                } else {
                    val rankCorrect = c < expectedRanks.size && card.rank.value == expectedRanks[c].value
                    if (suitCorrect && rankCorrect) correct++
                }
            }
        }
        return correct
    }
}
