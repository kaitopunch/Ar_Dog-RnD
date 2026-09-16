package com.example.ardogdemo.asset

import android.content.res.AssetManager

class CharacterAssetRepository(private val assets: AssetManager) {
    fun validate(manifest: CharacterManifest = CharacterManifest.Gugugaga): List<String> = buildList {
        addAll(manifest.validationErrors())
        manifest.modelPaths.forEach { path ->
            runCatching { assets.open(path).use { } }
                .onFailure { add("Missing model: $path") }
        }
        runCatching { assets.open("character/gugugaga.json").close() }
            .onFailure { add("Missing character manifest") }
    }
}
