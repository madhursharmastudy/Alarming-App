package com.example.data

enum class ChallengeType(val displayName: String, val iconDescription: String) {
    AUDIO("Audio Voice Math", "Spoken math questions and voice answers"),
    CAMERA("Camera Routine Match", "Real-world 3-step proof & object match"),
    PUZZLE("Puzzle Tile Rearrange", "Large high-contrast tile sliding"),
    MATH("Math Equations", "Progressive arithmetic problems"),
    SHAKE("Shake Device", "High-energy device motion"),
    STEPS("Walk Steps", "Physical steps tracking"),
    QR("QR Code Scan", "Scan custom QR / Barcode")
}
