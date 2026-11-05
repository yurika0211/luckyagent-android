package com.luckyagent.android.ui.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ForceSimulationTest {
    private val graph = GraphTopology(listOf("a", "b", "c"), listOf(GraphLink("a", "b"), GraphLink("b", "c")))

    @Test fun retainsSeededPositionsAndInitializesOnlyNewNodes() {
        val seed = GraphPoint(-320f, 210f)
        val simulation = ForceSimulation(graph, mapOf("b" to seed, "gone" to GraphPoint(5f, 5f)))
        assertEquals(seed, simulation.snapshot()["b"])
        assertEquals(graph.nodeIds.toSet(), simulation.snapshot().keys)
        assertTrue(simulation.snapshot().values.all { it.isFinite })
    }

    @Test fun invalidSeedsAreReplacedWithFiniteCoordinates() {
        val simulation = ForceSimulation(graph, mapOf("a" to GraphPoint(Float.NaN, Float.POSITIVE_INFINITY)))
        repeat(10) { simulation.tick() }
        assertTrue(simulation.snapshot().values.all { it.isFinite })
    }

    @Test fun draggedNodeIsFixedAndReheatsACooledSimulation() {
        val simulation = ForceSimulation(graph)
        repeat(320) { simulation.tick() }
        assertFalse(simulation.active)
        val pin = GraphPin("b", GraphPoint(1000f, -640f))
        repeat(20) {
            simulation.tick(pin)
            assertEquals(pin.point, simulation.snapshot()["b"])
        }
        assertTrue(simulation.active)
        simulation.tick()
        assertTrue(pin.point != simulation.snapshot()["b"])
    }

    @Test fun collisionSeparatesCoincidentNodesWithoutRandomNaNs() {
        val graph = GraphTopology(listOf("a", "b"), emptyList())
        val origin = GraphPoint(0f, 0f)
        val simulation = ForceSimulation(graph, mapOf("a" to origin, "b" to origin),
            GraphForces(center = 0f, repulsion = 0f, linkStrength = 0f))
        repeat(100) { simulation.tick() }
        val a = simulation.snapshot().getValue("a")
        val b = simulation.snapshot().getValue("b")
        assertTrue(hypot(a.x - b.x, a.y - b.y) >= 35.9f)
    }

    @Test fun layoutIsDeterministicAndSnapshotsDoNotMutate() {
        val first = ForceSimulation(graph)
        val second = ForceSimulation(graph)
        val initial = first.snapshot()
        repeat(60) { first.tick(); second.tick() }
        assertEquals(first.snapshot(), second.snapshot())
        assertTrue(initial != first.snapshot())
    }

    @Test fun largeHubGraphCoolsWithFiniteUnclippedPositions() {
        val ids = (0 until 1500).map { "n$it" }
        val graph = GraphTopology(ids, ids.drop(1).map { GraphLink("n0", it) })
        val simulation = ForceSimulation(graph)
        repeat(305) { simulation.tick() }
        assertFalse(simulation.active)
        assertEquals(1500, simulation.snapshot().size)
        assertTrue(simulation.snapshot().values.all { it.isFinite })
        assertTrue(simulation.snapshot().values.any { it.x < 0 || it.y < 0 })
    }

    @Test fun cooledLayoutStopsRebuilding() {
        val simulation = ForceSimulation(graph)
        var steps = 0
        while (simulation.active && steps < 400) {
            simulation.tick()
            steps++
        }
        assertTrue(steps < 400)
        assertFalse(simulation.active)
        assertTrue(simulation.temperature <= ForceSimulation.STOP_TEMPERATURE)
        val settled = simulation.snapshot()
        repeat(20) { simulation.tick() }
        assertEquals(settled, simulation.snapshot())
        assertTrue(simulation.temperature <= ForceSimulation.STOP_TEMPERATURE)
    }

    @Test fun emptyGraphAndUnknownPinAreSafe() {
        val empty = ForceSimulation(GraphTopology(emptyList(), emptyList()))
        empty.tick()
        assertFalse(empty.active)
        assertTrue(empty.snapshot().isEmpty())
        val simulation = ForceSimulation(graph)
        simulation.tick(GraphPin("missing", GraphPoint(0f, 0f)))
        assertTrue(simulation.snapshot().values.all { it.isFinite })
    }
}
