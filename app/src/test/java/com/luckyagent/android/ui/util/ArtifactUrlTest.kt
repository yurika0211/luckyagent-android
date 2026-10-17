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
    }
}
