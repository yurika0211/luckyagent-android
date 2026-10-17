package com.luckyagent.android.ui.components

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest

class HtmlPreviewTest {
    @Test fun artifactResourcesResolveBesideHtmlInsteadOfBesideApiRoute() {
        val documentUrl = "https://example.test/api/v1/artifacts?path=workspace%2Freports%2Fpage.html"
        val document = Jsoup.parse(htmlPreviewDocument("<img src='images/chart.png'>", documentUrl))
        val resourceUrl = document.selectFirst("img")!!.absUrl("src")
        val resolved = htmlResourceUrl(resourceUrl, documentUrl)!!.toHttpUrl()
        assertEquals("workspace/reports/images/chart.png", resolved.queryParameter("path"))
        assertEquals("example.test", resolved.host)
    }

    @Test fun previewRetainsLayoutAndScriptAndResolvesResources() {
        val source = "<!doctype html><html><head><style>.grid{display:grid}</style></head><body><div class='grid'>x</div><script>window.answer=42</script><img src='photo.png'></body></html>"
        val document = Jsoup.parse(htmlPreviewDocument(source, "https://example.test/reports/page.html"))
        assertEquals(HTML_PREVIEW_ORIGIN + "document/reports/photo.png", document.selectFirst("img")!!.absUrl("src"))
        assertTrue(document.selectFirst("style")!!.data().contains("display:grid"))
        assertTrue(document.selectFirst("script")!!.data().contains("answer=42"))
        assertTrue(document.selectFirst("meta[name=viewport]") != null)
        assertEquals("x", document.selectFirst(".grid")!!.text())
        val csp = document.selectFirst("meta[http-equiv=Content-Security-Policy]")!!.attr("content")
        assertTrue(csp.contains("connect-src 'none'"))
        assertTrue(csp.contains("worker-src 'none'"))
        assertFalse(csp.contains("unsafe-eval"))
        assertTrue(csp.contains("base-uri 'self'"))
        assertFalse(csp.contains("base-uri 'none'"))
    }

    @Test fun absoluteThirdPartyUrlsAreNotProxied() {
        val documentUrl = "https://example.test/api/v1/artifacts?path=workspace%2Freports%2Fpage.html"
        assertNull(htmlResourceUrl("https://evil.test/steal", documentUrl))
        assertNull(htmlResourceUrl("http://127.0.0.1:9/secret", documentUrl))
        assertNull(htmlResourceUrl("https://example.test/api/v1/sessions", documentUrl))
    }

    @Test fun onlyArtifactRoutesCountAsCredentialTargets() {
        val api = "https://example.test/"
        assertTrue(isArtifactResourceUrl("https://example.test/api/v1/artifacts?path=workspace%2Fpage.html", api))
        assertFalse(isArtifactResourceUrl("https://example.test/api/v1/sessions", api))
        assertFalse(isArtifactResourceUrl("https://evil.test/api/v1/artifacts?path=workspace%2Fpage.html", api))
    }

