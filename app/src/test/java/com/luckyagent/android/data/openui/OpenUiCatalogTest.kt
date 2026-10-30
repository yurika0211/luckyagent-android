package com.luckyagent.android.data.openui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenUiCatalogTest {
    @Test
    fun keepCatalogDropsUnknownTypes() {
        val delta = OpenUiDelta(
            treeId = "t1",
            nodes = listOf(
                OpenUiNode(id = "a", type = "Table", props = JsonObject(mapOf("columns" to JsonPrimitive("n")))),
                OpenUiNode(id = "b", type = "Carousel"),
            ),
        ).keepCatalog()
        assertEquals(1, delta.nodes.size)
        assertEquals("Table", delta.nodes[0].type)
    }

    @Test
    fun restoresClosedTreeFromStoredToolArguments() {
        val args = """
            {"tree":{"state":"complete","tree_id":"t1","nodes":[{"id":"a","type":"List","props":{"items":["x"]}}]}}
        """.trimIndent()

        val tree = openUiTreeFromToolArguments(args)

        assertEquals("t1", tree?.treeId)
        assertEquals("List", tree?.nodes?.single()?.type)
    }

    @Test
    fun ignoresHalfStreamedTreeInHistory() {
        val args = """{"tree":{"state":"streaming","nodes":[{"id":"a","type":"Table"}]}}"""
        assertEquals(null, openUiTreeFromToolArguments(args))
    }

    @Test
    fun ignoresMalformedToolArguments() {
        assertEquals(null, openUiTreeFromToolArguments("not json"))
        assertEquals(null, openUiTreeFromToolArguments(null))
        assertEquals(null, openUiTreeFromToolArguments("""{"tree":{"state":"complete","nodes":[]}}"""))
    }

    @Test
    fun parseDeltaFromWireJson() {
        val json = Json { ignoreUnknownKeys = true }
        val el = json.parseToJsonElement(
            """{"tree_id":"t1","state":"complete","nodes":[{"id":"a","type":"List","props":{"items":["x"]}}]}""",
        )
        val delta = el.asOpenUiDelta()
        assertEquals("t1", delta?.treeId)
        assertEquals("List", delta?.nodes?.single()?.type)
        assertTrue(delta?.nodes?.single()?.props?.containsKey("items") == true)
    }
}
