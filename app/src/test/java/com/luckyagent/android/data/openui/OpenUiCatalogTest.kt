package com.luckyagent.android.data.openui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
    fun tableGridJoinsHeaderAndClipsBody() {
        val json = Json { ignoreUnknownKeys = true }
        val props = json.parseToJsonElement(
            """{"columns":["饮品","咖啡因"],"rows":[["红茶","47-95 mg"],["咖啡","80-200 mg"]]}""",
        ).jsonObject
        val (grid, extra) = OpenUiNode(id = "t", type = "Table", props = props).tableGrid()
        assertEquals(listOf("饮品", "咖啡因"), grid[0])
        assertEquals(listOf("红茶", "47-95 mg"), grid[1])
        assertEquals(0, extra)
        assertEquals(3, grid.size)
    }

    @Test
    fun mapPointsReadNameAndCoordinates() {
        val json = Json { ignoreUnknownKeys = true }
        val props = json.parseToJsonElement(
            """{"points":[{"name":"望京","lat":39.99,"lng":116.48},{"label":"只写了名字"}]}""",
        ).jsonObject
        val points = OpenUiNode(id = "m", type = "Map", props = props).mapPoints()
        assertEquals("望京", points[0].name)
        assertEquals(39.99, points[0].lat)
        assertEquals(116.48, points[0].lng)
        assertEquals("只写了名字", points[1].name)
        assertEquals(null, points[1].lat)
    }

    @Test
    fun listItemsReadsLabelsAndClips() {
        val json = Json { ignoreUnknownKeys = true }
        val props = json.parseToJsonElement(
            """{"items":[{"label":"红茶"},{"text":"咖啡"},"水"]}""",
        ).jsonObject
        val (items, extra) = OpenUiNode(id = "l", type = "List", props = props).listItems(maxItems = 2)
        assertEquals(listOf("红茶", "咖啡"), items)
        assertEquals(1, extra)
    }

    @Test
    fun headingAndCaptionPreferTitleThenName() {
        val json = Json { ignoreUnknownKeys = true }
        val titled = OpenUiNode(
            id = "n",
            type = "Chart",
            props = json.parseToJsonElement("""{"title":"咖啡因","caption":"每杯"}""").jsonObject,
        )
        assertEquals("咖啡因", titled.heading())
        assertEquals("每杯", titled.caption())
        val named = OpenUiNode(id = "n", type = "Map", props = json.parseToJsonElement("""{"name":"望京"}""").jsonObject)
        assertEquals("望京", named.heading())
        assertNull(named.caption())
    }

    @Test
    fun chartSeriesReadsNamedValues() {
        val json = Json { ignoreUnknownKeys = true }
        val props = json.parseToJsonElement(
            """{"kind":"bar","series":[{"name":"咖啡因","values":[47,95]}]}""",
        ).jsonObject
        val series = OpenUiNode(id = "c", type = "Chart", props = props).chartSeries()
        assertEquals("咖啡因", series.single().name)
        assertEquals(listOf(47f, 95f), series.single().values)
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
