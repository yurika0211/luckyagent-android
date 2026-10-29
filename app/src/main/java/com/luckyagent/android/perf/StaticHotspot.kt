package com.luckyagent.android.perf

enum class HotspotKind {
    NESTED_LOOP,
    STRING_CONCAT_IN_LOOP,
    UNBOUNDED_COLLECTION,
    REPEATED_PARSE,
}

data class StaticHotspot(
    val file: String,
    val line: Int,
    val kind: HotspotKind,
    val evidence: String,
)

/**
 * Line-oriented scan of Kotlin sources. It does not compile the project; it
 * flags shapes that already showed up as chat jank so a PR can be checked
 * without a device trace.
 */
object StaticHotspotScanner {
    private val loopStart = Regex("""\b(for|while|forEach|forEachIndexed|map|mapIndexed)\b""")
    private val stringBuild = Regex("""\+=|\bappend\(""")
    private val unbounded = Regex("""mutableListOf<|mutableMapOf<|HashMap<|ArrayList<""")
    private val repeatedParse = Regex("""splitMarkdownBlocks|parseHtml|richTextNodes|Json\s*\{""")

    fun scan(file: String, source: String): List<StaticHotspot> {
        val lines = source.lines()
        val hits = ArrayList<StaticHotspot>()
        var braceDepth = 0
        var loopDepth = 0
        val loopBraces = ArrayDeque<Int>()
        lines.forEachIndexed { index, raw ->
            val line = stripComment(raw)
            val lineNumber = index + 1
            if (loopStart.containsMatchIn(line)) {
                loopDepth += 1
                loopBraces.addLast(braceDepth + line.count { it == '{' })
            }
            if (loopDepth >= 2) {
                hits += StaticHotspot(file, lineNumber, HotspotKind.NESTED_LOOP, line.trim())
            }
            if (loopDepth >= 1 && stringBuild.containsMatchIn(line)) {
                hits += StaticHotspot(file, lineNumber, HotspotKind.STRING_CONCAT_IN_LOOP, line.trim())
            }
            if (unbounded.containsMatchIn(line) && !line.contains("capacity") && !line.contains("MAX_")) {
                hits += StaticHotspot(file, lineNumber, HotspotKind.UNBOUNDED_COLLECTION, line.trim())
            }
            if (repeatedParse.containsMatchIn(line) && loopDepth >= 1) {
                hits += StaticHotspot(file, lineNumber, HotspotKind.REPEATED_PARSE, line.trim())
            }
            braceDepth += line.count { it == '{' } - line.count { it == '}' }
            while (loopBraces.isNotEmpty() && braceDepth < loopBraces.last()) {
                loopBraces.removeLast()
                loopDepth -= 1
            }
        }
        return hits
    }

    fun scanAll(files: Map<String, String>): List<StaticHotspot> =
        files.flatMap { (name, source) -> scan(name, source) }
            .sortedWith(compareBy({ it.file }, { it.line }, { it.kind }))

    private fun stripComment(line: String): String {
        val marker = line.indexOf("//")
        return if (marker < 0) line else line.substring(0, marker)
    }
}
