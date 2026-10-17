package com.luckyagent.android.ui.util

import java.net.URLEncoder

/** Validate before URL encoding; downstream decoders must not change the path's meaning. */
internal fun validatedArtifactPath(path: String): String? {
    if (path.any { it <= '\u001f' || it == '\\' } ||
        Regex("%(?:25|2e|2f|5c|00)", RegexOption.IGNORE_CASE).containsMatchIn(path)) return null
    val segments = path.split('/')
    if (segments.size < 2 || segments.first() !in setOf("workspace", "uploads")) return null
    return path.takeIf { segments.none { it.isBlank() || it.trimEnd(' ').all { c -> c == '.' } } }
}

/** Canonical artifact route shared by attachment downloads and previews. */
internal fun artifactDownloadUrl(path: String, apiBase: String): String? {
    if (apiBase.isBlank()) return null
    val normalized = path.replace('\\', '/')
    val relative = when {
        normalized.startsWith("~/.luckyagent/workspace/") -> "workspace/" + normalized.removePrefix("~/.luckyagent/workspace/")
        normalized.startsWith("~/.luckyagent/uploads/") -> "uploads/" + normalized.removePrefix("~/.luckyagent/uploads/")
        normalized.contains("/.luckyagent/workspace/") -> "workspace/" + normalized.substringAfter("/.luckyagent/workspace/")
        normalized.contains("/.luckyagent/uploads/") -> "uploads/" + normalized.substringAfter("/.luckyagent/uploads/")
        else -> return null
    }
    val safePath = validatedArtifactPath(relative) ?: return null
    return apiBase.trimEnd('/') + "/api/v1/artifacts?path=" + URLEncoder.encode(safePath, Charsets.UTF_8.name())
}
