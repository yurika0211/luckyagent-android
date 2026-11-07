package com.luckyagent.android.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One sidebar reading. Missing server fields stay as an explicit dash. */
internal data class HostResourceLines(
    val lines: List<String>,
)

private const val MISSING = "—"

/**
 * Formats LuckyAgent process RSS, CPU, ~/.luckyagent disk, and Go heap.
 * Absent keys stay as 「—」. Heap is never shown as RSS.
 */
/** Whole milliseconds. A missing, negative, or non-finite sample is a dash. */
internal fun formatRtt(rttMs: Long?): String {
    if (rttMs == null || rttMs < 0L) return "延迟 $MISSING"
    return "延迟 ${rttMs}ms"
}

internal fun formatHostResources(system: JsonObject?, rttMs: Long? = null): HostResourceLines {
    val latency = formatRtt(if (system == null) null else rttMs)
    if (system == null) {
        return HostResourceLines(listOf("主机资源暂无", "RSS $MISSING · CPU $MISSING · $latency", "磁盘 $MISSING"))
    }
    val rss = firstPresent(system, "process_rss_bytes", "rss_bytes", "rss_mb", "process_rss_mb")
    val cpu = firstPresent(system, "process_cpu_percent", "cpu_percent")
    val disk = firstPresent(system, "luckyagent_dir_bytes", "luckyagent_bytes")
    val heap = firstPresent(system, "heap_alloc_mb", "heap_alloc_bytes")
    val interval = firstPresent(system, "process_cpu_sample_ms", "cpu_sample_ms", "sample_interval_ms")
    val rssText = when {
        rss == null -> "RSS $MISSING"
        rss.key.endsWith("_bytes") -> "RSS ${formatBytes(rss.value)}"
        else -> "RSS ${formatCount(rss.value, "MB")}"
    }
    val cpuText = if (cpu == null) {
        "CPU $MISSING"
    } else {
        val sample = interval?.let { " / ${formatDuration(it.value)}" }.orEmpty()
        "CPU ${trimNumber(cpu.value)}%$sample"
    }
    val diskText = if (disk == null) "磁盘 $MISSING" else "磁盘 ${formatBytes(disk.value)}"
    val heapText = when {
        heap == null -> null
        heap.key.endsWith("_bytes") -> "Go 堆 ${formatBytes(heap.value)}"
        else -> "Go 堆 ${formatCount(heap.value, "MB")}"
    }
    val lines = mutableListOf("$rssText · $cpuText · $latency", diskText)
    if (heapText != null) lines += heapText
    if (rss == null && cpu == null && disk == null && heap == null) {
        return HostResourceLines(listOf("主机资源暂无", "RSS $MISSING · CPU $MISSING · $latency", "磁盘 $MISSING"))
    }
    return HostResourceLines(lines)
}

private data class Reading(val key: String, val value: Double)

private fun firstPresent(system: JsonObject, vararg keys: String): Reading? {
    for (key in keys) {
        val raw = system[key] ?: continue
        val number = runCatching { raw.jsonPrimitive.doubleOrNull }.getOrNull()
            ?: runCatching { raw.jsonPrimitive.longOrNull?.toDouble() }.getOrNull()
            ?: runCatching { raw.jsonPrimitive.contentOrNull?.toDoubleOrNull() }.getOrNull()
            ?: continue
        if (!number.isFinite()) continue
        return Reading(key, number)
    }
    return null
}

internal fun hostResourcesFromDetail(root: JsonObject?, rttMs: Long? = null): HostResourceLines {
    val system = root?.get("system")?.let { runCatching { it.jsonObject }.getOrNull() }
    return formatHostResources(system, rttMs)
}

private fun formatBytes(value: Double): String {
    val bytes = value.coerceAtLeast(0.0)
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes
    var index = 0
    while (size >= 1024.0 && index < units.lastIndex) {
        size /= 1024.0
        index++
    }
    val text = if (index == 0) size.toLong().toString() else trimNumber(size)
    return "$text ${units[index]}"
}

private fun formatCount(value: Double, unit: String): String = "${trimNumber(value)} $unit"

private fun formatDuration(ms: Double): String {
    val rounded = ms.toLong().coerceAtLeast(0)
    return if (rounded >= 1000 && rounded % 1000 == 0L) "${rounded / 1000}s" else "${rounded}ms"
}

private fun trimNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return text
}
