package com.luckyagent.android.ui.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ArtifactUrlTest {
    @Test fun validLocalPathsKeepSpacesUnicodeAndWindowsSeparators() {
        val url = artifactDownloadUrl("C:\\Users\\user\\.luckyagent\\workspace\\报告 1.html", "https://example.test/prefix/")!!
        assertEquals("/prefix/api/v1/artifacts", url.toHttpUrl().encodedPath)
        assertEquals("workspace/报告 1.html", url.toHttpUrl().queryParameter("path"))
    }

    @Test fun traversalIsRejectedBeforeQueryEncoding() {
        listOf("../uploads/secret", "reports/../../secret", "./report.html", ".. /secret", ".../secret", "%2e%2e/secret", "%252e%252e/secret",
            "reports/%2f../secret", "reports/%5c../secret", "reports/\u0000secret").forEach { path ->
            assertNull(path, artifactDownloadUrl("~/.luckyagent/workspace/$path", "https://example.test"))
        }
    }

    @Test fun artifactNamespaceAndFileNameAreRequired() {
        assertNull(validatedArtifactPath("private/file.txt"))
        assertNull(validatedArtifactPath("/workspace/file.txt"))
        assertNull(validatedArtifactPath("workspace/"))
        assertNull(validatedArtifactPath("workspace//file.txt"))
        assertEquals("uploads/file.txt", validatedArtifactPath("uploads/file.txt"))
        assertEquals("cwd/build/app.apk", validatedArtifactPath("cwd/build/app.apk"))
    }

    @Test fun pathsOutsideArtifactRootsAreRejected() {
        // These used to be forwarded verbatim and the server answered 403
        // unsupported artifact root. Only workspace and uploads are deliverable.
        listOf(
            "/srv/luckyagent/build/app-debug.apk",
            "/tmp/x.png",
            "~/Downloads/report.pdf",
            "C:/Users/user/report.pdf",
        ).forEach { path ->
            assertNull(path, artifactDownloadUrl(path, "https://example.test"))
        }
    }

    @Test fun bodyTextIsNotScrapedIntoCards() {
        val reply = """
            https://www.live2d.com/zh-CHS/cubism/download/editor/
            https://docs.live2d.com/zh-CHS/cubism-editor-tutorials/top/
            https://www.youtube.com/watch?v=XEJvtNV1aK0
            MEDIA:/tmp/x.png
            MEDIA:~/.luckyagent/workspace/foo.png
            MEDIA: ~/.luckyagent/workspace/foo.png
        """.trimIndent()
        assertTrue(chatMediaFromAssistantText(reply).isEmpty())
    }

    @Test fun attachmentJsonStillBecomesCards() {
        val media = chatMediaFromAttachmentJson(
            """{"attachments":[{"file_name":"report.pdf","file_path":"/home/x/.luckyagent/workspace/deliverables/ab12.pdf","file_url":"/api/v1/artifacts?path=workspace/deliverables/ab12.pdf","mime_type":"application/pdf","file_size":9}]}""",
        )
        assertEquals(1, media.size)
        assertEquals("report.pdf", media[0].descriptor.fileName)
        assertEquals("/api/v1/artifacts?path=workspace/deliverables/ab12.pdf", media[0].descriptor.fileUrl)
    }

    @Test fun mediaPrefixIsStrippedBeforeTheArtifactRoute() {
        val apiBase = "https://example.test"
        val absolute = "/home/x/.luckyagent/workspace/a.png"
        val tilde = "~/.luckyagent/uploads/a.bin"
        assertEquals(artifactDownloadUrl(absolute, apiBase), artifactDownloadUrl("MEDIA:$absolute", apiBase))
        assertEquals(artifactDownloadUrl(absolute, apiBase), artifactDownloadUrl("media: $absolute", apiBase))
        assertEquals(cleanArtifactPath("MEDIA:$absolute"), absolute)
        assertEquals(artifactDownloadUrl(tilde, apiBase), artifactDownloadUrl("MEDIA: $tilde", apiBase))
        assertEquals(cleanArtifactPath("MEDIA: $tilde"), tilde)
        assertEquals("workspace/a.png", artifactDownloadUrl("MEDIA:$absolute", apiBase)!!.toHttpUrl().queryParameter("path"))
        assertEquals("uploads/a.bin", artifactDownloadUrl("MEDIA: $tilde", apiBase)!!.toHttpUrl().queryParameter("path"))
    }

    @Test fun attachmentUrlResolveKeepsHttpAndDropsOtherSchemes() {
        val apiBase = "http://10.0.2.2:8080"
        assertEquals(
            "http://10.0.2.2:8080/api/v1/artifacts?path=uploads/a.bin",
            resolveAttachmentDownloadUrl("/api/v1/artifacts?path=uploads/a.bin", apiBase),
        )
        assertEquals(
            "https://example.test/api/v1/artifacts?path=uploads/a.bin",
            resolveAttachmentDownloadUrl("https://example.test/api/v1/artifacts?path=uploads/a.bin", "http://ignored"),
        )
        assertNull(resolveAttachmentDownloadUrl("ftp://files.example/a.bin", apiBase))
        assertNull(resolveAttachmentDownloadUrl("file:///tmp/a.bin", apiBase))
        assertFalse(canDownloadAttachment("ftp://files.example/a.bin", null, apiBase))
        assertTrue(canDownloadAttachment("/api/v1/artifacts?path=uploads/a.bin", null, apiBase))
    }
}
