package com.luckyagent.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StartupLoadingTest {
    @Test
    fun threeLeavesHaveSeparateSeats() {
        listOf(18f, 42f).forEach { side ->
            val geometry = cloverGeometry(side)
            assertEquals(3, geometry.leaves.size)
            val centers = geometry.leaves.map { it.center }
            assertEquals(3, centers.distinctBy { it.x to it.y }.size)
            val orbit = hypot(
                (centers[1].x - centers[0].x).toDouble(),
                (centers[1].y - centers[0].y).toDouble(),
            )
            val leafReach = hypot(geometry.leaves[0].size.width / 2.0, geometry.leaves[0].size.height / 2.0)
            assertTrue("leaves overlap at $side", orbit > leafReach * 2)
            val rotations = geometry.leaves.map { it.rotationDegrees }
            assertEquals(3, rotations.distinct().size)
            assertEquals(side / 2f, geometry.stemEnd.x, 0.01f)
            assertTrue(geometry.stemEnd.y > side / 2f)
            geometry.leaves.forEach { leaf ->
                val reach = hypot(leaf.size.width / 2.0, leaf.size.height / 2.0)
                val fromJunction = hypot(
                    (leaf.center.x - side / 2f).toDouble(),
                    (leaf.center.y - side / 2f).toDouble(),
                )
                val fromStemEnd = hypot(
                    (leaf.center.x - geometry.stemEnd.x).toDouble(),
                    (leaf.center.y - geometry.stemEnd.y).toDouble(),
                )
                assertTrue(fromJunction > reach)
                assertTrue(fromStemEnd > reach)
            }
        }
    }
}
