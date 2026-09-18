package com.example.ardogdemo.domain.character

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class FormationLayout { Row, Triangle, Circle }

/** Local ground-plane offset from the primary character, in scene units before base-scale compensation. */
data class FormationOffset(val x: Float, val z: Float)

/**
 * Pure rules for the timed multi-model formation. Index 0 is always the user-controlled
 * primary at (0, 0); clones spread evenly around it. The camera looks down +Z, so
 * "behind the primary" is negative Z.
 */
object MultiModelFormation {
    const val MIN_COUNT = 3
    const val MAX_COUNT = 6
    const val DEFAULT_COUNT = 6

    /** Clamps a remote-config value into the supported range; missing values use the default. */
    fun clampCount(remote: Int?): Int = (remote ?: DEFAULT_COUNT).coerceIn(MIN_COUNT, MAX_COUNT)

    /** Triangle needs a triangular number of slots, so it rounds down to the nearest one. */
    fun resolveCount(count: Int, layout: FormationLayout): Int {
        val clamped = clampCount(count)
        if (layout != FormationLayout.Triangle) return clamped
        return largestTriangularAtMost(clamped).coerceAtLeast(MIN_COUNT)
    }

    fun offsets(layout: FormationLayout, count: Int, spacing: Float, rowDepth: Float): List<FormationOffset> =
        when (layout) {
            FormationLayout.Row -> rowOffsets(count, spacing)
            FormationLayout.Triangle -> triangleOffsets(count, spacing, rowDepth)
            FormationLayout.Circle -> circleOffsets(count, spacing)
        }

    private fun rowOffsets(count: Int, spacing: Float) = List(count) { i ->
        val step = (i + 1) / 2
        val sign = if (i % 2 == 1) 1f else -1f
        FormationOffset(x = if (i == 0) 0f else sign * step * spacing, z = 0f)
    }

    private fun triangleOffsets(count: Int, spacing: Float, rowDepth: Float): List<FormationOffset> {
        val result = ArrayList<FormationOffset>(count)
        var row = 0
        while (result.size < count) {
            for (column in 0..row) {
                if (result.size == count) break
                result += FormationOffset(x = (column - row / 2f) * spacing, z = -row * rowDepth)
            }
            row++
        }
        return result
    }

    private fun circleOffsets(count: Int, spacing: Float): List<FormationOffset> {
        val radius = spacing / (2f * sin(PI / count).toFloat())
        return List(count) { i ->
            val theta = i * 2.0 * PI / count
            FormationOffset(
                x = radius * sin(theta).toFloat(),
                z = -radius + radius * cos(theta).toFloat(),
            )
        }
    }

    private fun largestTriangularAtMost(value: Int): Int {
        var k = 1
        while ((k + 1) * (k + 2) / 2 <= value) k++
        return k * (k + 1) / 2
    }
}
