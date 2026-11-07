package com.luckyagent.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkCardTest {
    @Test
    fun openGraphBecomesACard() {
        val html = """
            <html><head>
            <title>页标题</title>
            <meta property="og:title" content="卡片标题">
            <meta property="og:description" content="一段说明">
            <meta property="og:site_name" content="示例">
            <meta property="og:image" content="https://example.com/cover.jpg">
            </head></html>
        """.trimIndent()
        val card = parseLinkPreview("https://example.com/a", html, "text/html; charset=utf-8")
        assertEquals("卡片标题", card?.title)
        assertEquals("一段说明", card?.description)
        assertEquals("示例", card?.site)
        assertEquals("https://example.com/cover.jpg", card?.image)
    }

    @Test
    fun nonHtmlFallsBackToPlainLink() {
        assertNull(parseLinkPreview("https://example.com/file.bin", "PK", "application/octet-stream"))
        assertNull(parseLinkPreview("https://example.com/empty", "<html></html>", "text/html"))
    }
}
