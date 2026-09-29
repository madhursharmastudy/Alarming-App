package com.example.data

enum class ChallengeType(val displayName: String, val iconDescription: String) {
    TYPING("Typing Task", "Exact-match typing under time pressure"),
    IMAGE_ARRANGEMENT("Image Arrangement", "Arrange sliced image pieces with holding area"),
    CHARGER("Charger Action", "Plug in or unplug phone charger"),
    CARD_ARRANGEMENT("Card Arrangement", "Arrange suits and ranks on a 4-row board"),
    PATTERN_LOCK("Pattern Lock", "Draw matching dot pattern from reference"),
    ODD_ONE_OUT("Odd One Out", "Spot the subtly different shape or rotation"),
    REACTION_GAME("Reaction Target", "Time your tap inside moving hit zone"),
    MATH("Math Equations", "Sequential arithmetic problems per stage"),
    SHAKE("Shake Device", "High-energy device motion"),
    STEPS("Walk Steps", "Physical steps tracking"),
    QR("QR Code Scan", "Scan custom QR / Barcode")
}
