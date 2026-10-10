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
    fun treatsSetextLookingOneColumnTextAsParagraphThenRule() {
        val blocks = splitMarkdownBlocks("Value\n---")

        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertEquals("Value", (blocks[0] as MdBlock.Paragraph).text)
        assertTrue(blocks[1] is MdBlock.Rule)
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
    fun thematicBreakBecomesARule() {
        val blocks = splitMarkdownBlocks("before\n\n---\n\nafter")

        assertTrue(blocks[1] is MdBlock.Rule)
        assertEquals("before", (blocks[0] as MdBlock.Paragraph).text)
        assertEquals("after", (blocks[2] as MdBlock.Paragraph).text)
    }

    @Test
    fun spacedDashesAndStarsAreRules() {
        assertTrue(isThematicBreak("- - -"))
        assertTrue(isThematicBreak("***"))
        assertTrue(!isThematicBreak("--"))
    }

    @Test
    fun displayMathIsItsOwnBlock() {
        val block = splitMarkdownBlocks("$$\\frac{a}{b}$$").single() as MdBlock.Math

        assertEquals("\\frac{a}{b}", block.latex)
        assertTrue(block.display)
    }

    @Test
    fun multilineDisplayMathKeepsTheBody() {
        val block = splitMarkdownBlocks("$$\n\\sum_i x_i\n$$").single() as MdBlock.Math

        assertTrue(block.display)
        assertTrue(block.latex.contains("\\sum_i x_i"))
    }

    @Test
    fun inlineDisplayStyleStaysInsideTheParagraph() {
        val paragraph = splitMarkdownBlocks("area is $\\displaystyle \\frac{a}{b}$ here").single() as MdBlock.Paragraph

        assertTrue(paragraph.text.contains("\\displaystyle"))
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

    @Test
    fun fenceKeepsItsLanguage() {
        val code = splitMarkdownBlocks("```kotlin title\nval x = 1\n```").single() as MdBlock.Code

        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1", code.body)
    }

    @Test
    fun fenceWithoutLanguageHasEmptyLang() {
        val code = splitMarkdownBlocks("```\nplain\n```").single() as MdBlock.Code

        assertEquals("", code.lang)
    }

    @Test
    fun mermaidFenceBecomesAMermaidBlock() {
        val block = splitMarkdownBlocks("```mermaid\nflowchart LR\n  A-->B\n```").single() as MdBlock.Mermaid

        assertTrue(block.source.contains("A-->B"))
    }

    @Test
    fun unclosedMermaidFenceStaysSource() {
        val block = splitMarkdownBlocks("```mermaid\nflowchart LR").single() as MdBlock.Code

        assertEquals("mermaid", block.lang)
    }

    @Test
    fun htmlFenceBecomesAnHtmlBlock() {
        val block = splitMarkdownBlocks("```html\n<b>bold</b>\n```").single() as MdBlock.Html

        assertTrue(block.source.contains("<b>bold</b>"))
    }

    @Test
    fun detailsBlockIsHtml() {
        val block = splitMarkdownBlocks("<details><summary>more</summary>\nbody\n</details>").single()

        assertTrue(block is MdBlock.Html)
    }

    @Test
    fun inlineHtmlStaysInTheParagraph() {
        val block = splitMarkdownBlocks("this is <b>bold</b> text").single() as MdBlock.Paragraph

        assertTrue(block.text.contains("<b>bold</b>"))
    }

    @Test
    fun streamingMermaidStaysSourceUntilItsOwnFenceCloses() {
        val blocks = splitMarkdownBlocks("```mermaid\nflowchart LR\n  A-->B\n```kotlin\nval x = 1\n```")
        val res = MarkdownParserTest::class.java.classLoader.getResource("com/luckyagent/android/ui/components/MarkdownParserKt.class")
        println("RESOURCE " + res)
        println("COUNT " + blocks.size); blocks.forEach { println("BLOCK " + it::class.simpleName + " " + it.toString().replace("\n", "|")) }

        val first = blocks[0] as MdBlock.Code
        assertEquals("mermaid", first.lang)
        assertTrue(first.body.contains("A-->B"))
        assertEquals("kotlin", (blocks[1] as MdBlock.Code).lang)
        assertEquals("val x = 1", (blocks[1] as MdBlock.Code).body)
    }

    @Test
    fun mermaidFenceKeepsOnlyItsOwnBody() {
        val block = splitMarkdownBlocks("```mermaid\nflowchart LR\n  A-->B\n```\n\nafter").first() as MdBlock.Mermaid

        assertEquals("flowchart LR\n  A-->B", block.source.trim())
    }
}
