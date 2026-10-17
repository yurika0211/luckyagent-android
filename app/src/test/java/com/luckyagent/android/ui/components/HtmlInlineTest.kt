package com.luckyagent.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.*
import org.junit.Test

class HtmlInlineTest {
    private fun html(source: String) = htmlAnnotatedText(parseHtml(source).orEmpty(), baseUrl = "https://example.test/docs/")

    @Test fun preservesHtmlInsideInlineCode() {
        assertEquals("<b>x</b>", richInlineText("`<b>x</b>`").text)
    }
    @Test fun escapedMarkupIsDecodedExactlyOnce() {
        assertEquals("<b>x</b>", html("<p>&lt;b&gt;x&lt;/b&gt;</p>").text)
        assertEquals("<b>x</b>", richInlineText("&lt;b&gt;x&lt;/b&gt;").text)
        assertEquals("&lt;b&gt;", richInlineText("`&lt;b&gt;`").text)
    }
    @Test fun nestedBoldItalicComposeWithoutMarkers() {
        val text = richInlineText("<b><i>x</i></b>")
        assertEquals("x", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }
    @Test fun mixedMarkdownAndHtmlRetainsNestedStyles() {
        val text = richInlineText("<span style='color:red'>**bold** and *italic*</span>")
        assertEquals("bold and italic", text.text)
        assertTrue(text.spanStyles.any { it.item.color == Color.Red })
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }
    @Test fun lineBreaksAndParagraphsKeepTheirBoundaries() {
        assertEquals("a\nb", html("<p>a<br>b</p>").text)
        assertEquals("intro\nfirst\nsecond\noutro", html("intro<p>first</p><p>second</p>outro").text)
    }
    @Test fun quotedAngleBracketAndParenthesesStayInsideUrl() {
        val text = html("<a href='https://example.test/a(b)?q=a&gt;b'>link</a>")
        assertEquals("link", text.text)
        assertEquals("https://example.test/a(b)?q=a%3Eb", text.getStringAnnotations("url", 0, text.length).singleOrNull()?.item)
    }
    @Test fun literalGreaterThanInAttributeDoesNotEndTag() {
        val element = parseHtml("<a href='https://example.test/?q=a>b'>link</a>")!!.single() as HtmlNode.Element
        assertEquals("https://example.test/?q=a>b", element.href)
        assertEquals("link", htmlAnnotatedText(listOf(element)).text)
    }
    @Test fun markdownLinksSupportBalancedParentheses() {
        val text = richInlineText("[link](https://example.test/a(b))")
        assertEquals("link", text.text)
        assertEquals("https://example.test/a(b)", text.getStringAnnotations("url", 0, text.length).single().item)
    }
    @Test fun relativeUrlsResolveAgainstDocument() {
        val text = html("<a href='../help#part'>help</a>")
        assertEquals("https://example.test/help#part", text.getStringAnnotations("url", 0, text.length).single().item)
    }
    @Test fun nonWebLinksRetainTextWithoutAction() {
        val text = html("<a href='javascript:alert(1)'>x</a>")
        assertEquals("x", text.text)
        assertTrue(text.getStringAnnotations("url", 0, text.length).isEmpty())
    }
    @Test fun entityNamesDecimalAndHexAreSupported() {
        assertEquals("A\u00a0B 你 — 你 &", html("<p>A&nbsp;B &#x4F60; &mdash; &#20320; &amp;</p>").text)
        assertEquals("A\u00a0B", richInlineText("A&nbsp;B").text)
    }
    @Test fun unfinishedFragmentsAreRepairedForStreaming() {
        assertEquals("bold", html("<b>bold").text)
    }
    @Test fun unknownWrapperKeepsSupportedChildren() {
        assertEquals("bold underline", richInlineText("<b>bold</b> <u>underline</u>").text)
        assertTrue(richInlineText("<u>underline</u>").spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
    }
    @Test fun scriptAndStyleNeverAppearInNativeText() {
        assertEquals("visible", html("<script>alert(1)</script><style>p{color:red}</style><p>visible</p>").text)
    }
    @Test fun excessiveNestingPreservesText() {
        assertEquals("x", html("<b>".repeat(100) + "x" + "</b>".repeat(100)).text)
    }
    @Test fun detailsRetainsDefaultOpenAndNestedSummary() {
        val node = parseHtml("<details open><summary>more</summary><details><summary>inner</summary>x</details></details>")!!.single() as HtmlNode.Element
        assertTrue("open" in node.attributes)
        assertEquals("details", (node.children.last() as HtmlNode.Element).tag)
    }
    @Test fun semanticAndCssColorsCompose() {
        val node = parseHtml("<span data-color='warning' style='color:#123;background-color:rgba(1,2,3,0.5)'>x</span>")!!.single() as HtmlNode.Element
        assertEquals(HtmlColor.WARNING, node.color)
        assertEquals(Color(0xFF112233), htmlSpanStyle(node, emptyMap()).color)
        assertEquals(Color(1, 2, 3, 127), htmlSpanStyle(node, emptyMap()).background)
    }
    @Test fun htmlCodeDoesNotInterpretMarkdownOrMath() {
        val text = html("<code>&lt;b&gt; **literal** ${'$'}x${'$'}</code>")
        assertEquals("<b> **literal** ${'$'}x${'$'}", text.text)
        assertTrue(text.getStringAnnotations("math", 0, text.length).isEmpty())
    }
    @Test fun mathOutsideCodeKeepsItsNativeAnnotation() {
        val text = richInlineText("area ${'$'}x^2${'$'} and `${'$'}literal${'$'}`")
        assertEquals(1, text.getStringAnnotations("math", 0, text.length).size)
        assertTrue(text.text.contains("${'$'}literal${'$'}"))
    }
    @Test fun originCheckIncludesSchemeAndEffectivePort() {
        assertTrue(sameHtmlOrigin("https://EXAMPLE.test/x", "https://example.test:443/"))
        assertFalse(sameHtmlOrigin("https://example.test.evil/x", "https://example.test/"))
        assertFalse(sameHtmlOrigin("http://example.test/x", "https://example.test/"))
        assertFalse(sameHtmlOrigin("https://example.test:444/x", "https://example.test/"))
    }
    @Test fun mathMultiplicationAndEscapesAreProtectedBeforeMarkdownParsing() {
        val text = richInlineText("${'$'}x*y*z${'$'} and \\${'$'}literal\\${'$'}")
        assertEquals("x*y*z and ${'$'}literal${'$'}", text.text)
        assertEquals(1, text.getStringAnnotations("math", 0, text.length).size)
    }
    @Test fun invalidHugeCssValuesDoNotThrow() {
        assertNull(parseHtmlColor("rgb(999999999999999999,2,3)"))
        val element = parseHtml("<span style='font-size:NaN'>x</span>")!!.single() as HtmlNode.Element
        htmlSpanStyle(element, emptyMap())
    }
    @Test fun preformattedTextRetainsIndentationNewlinesAndDollarSigns() {
        val node = parseHtml("<pre>  &lt;b&gt; ${'$'}x${'$'}\n  line 2\n</pre>")!!.single() as HtmlNode.Element
        assertEquals("  <b> ${'$'}x${'$'}\n  line 2\n", htmlLiteralText(node.children))
    }
    @Test fun detectsDocumentsAndHtmlAttachments() {
        assertTrue(requiresHtmlPage("<!doctype html><html><style>p{}</style></html>"))
        assertTrue(requiresHtmlPage("<div style='display:flex'>x</div>"))
        assertFalse(requiresHtmlPage("<table><tr><td>x</td></tr></table>"))
        assertTrue(isHtmlAttachment("text/html; charset=utf-8", "file"))
        assertTrue(isHtmlAttachment(null, "PAGE.HTML"))
        assertFalse(isHtmlAttachment("text/plain", "file.txt"))
    }
}
