package com.luckyagent.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun parsesTwoColumnTableWithHeaderAndBody() {
        val table = table("| Name | Count |\n| --- | --- |\n| apples | 2 |\n| pears | 3 |")

        assertEquals(
            listOf(
                listOf("Name", "Count"),
                listOf("apples", "2"),
                listOf("pears", "3"),
            ),
            table.rows,
        )
    }

    @Test
    fun preservesLeftCenterAndRightAlignment() {
        val table = table("| Left | Centre | Right |\n| :--- | :---: | ---: |")

        assertEquals(
            listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT),
            table.alignments,
        )
    }

    @Test
    fun keepsEmptyCellsAndNormalizesShortBodyRows() {
        val table = table("| A | B | C |\n| --- | --- | --- |\n| value |  |\n| extra | two | three | ignored |")

        assertEquals(listOf("value", "", ""), table.rows[1])
        assertEquals(listOf("extra", "two", "three"), table.rows[2])
    }

    @Test
    fun escapedPipeStaysInsideCellAndIsUnescaped() {
        val table = table("| A | B |\n| --- | --- |\n| left \\| right | ok |")

        assertEquals(listOf("left | right", "ok"), table.rows[1])
    }

    @Test
    fun codeSpanPipeStaysInsideCell() {
        val table = table("| Syntax | Meaning |\n| --- | --- |\n| `a|b` | literal |")

        assertEquals(listOf("`a|b`", "literal"), table.rows[1])
    }

    @Test
    fun linkDestinationPipeStaysInsideCell() {
        val table = table("| Link | Label |\n| --- | --- |\n| [query](https://example.test/a|b) | docs |")

        assertEquals(listOf("[query](https://example.test/a|b)", "docs"), table.rows[1])
    }

    @Test
    fun supportsExplicitOneColumnTable() {
        val table = table("| Value |\n| --- |\n| one |\n| two |")

        assertEquals(1, table.alignments.size)
        assertEquals(listOf(listOf("Value"), listOf("one"), listOf("two")), table.rows)
    }

    @Test
    fun treatsSetextLookingOneColumnTextAsParagraph() {
        val blocks = splitMarkdownBlocks("Value\n---")

        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun mismatchedDelimiterColumnsFallBackWithoutCrashing() {
        val blocks = splitMarkdownBlocks("| A | B |\n| --- |")

        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun hidesIncompleteTableDuringStreamingUntilSeparatorIsComplete() {
        val blocks = splitMarkdownBlocks("| A | B |\n| ---", hideIncompleteTables = true)

        assertTrue(blocks.any { it is MdBlock.IncompleteTable })
        assertTrue(blocks.none { it is MdBlock.Paragraph && it.text.contains("| A |") })
    }

    @Test
    fun hidesHeaderWithTrailingNewlineWhileStreaming() {
        val blocks = splitMarkdownBlocks("| A | B |\n", hideIncompleteTables = true)

        assertTrue(blocks.any { it is MdBlock.IncompleteTable })
        assertTrue(blocks.none { it is MdBlock.Paragraph })
    }

    @Test
    fun keepsExistingMarkdownBlockTypesWorking() {
        val blocks = splitMarkdownBlocks(
            "# Heading\n\n**bold** and *italic* [link](https://example.test)\n\n- item\n\n```\ncode\n```",
        )

        assertTrue(blocks.any { it is MdBlock.Heading })
        assertTrue(blocks.any { it is MdBlock.Paragraph && it.text.contains("**bold**") })
        assertTrue(blocks.any { it is MdBlock.ListItem })
        assertTrue(blocks.any { it is MdBlock.Code && it.body == "code" })
    }

    @Test
    fun parsesHeadingLevelsFourThroughSix() {
        val headings = splitMarkdownBlocks("#### Four\n##### Five\n###### Six")
            .map { it as MdBlock.Heading }

        assertEquals(listOf(4, 5, 6), headings.map { it.level })
        assertEquals(listOf("Four", "Five", "Six"), headings.map { it.text })
    }

    @Test
    fun parsesOrderedListItemWithItsNumber() {
        val item = splitMarkdownBlocks("1. first").single() as MdBlock.ListItem

        assertEquals("first", item.text)
        assertEquals("1.", item.marker)
    }

    private fun table(markdown: String): MdBlock.Table {
        val table = splitMarkdownBlocks(markdown).single() as? MdBlock.Table
        return table ?: error("Expected a table block: $markdown")
    }
}
