package com.example.ardogdemo.domain

import com.example.ardogdemo.domain.character.FormationLayout
import com.example.ardogdemo.domain.character.FormationOffset
import com.example.ardogdemo.domain.character.MultiModelFormation
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class MultiModelFormationTest {
    @Test fun `remote count is clamped and defaults to six`() {
        assertEquals(6, MultiModelFormation.clampCount(null))
        assertEquals(3, MultiModelFormation.clampCount(2))
        assertEquals(3, MultiModelFormation.clampCount(3))
        assertEquals(6, MultiModelFormation.clampCount(6))
        assertEquals(6, MultiModelFormation.clampCount(7))
        assertEquals(6, MultiModelFormation.clampCount(100))
    }

    @Test fun `triangle rounds down to a triangular number and other layouts keep the count`() {
        assertEquals(3, MultiModelFormation.resolveCount(4, FormationLayout.Triangle))
        assertEquals(4, MultiModelFormation.resolveCount(4, FormationLayout.Row))
        assertEquals(4, MultiModelFormation.resolveCount(4, FormationLayout.Circle))
        assertEquals(3, MultiModelFormation.resolveCount(5, FormationLayout.Triangle))
        assertEquals(5, MultiModelFormation.resolveCount(5, FormationLayout.Row))
        assertEquals(6, MultiModelFormation.resolveCount(6, FormationLayout.Triangle))
        assertEquals(3, MultiModelFormation.resolveCount(2, FormationLayout.Triangle))
    }

    @Test fun `every layout yields count offsets with the primary at origin`() {
        for (layout in FormationLayout.entries) {
            for (count in MultiModelFormation.MIN_COUNT..MultiModelFormation.MAX_COUNT) {
                val offsets = MultiModelFormation.offsets(layout, count, SPACING, DEPTH)
                assertEquals("$layout/$count", count, offsets.size)
                assertEquals("$layout/$count", FormationOffset(0f, 0f), offsets[0])
            }
        }
    }

    @Test fun `row alternates evenly to the right and left on one line`() {
        val offsets = MultiModelFormation.offsets(FormationLayout.Row, 6, SPACING, DEPTH)
        assertEquals(listOf(0f, SPACING, -SPACING, 2 * SPACING, -2 * SPACING, 3 * SPACING), offsets.map { it.x })
        offsets.forEach { assertEquals(0f, it.z, EPSILON) }
    }

    @Test fun `triangle fills symmetric rows one two three behind the apex`() {
        val offsets = MultiModelFormation.offsets(FormationLayout.Triangle, 6, SPACING, DEPTH)
        val rows = offsets.groupBy { it.z }
        assertEquals(listOf(0f, -DEPTH, -2 * DEPTH), rows.keys.toList())
        assertEquals(listOf(1, 2, 3), rows.values.map { it.size })
        rows.values.forEach { row ->
            assertEquals(0f, row.sumOf { it.x.toDouble() }.toFloat(), EPSILON)
            row.zipWithNext { a, b -> assertEquals(SPACING, b.x - a.x, EPSILON) }
        }
    }

    @Test fun `clones never stand in front of the primary`() {
        for (layout in FormationLayout.entries) {
            for (count in MultiModelFormation.MIN_COUNT..MultiModelFormation.MAX_COUNT) {
                val clones = MultiModelFormation.offsets(layout, count, SPACING, DEPTH).drop(1)
                clones.forEach { assertEquals("$layout/$count", true, it.z <= 0f) }
            }
        }
    }

    @Test fun `circle points are equidistant from the centre and evenly spaced`() {
        for (count in MultiModelFormation.MIN_COUNT..MultiModelFormation.MAX_COUNT) {
            val offsets = MultiModelFormation.offsets(FormationLayout.Circle, count, SPACING, DEPTH)
            val radius = SPACING / (2f * sin(PI / count).toFloat())
            offsets.forEach { assertEquals("$count", radius, hypot(it.x, it.z + radius), EPSILON) }
            (offsets + offsets.first()).zipWithNext { a, b ->
                assertEquals("$count", SPACING, hypot(b.x - a.x, b.z - a.z), EPSILON)
            }
        }
    }

    private companion object {
        const val SPACING = .5f
        const val DEPTH = .2f
        const val EPSILON = 1e-4f
    }
}
