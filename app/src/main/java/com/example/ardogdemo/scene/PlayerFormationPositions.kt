package com.example.ardogdemo.scene

import com.example.ardogdemo.domain.character.FormationLayout
import com.example.ardogdemo.domain.character.FormationOffset
import com.example.ardogdemo.domain.character.MultiModelFormation
import com.example.ardogdemo.presentation.ArDogState
import io.github.sceneview.math.Position

/**
 * Scene positions for every retained player instance, indexed like the Filament instances.
 * Calibrated on SM-A165F; see plans/260918-ardog-multi-model-layouts/reports/layout-calibration-record.md.
 *
 * The scene camera sits almost level with the characters, so a flat ground formation would
 * hide rear rows behind the front one. Rear offsets are lifted by [FORMATION_RISE] per unit of
 * depth, tilting the formation plane toward the camera like an elevated view. Hidden clones
 * beyond the resolved count park at the origin; the root scale multiplies the base factor back.
 */
internal fun ArDogState.playerFormationPositions(): List<Position> {
    val offsets = MultiModelFormation.offsets(
        layout = formationLayout,
        count = formationCount,
        spacing = if (formationLayout == FormationLayout.Row) FORMATION_ROW_SPACING else FORMATION_SPACING,
        rowDepth = FORMATION_ROW_DEPTH_STEP,
    )
    return List(MultiModelFormation.MAX_COUNT) { index ->
        val offset = offsets.getOrNull(index) ?: FormationOffset(0f, 0f)
        Position(
            x = offset.x / GUGUGAGA_BASE_SCALE,
            y = -offset.z * FORMATION_RISE / GUGUGAGA_BASE_SCALE,
            z = offset.z / GUGUGAGA_BASE_SCALE,
        )
    }
}

internal const val GUGUGAGA_BASE_SCALE = .25f
private const val FORMATION_SPACING = .50f
private const val FORMATION_ROW_SPACING = .34f
private const val FORMATION_ROW_DEPTH_STEP = .45f
private const val FORMATION_RISE = .75f