    @Test fun documentRedirectOffOriginIsBlockedBeforeTheNextRequest() {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.start(); second.start()
            first.enqueue(MockResponse().setResponseCode(302).addHeader("Location", second.url("/page.html").toString()))
            second.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody("<p>ok</p>"))
            val loader = HtmlResourceLoader(mapOf("Authorization" to "Bearer sample", "X-API-Key" to "sample"), first.url("/").toString())
            assertBlocked { loader.fetch(first.url("/page.html").toString()) }
            assertNull(first.takeRequest().getHeader("Authorization"))
            assertEquals(0, second.requestCount)
            loader.close()
        } }
    }

    @Test fun artifactRedirectOffOriginIsBlockedBeforeTheNextRequest() {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.start(); second.start()
            val artifact = first.url("/api/v1/artifacts?path=workspace%2Fpage.html")
            first.enqueue(MockResponse().setResponseCode(302).addHeader("Location", second.url("/leaked").toString()))
            second.enqueue(MockResponse().setBody("secret"))
            val loader = HtmlResourceLoader(mapOf("X-API-Key" to "sample"), first.url("/").toString())
            assertBlocked { loader.fetch(artifact.toString()) }
            assertEquals("sample", first.takeRequest().getHeader("X-API-Key"))
            assertEquals(0, second.requestCount)
            loader.close()
        } }
    }

    @Test fun relativeRedirectWithinArtifactOriginKeepsCredentials() {
        MockWebServer().use { server ->
            server.start()
            val start = server.url("/api/v1/artifacts?path=workspace%2Fpage.html")
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/api/v1/artifacts?path=workspace%2Fassets%2Fpage.html"))
            server.enqueue(MockResponse().setBody("ok"))
            val loader = HtmlResourceLoader(mapOf("X-API-Key" to "sample"), server.url("/").toString())
            val result = loader.fetch(start.toString())
            assertTrue(result.url.contains("assets/page.html") || result.url.contains("assets%2Fpage.html"))
            assertEquals("sample", server.takeRequest().getHeader("X-API-Key"))
            assertEquals("sample", server.takeRequest().getHeader("X-API-Key"))
        }
    }

    @Test fun oversizedDocumentFailsWithDownloadHint() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("long content"))
            try {
                HtmlResourceLoader(emptyMap(), server.url("/").toString()).fetch(server.url("/page").toString(), limit = 4)
                fail("Expected size limit")
            } catch (e: IOException) { assertTrue(e.message.orEmpty().contains("下载")) }
        }
    }

    @Test fun nonArtifactDocumentBaseKeepsDirectoryForRelativeAssets() {
        val base = htmlResourceBase("https://example.test/reports/page.html")
        assertEquals(HTML_PREVIEW_ORIGIN + "document/reports/page.html", base)
        // Still must not proxy absolute third-party URLs for that document.
        assertNull(htmlResourceUrl("https://cdn.example/x.css", "https://example.test/reports/page.html"))
    }

    @Test fun ordinaryDocumentAssetsResolveAndStayInTheDocumentDirectory() {
        val url = "https://example.test/reports/page.html"
        val doc = Jsoup.parse(htmlPreviewDocument("<link href='assets/style.css?v=2'><img src='photo.png'>", url))
        assertEquals("https://example.test/reports/assets/style.css?v=2",
            htmlResourceUrl(doc.selectFirst("link")!!.absUrl("href"), url))
        assertEquals("https://example.test/reports/photo.png", htmlResourceUrl(doc.selectFirst("img")!!.absUrl("src"), url))
        assertNull(htmlResourceUrl(HTML_PREVIEW_ORIGIN + "document/api/v1/sessions", url))
        assertNull(htmlResourceUrl(HTML_PREVIEW_ORIGIN + "document/reports/../private.png", url))
    }

    @Test fun normalizedTraversalAndDirectSiblingArtifactDirectoriesAreBlocked() {
        val url = "https://example.test/api/v1/artifacts?path=workspace%2Freports%2Fpage.html"
        listOf(
            "artifact/workspace/reports/../../uploads/secret.png",
            "artifact/workspace/reports/%2e%2e/private.png",
            "artifact/uploads/secret.png",
            "artifact/workspace/private.png",
            "artifact/workspace/reports/a%2f..%2f..%2fprivate.png",
            "artifact/workspace/reports/a%5c..%5cprivate.png",
        ).forEach { assertNull(it, htmlResourceUrl(HTML_PREVIEW_ORIGIN + it, url)) }
        assertNotNull(htmlResourceUrl(HTML_PREVIEW_ORIGIN + "artifact/workspace/reports/assets/photo.png", url))
    }

    @Test fun previewOriginRequiresHttpsAndTheDefaultPortAndNoUserInfo() {
        val url = "https://example.test/api/v1/artifacts?path=workspace%2Freports%2Fpage.html"
        val path = "/artifact/workspace/reports/photo.png"
        assertNull(htmlResourceUrl("http://html-preview.luckyagent.invalid$path", url))
        assertNull(htmlResourceUrl("https://html-preview.luckyagent.invalid:444$path", url))
        assertNull(htmlResourceUrl("https://user@html-preview.luckyagent.invalid$path", url))
        assertNull(htmlResourceUrl(url, url))
    }

    @Test fun genericDocumentsCannotAcquireArtifactCredentialsThroughRedirects() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302)
                .addHeader("Location", "/api/v1/artifacts?path=workspace%2Fsecret.html"))
            HtmlResourceLoader(mapOf("X-API-Key" to "sample"), server.url("/").toString()).use { loader ->
                assertBlocked { loader.fetch(server.url("/page.html").toString()) }
            }
            assertEquals(1, server.requestCount)
            assertNull(server.takeRequest().getHeader("X-API-Key"))
        }
    }

    @Test fun artifactRedirectToAnotherDirectoryOrApiRouteIsBlocked() {
        listOf("/api/v1/artifacts?path=uploads%2Fsecret.png", "/api/v1/sessions").forEach { location ->
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", location))
                HtmlResourceLoader(mapOf("X-API-Key" to "sample"), server.url("/").toString()).use { loader ->
                    assertBlocked { loader.fetch(server.url("/api/v1/artifacts?path=workspace%2Freports%2Fpage.html").toString()) }
                }
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun requestBudgetCoversRedirectHops() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next.html"))
            HtmlResourceLoader(emptyMap(), server.url("/").toString(), HtmlResourceBudget(maxRequests = 1)).use { loader ->
                assertBlocked { loader.fetch(server.url("/page.html").toString()) }
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun totalResourceBudgetStaysExhaustedAfterAnOversizedResponse() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("1234"))
            server.enqueue(MockResponse().setBody("5678"))
            HtmlResourceLoader(emptyMap(), server.url("/").toString(), HtmlResourceBudget(maxBytes = 6)).use { loader ->
                loader.fetch(server.url("/one.png").toString())
                assertBlocked { loader.fetch(server.url("/two.png").toString()) }
                assertBlocked { loader.fetch(server.url("/three.png").toString()) }
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun previewResourceScopeCannotBeWidenedByUsingAnAssetAsTheRedirectBase() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/outside.png"))
            val document = server.url("/reports/page.html").toString()
            HtmlResourceLoader(emptyMap(), server.url("/").toString()).use { loader ->
                assertBlocked { loader.fetch(server.url("/reports/photo.png").toString(), scope = HtmlResourceScope(document)) }
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun resourceConcurrencyIsBoundedAcrossParallelFetches() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val workers = Executors.newFixedThreadPool(3)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val count = active.incrementAndGet()
                    maxActive.updateAndGet { maxOf(it, count) }
                    entered.countDown()
                    try { release.await(5, TimeUnit.SECONDS) }
                    finally { active.decrementAndGet() }
                    return MockResponse().setBody("ok")
                }
            }
            server.start()
            HtmlResourceLoader(emptyMap(), server.url("/").toString(), HtmlResourceBudget(maxConcurrent = 2)).use { loader ->
                try {
                    val calls = (1..3).map { i -> workers.submit<String> { String(loader.fetch(server.url("/$i.png").toString()).bytes) } }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    assertEquals(2, server.requestCount)
                    release.countDown()
                    calls.forEach { assertEquals("ok", it.get(5, TimeUnit.SECONDS)) }
                    assertEquals(2, maxActive.get())
                    assertEquals(3, server.requestCount)
                } finally { release.countDown(); workers.shutdownNow() }
            }
        }
    }

    @Test fun duplicateArtifactPathParametersCannotWidenTheCredentialScope() {
        val url = "https://example.test/api/v1/artifacts?path=workspace%2Freports%2Fpage.html&path=uploads%2Fsecret.html"
        assertFalse(isArtifactResourceUrl(url, "https://example.test"))
        assertFalse(HtmlResourceScope(url).allows(url))
    }

    private fun assertBlocked(action: () -> Unit) {
        try { action(); fail("Expected the resource policy or budget to block loading") }
        catch (_: IOException) { }
    }
}
