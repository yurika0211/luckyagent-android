package com.luckyagent.android.data.openui

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun cjkColumnIsWiderThanALatinLengthEstimate() {
        val cell = "咖啡因"
        val units = openUiDisplayUnits(cell)
        val width = openUiColumnWidthDp(listOf(listOf(cell)), 0)

        assertTrue(units > cell.length)
        assertEquals(units * 8 + 16, width)
        assertTrue(width > cell.length * 8 + 16)
    }

    @Test
    fun tableGridReportsRemainingAndShowsAllWhenExpanded() {
        val rows = (1..10).joinToString(",") { """["饮品$it","$it"]""" }
        val json = Json { ignoreUnknownKeys = true }
        val props = json.parseToJsonElement(
            """{"columns":["饮品","咖啡因"],"rows":[$rows]}""",
        ).jsonObject
        val node = OpenUiNode(id = "t", type = "Table", props = props)
        val (clipped, extra) = node.tableGrid()
        assertEquals(2, extra)
        assertEquals(9, clipped.size)
        val (full, none) = node.tableGrid(OpenUiTableExpandedRows)
        assertEquals(0, none)
        assertEquals(11, full.size)
        assertEquals(listOf("饮品10", "10"), full.last())
    }

    @Test
    fun listItemsReportsRemainingAndShowsAllWhenExpanded() {
        val json = Json { ignoreUnknownKeys = true }
        val labels = (1..14).joinToString(",") { """{"label":"项$it"}""" }
        val props = json.parseToJsonElement("""{"items":[$labels]}""").jsonObject
        val node = OpenUiNode(id = "l", type = "List", props = props)
        val (clipped, extra) = node.listItems()
        assertEquals(2, extra)
        assertEquals(12, clipped.size)
        val (full, none) = node.listItems(OpenUiListExpandedItems)
        assertEquals(0, none)
        assertEquals(14, full.size)
    }

    @Test
    fun actionsWithoutPolicyFieldAreSendable() {
        val json = Json { ignoreUnknownKeys = true }
        val node = json.decodeFromString<OpenUiNode>(
            """{"id":"a","type":"Actions","props":{"items":[{"id":"x","label":"绿茶"}]}}""",
        )
        assertEquals("inspect", node.policy)
        assertTrue(node.canSendAction())
        assertTrue(node.copy(policy = "mutate").canSendAction())
        assertTrue(node.copy(policy = "action").canSendAction())
        assertFalse(node.copy(type = "List").canSendAction())
    }

    @Test
    fun actionItemsReportRemainingAndShowAllWhenExpanded() {
        val labels = (1..6).map { "选项$it" }
        val (clipped, extra) = openUiActionItems(labels)
        assertEquals(4, clipped.size)
        assertEquals(2, extra)
        val (full, none) = openUiActionItems(labels, OpenUiActionExpandedItems)
        assertEquals(0, none)
        assertEquals(labels, full)
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
    fun chartKindFallsBackToBar() {
        assertEquals("bar", openUiChartKind("", 3, 10f))
        assertEquals("bar", openUiChartKind("柱", 3, 10f))
        assertEquals("bar", openUiChartKind("折线", 3, 10f))
        assertEquals("bar", openUiChartKind("foo", 3, 10f))
        assertEquals("line", openUiChartKind("line", 3, 10f))
        assertEquals("area", openUiChartKind("AREA", 3, 10f))
        assertEquals("pie", openUiChartKind("pie", 6, 10f))
        assertEquals("bar", openUiChartKind("pie", 7, 10f))
        assertEquals("bar", openUiChartKind("pie", 3, 0f))
        assertEquals("bar", openUiChartKind("pie", 3, -1f))
    }

    @Test
    fun chartWindowKeepsTheLastPoints() {
        val values = (1..10).map { it.toFloat() }
        val labels = (1..10).map { "第$it" }
        val (windowed, names) = openUiChartWindow(values, labels, 3)
        assertEquals(listOf(8f, 9f, 10f), windowed)
        assertEquals(listOf("第8", "第9", "第10"), names)
        val (all, allNames) = openUiChartWindow(values, labels, 0)
        assertEquals(values, all)
        assertEquals(labels, allNames)
    }

    @Test
    fun chartHitPicksTheSeriesUnderTheTap() {
        val bars = listOf(listOf(1f, 2f), listOf(3f, 4f))
        assertEquals(1 to 1, openUiChartHit("bar", 2, 2, 0.8f, 0.5f, bars))
        val lines = listOf(listOf(10f), listOf(0f))
        assertEquals(0 to 0, openUiChartHit("line", 2, 1, 0.2f, 0.05f, lines))
        assertEquals(1 to 0, openUiChartHit("area", 2, 1, 0.2f, 0.95f, lines))
        val pie = openUiChartHit("pie", 1, 3, 0.5f, 0.1f, listOf(listOf(1f, 1f, 1f)))
        assertEquals(0, pie?.first)
    }

    @Test
    fun chartReadoutJoinsLabelSeriesValueAndUnit() {
        assertEquals("周二 · 咖啡因 95 mg", openUiChartReadout("周二", "咖啡因", 95f, "mg"))
        assertEquals("咖啡因 12.5", openUiChartReadout("", "咖啡因", 12.5f, ""))
    }

    @Test
    fun chartRangeReadsOptionalControl() {
        val json = Json { ignoreUnknownKeys = true }
        val props = json.parseToJsonElement(
            """{"controls":[{"id":"n","type":"range","min":1,"max":10,"step":1,"value":4}]}""",
        ).jsonObject
        val range = OpenUiNode(id = "c", type = "Chart", props = props).chartRange()
        assertEquals("n", range?.id)
        assertEquals(4f, range?.value)
        assertNull(OpenUiNode(id = "c", type = "Chart").chartRange())
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
