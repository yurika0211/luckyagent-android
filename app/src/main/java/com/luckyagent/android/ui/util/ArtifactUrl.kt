package com.luckyagent.android.ui.util

import java.net.URLEncoder

/** Validate before URL encoding; downstream decoders must not change the path's meaning. */
internal fun validatedArtifactPath(path: String): String? {
    if (path.any { it <= '\u001f' || it == '\\' } ||
        Regex("%(?:25|2e|2f|5c|00)", RegexOption.IGNORE_CASE).containsMatchIn(path)) return null
    val segments = path.split('/')
    if (segments.size < 2 || segments.first() !in setOf("workspace", "uploads", "cwd")) return null
    return path.takeIf { segments.none { it.isBlank() || it.trimEnd(' ').all { c -> c == '.' } } }
}

/**
 * Same gate as a successful download enqueue: an http(s) URL, a relative URL
 * that [apiBase] can turn into one, or a [filePath] that [artifactDownloadUrl] accepts.
 * Non-http schemes (ftp, file, content) and paths the artifact helper rejects stay hidden.
 */
internal fun canDownloadAttachment(fileUrl: String?, filePath: String?, apiBase: String): Boolean {
    val rawUrl = fileUrl?.trim().orEmpty()
    if (rawUrl.isNotEmpty()) {
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) return true
        val scheme = rawUrl.substringBefore(':', "").lowercase()
        val hasScheme = scheme.isNotEmpty() && scheme.all { it.isLetter() } && rawUrl.contains(':')
        if (!hasScheme && apiBase.isNotBlank()) return true
        return false
    }
    val path = filePath?.trim().orEmpty()
    if (path.isEmpty()) return false
    return artifactDownloadUrl(path, apiBase) != null
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
        // MEDIA directives can point at the service checkout (or another
        // explicitly allowed server root). Keep the absolute reference in the
        // query; the server performs the authoritative root and symlink check.
        normalized.startsWith("/") || Regex("^[A-Za-z]:/").matches(normalized) || normalized.startsWith("~/") -> normalized
        else -> return null
    }
    val safePath = if (relative.startsWith("/") || Regex("^[A-Za-z]:/").matches(relative) || relative.startsWith("~/")) {
        if (relative.any { it <= '\u001f' } || Regex("%(?:25|2e|2f|5c|00)", RegexOption.IGNORE_CASE).containsMatchIn(relative)) return null
        relative
    } else {
        validatedArtifactPath(relative) ?: return null
    }
    return apiBase.trimEnd('/') + "/api/v1/artifacts?path=" + URLEncoder.encode(safePath, Charsets.UTF_8.name())
}
