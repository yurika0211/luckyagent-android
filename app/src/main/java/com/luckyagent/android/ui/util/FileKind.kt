package com.luckyagent.android.ui.util

/** Display kind for a non-media file. Download routing still uses document. */
enum class FileVisualKind {
    Pdf,
    Archive,
    Code,
    Spreadsheet,
    Document,
    Unknown,
}

private val archiveExtensions = setOf("zip", "7z", "tar", "gz", "tgz", "bz2", "xz", "rar")
private val codeExtensions = setOf(
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "c", "h", "cc", "cpp", "hpp",
    "cs", "rb", "php", "swift", "sh", "bash", "zsh", "sql", "json", "yaml", "yml", "toml", "xml",
    "html", "css", "scss",
)
private val spreadsheetExtensions = setOf("xls", "xlsx", "csv", "ods", "tsv")
private val documentExtensions = setOf("doc", "docx", "txt", "md", "markdown", "rtf", "odt", "pdf")

fun fileExtension(fileName: String?): String {
    val name = fileName.orEmpty().substringAfterLast('/').substringAfterLast('\\')
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex - 1) return ""
    return name.substring(dot + 1).lowercase()
}

fun fileVisualKind(fileName: String?, mimeType: String? = null): FileVisualKind {
    val mime = mimeType.orEmpty().lowercase().substringBefore(';').trim()
    val extension = fileExtension(fileName)
    return when {
        extension == "pdf" || mime == "application/pdf" -> FileVisualKind.Pdf
        extension in archiveExtensions || mime.contains("zip") || mime.contains("compressed") ||
            mime.contains("x-tar") || mime.contains("gzip") || mime.contains("x-7z") -> FileVisualKind.Archive
        extension in spreadsheetExtensions || mime.contains("spreadsheet") || mime.contains("excel") ||
            mime == "text/csv" -> FileVisualKind.Spreadsheet
        extension in codeExtensions || mime.contains("javascript") || mime.contains("typescript") ||
            mime == "text/x-python" || mime == "text/x-go" || mime == "text/x-rust" ||
            mime == "application/json" || mime == "application/xml" -> FileVisualKind.Code
        extension in documentExtensions || mime.startsWith("text/") || mime.contains("word") ||
            mime.contains("document") -> FileVisualKind.Document
        else -> FileVisualKind.Unknown
    }
}

fun fileVisualLabel(kind: FileVisualKind): String = when (kind) {
    FileVisualKind.Pdf -> "PDF"
    FileVisualKind.Archive -> "压缩包"
    FileVisualKind.Code -> "代码"
    FileVisualKind.Spreadsheet -> "表格"
    FileVisualKind.Document -> "文档"
    FileVisualKind.Unknown -> "文件"
}

fun fileVisualLabel(fileName: String?, mimeType: String? = null): String =
    fileVisualLabel(fileVisualKind(fileName, mimeType))
