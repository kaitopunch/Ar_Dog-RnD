package com.example.ardogdemo.asset

data class CharacterManifest(
    val modelPaths: List<String>,
    val clipNames: List<String>,
    val accessoryIds: List<String>,
) {
    fun validationErrors(): List<String> = buildList {
        if (modelPaths.distinct().size != modelPaths.size) add("Duplicate model path")
        if (accessoryIds.distinct().size != accessoryIds.size) add("Duplicate accessory id")
        listOf("Idle", "Walk", "Attack", "Dancing", "Howl").filterNot(clipNames::contains)
            .forEach { add("Missing clip: $it") }
        if (modelPaths.size != 9) add("Expected 9 valid accessory variants")
    }

    companion object {
        val Gugugaga = CharacterManifest(
            modelPaths = listOf(
                "base", "pipe", "cigar", "birthday_glasses", "spiral_glasses",
                "pipe_birthday_glasses", "pipe_spiral_glasses",
                "cigar_birthday_glasses", "cigar_spiral_glasses",
            ).map { "models/gugugaga/$it.glb" },
            clipNames = listOf("Idle", "Walk", "Attack", "Dancing", "Howl"),
            accessoryIds = listOf("pipe", "cigar", "birthday_glasses", "spiral_glasses"),
        )
    }
}
