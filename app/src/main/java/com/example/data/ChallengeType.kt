package com.example.data

enum class ChallengeType(val displayName: String, val iconDescription: String) {
    AUDIO("Audio Voice Math", "Spoken math questions and voice answers"),
    CAMERA("Camera Routine Match", "Real-world 3-step proof & object match"),
    TYPING("Typing Task", "Exact-match typing under time pressure"),
    IMAGE_ARRANGEMENT("Image Arrangement", "Arrange sliced image pieces with holding area"),
    CHARGER("Charger Action", "Plug in or unplug phone charger"),
    CARD_ARRANGEMENT("Card Arrangement", "Arrange suits and ranks on a 4-row board"),
    PATTERN_LOCK("Pattern Lock", "Draw matching 3x3 pattern"),
    ODD_ONE_OUT("Odd One Out", "Spot the subtly different shape or color"),
    REACTION_GAME("Reaction Target", "Time your tap inside moving hit zone"),
    MATH("Math Equations", "Progressive arithmetic problems"),
    SHAKE("Shake Device", "High-energy device motion"),
    STEPS("Walk Steps", "Physical steps tracking"),
    QR("QR Code Scan", "Scan custom QR / Barcode")
}
