package com.example.ardogdemo.domain.character

enum class CharacterReadiness { Loading, Ready, Failed }

enum class AccessorySlot { Mouth, Eyes }

enum class CharacterAction(
    val clip: String,
    val durationMs: Long,
    val loops: Boolean = false,
) {
    Idle("Idle", Long.MAX_VALUE, true),
    RunForward("Walk", Long.MAX_VALUE, true),
    Punch("Attack", 1_067),
    Dance("Dancing", 17_000),
    Howl("Howl", 2_367),
}

enum class AccessoryId(val slot: AccessorySlot) {
    Pipe(AccessorySlot.Mouth),
    Cigar(AccessorySlot.Mouth),
    BirthdayGlasses(AccessorySlot.Eyes),
    SpiralGlasses(AccessorySlot.Eyes),
}

data class ModelTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val yaw: Float = 0f,
)

fun AccessoryId.assetName(): String = when (this) {
    AccessoryId.Pipe -> "pipe"
    AccessoryId.Cigar -> "cigar"
    AccessoryId.BirthdayGlasses -> "birthday_glasses"
    AccessoryId.SpiralGlasses -> "spiral_glasses"
}
