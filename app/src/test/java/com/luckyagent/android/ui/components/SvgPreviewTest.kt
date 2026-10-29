package com.luckyagent.android.ui.components

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgPreviewTest {
    private fun svg(source: String) = requireNotNull(staticSvgDocument(source)) { "expected a static graphic" }

    private fun previewStyles(source: String, fit: Boolean): String =
        Jsoup.parse(svgPreviewDocument(svg(source), fit)).select("style").joinToString("\n") { it.data() }

    @Test fun staticSvgKeepsItsFrameAndDoesNotUseThePageViewer() {
        val source = """<svg viewBox="0 0 200 80" xmlns="http://www.w3.org/2000/svg"><rect width="200" height="80"/></svg>"""
        val frame = svg(source).frame
        assertNotNull(frame)
        assertTrue(requiresHtmlPage(source))
        assertEquals(SvgFrame(200f, 80f), frame)
        assertEquals(128f, svgCardHeight(frame, widthDp = 320f, viewportDp = 800f))
        assertFalse(svgCardScrolls(frame, widthDp = 320f, viewportDp = 800f))
    }

    @Test fun tallSvgScrollsInsideTheCardInsteadOfCroppingAt480() {
        val frame = SvgFrame(100f, 2000f)
        val height = svgCardHeight(frame, widthDp = 300f, viewportDp = 800f)
        assertEquals(576f, height)
        assertTrue(height < 300f * 20f)
        assertTrue(svgCardScrolls(frame, widthDp = 300f, viewportDp = 800f))
    }

    @Test fun missingFrameUsesASquareUntilLayoutReturns() {
        val source = "<svg xmlns='http://www.w3.org/2000/svg'><circle r='4'/></svg>"
        assertNull(svg(source).frame)
        assertEquals(200f, svgCardHeight(null, widthDp = 200f, viewportDp = 800f))
    }

    @Test fun scriptOrExternalReferenceStaysOnTheHtmlPagePath() {
        assertNull(staticSvgDocument("<svg viewBox='0 0 10 10'><script>alert(1)</script></svg>"))
        assertNull(staticSvgDocument("<svg viewBox='0 0 10 10'><image href='https://example.test/a.png'/></svg>"))
        assertNull(staticSvgDocument("<svg viewBox='0 0 10 10'><image src='a.png'/></svg>"))
        assertNull(staticSvgDocument("<svg viewBox='0 0 10 10'><foreignObject><div>x</div></foreignObject></svg>"))
        assertNull(staticSvgDocument("<div><p>not a drawing</p></div>"))
    }

    @Test fun markupNextToTheDrawingKeepsThePagePath() {
        // The page-less renderer keeps the graphic only; losing a heading or a
        // sentence next to it would be silent content loss.
        assertNull(staticSvgDocument("<h1>图</h1><svg viewBox='0 0 8 8'><rect width='8' height='8'/></svg>"))
        assertNull(staticSvgDocument("说明<svg viewBox='0 0 8 8'><rect width='8' height='8'/></svg>"))
        assertNull(staticSvgDocument("<svg viewBox='0 0 8 8'></svg><svg viewBox='0 0 8 8'></svg>"))
    }

    @Test fun inlineEventHandlerStaysOnTheHtmlPagePath() {
        assertNull(staticSvgDocument("<svg viewBox='0 0 10 10' onload='alert(1)'><rect width='10' height='10'/></svg>"))
        assertNull(staticSvgDocument("<svg viewBox='0 0 10 10'><rect width='10' height='10' onclick='x()'/></svg>"))
    }

    @Test fun sameDocumentReferenceStaysStatic() {
        val source = "<svg viewBox='0 0 8 8'><use href='#icon'/><use xlink:href='#icon'/></svg>"
        assertNotNull(staticSvgDocument(source))
    }

    @Test fun oversizedSourceKeepsTheInlinePageGuard() {
        val big = "<svg viewBox='0 0 8 8'>" + "x".repeat(INLINE_HTML_ACTIVE_LIMIT) + "</svg>"
        assertTrue(big.length > INLINE_HTML_ACTIVE_LIMIT)
        assertNull(staticSvgDocument(big))
    }

    @Test fun previewDocumentContainsTheGraphicAndBlocksNetwork() {
        val source = """<svg viewBox="0 0 10 10"><rect id="box" width="10" height="10"/></svg>"""
        val doc = Jsoup.parse(svgPreviewDocument(svg(source), fit = true))
        assertNotNull(doc.selectFirst("svg rect#box"))
        val csp = doc.selectFirst("meta[http-equiv=Content-Security-Policy]")!!.attr("content")
        assertTrue(csp.contains("default-src 'none'"))
        assertTrue(csp.contains("connect-src 'none'"))
        assertFalse(csp.contains("script-src"))
        assertTrue(previewStyles(source, fit = true).contains("height:100%"))
        // Scrolling cards keep the full frame width so nothing is cropped sideways.
        assertTrue(previewStyles(source, fit = false).contains("width:100%;height:auto"))
    }

    @Test fun pageStyleOutsideTheGraphicSurvivesThePreview() {
        val source = "<html><head><style>.cls-1{fill:#fff}</style></head>" +
            "<body><svg viewBox='0 0 8 8'><rect class='cls-1' width='8' height='8'/></svg></body></html>"
        assertTrue(previewStyles(source, fit = true).contains(".cls-1{fill:#fff}"))
    }

    @Test fun sizeScriptReadsTheRootOnce() {
        assertTrue(SVG_SIZE_JS.contains("getBBox"))
        assertFalse(SVG_SIZE_JS.contains("querySelectorAll"))
    }

    @Test fun fencedStaticSvgIsStillAnHtmlBlock() {
        val block = splitMarkdownBlocks("```html\n<svg viewBox='0 0 8 8'></svg>\n```").single() as MdBlock.Html
        assertTrue(block.showPreview)
        assertNotNull(staticSvgDocument(block.source))
    }
}
