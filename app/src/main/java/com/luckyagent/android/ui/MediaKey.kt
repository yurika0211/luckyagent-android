package com.luckyagent.android.ui

import android.net.Uri

/**
 * Returns a stable key for an attachment value used while merging history.
 *
 * Some attachment identifiers are opaque URIs (for example, `artifact:...`).
 * Query parameters are only available on hierarchical URIs; calling
 * getQueryParameter on an opaque URI throws UnsupportedOperationException.
 */
internal fun canonicalMediaKey(value: String): String {
    val normalized = value.replace('\\', '/')
    val uri = runCatching { Uri.parse(normalized) }.getOrNull()
    uri?.takeIf { it.isHierarchical }?.getQueryParameter("path")?.takeIf { it.isNotBlank() }?.let {
        return "artifact:${it.trimStart('/')}"
    }
    listOf("/.luckyagent/workspace/", "/.luckyagent/uploads/").forEach { marker ->
        if (normalized.contains(marker)) {
            return "artifact:${normalized.substringAfter(marker)}".let {
                if (marker.contains("uploads")) it.replaceFirst("artifact:", "artifact:uploads/")
                else it.replaceFirst("artifact:", "artifact:workspace/")
            }
        }
    }
    return normalized
}
