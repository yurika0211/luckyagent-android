package com.luckyagent.android.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticHotspotTest {
    @Test
    fun flagsNestedLoopAndRepeatedParse() {
        val source = """
            fun project(messages: List<String>) {
                messages.forEach { message ->
                    parts.forEach { part ->
                        parseHtml(part)
                    }
                }
            }
        """.trimIndent()

        val hits = StaticHotspotScanner.scan("Chat.kt", source)

        assertTrue(hits.any { it.kind == HotspotKind.NESTED_LOOP })
        assertTrue(hits.any { it.kind == HotspotKind.REPEATED_PARSE && it.line > 1 })
    }

    @Test
    fun flagsStringGrowthInsideALoop() {
        val source = """
            fun render(rows: List<String>): String {
                var text = ""
                for (row in rows) {
                    text += row
                }
                return text
            }
        """.trimIndent()

        val hits = StaticHotspotScanner.scan("Render.kt", source)

        assertEquals(1, hits.count { it.kind == HotspotKind.STRING_CONCAT_IN_LOOP })
    }

    @Test
    fun ignoresCommentsAndKeepsBoundedCollections() {
        val source = """
            fun quiet() {
                // messages.forEach { parseHtml(it) }
                val bounded = ArrayList<String>(MAX_ENTRIES)
                val open = mutableListOf<String>()
            }
        """.trimIndent()

        val hits = StaticHotspotScanner.scan("Quiet.kt", source)

        assertTrue(hits.none { it.kind == HotspotKind.REPEATED_PARSE })
        assertEquals(listOf("val open = mutableListOf<String>()"), hits.map { it.evidence })
    }
}
