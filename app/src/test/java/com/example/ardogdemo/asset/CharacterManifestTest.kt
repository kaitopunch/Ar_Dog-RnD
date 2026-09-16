package com.example.ardogdemo.asset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterManifestTest {
    @Test fun `production manifest contains all clips items and combinations`() {
        val manifest = CharacterManifest.Gugugaga
        assertTrue(manifest.validationErrors().isEmpty())
        assertEquals(9, manifest.modelPaths.size)
        assertEquals(4, manifest.accessoryIds.size)
        assertEquals(5, manifest.clipNames.size)
    }

    @Test fun `missing clip and duplicate id are rejected`() {
        val invalid = CharacterManifest(listOf("base"), listOf("Idle"), listOf("pipe", "pipe"))
        assertTrue(invalid.validationErrors().any { it.startsWith("Missing clip") })
        assertTrue(invalid.validationErrors().contains("Duplicate accessory id"))
    }
}
