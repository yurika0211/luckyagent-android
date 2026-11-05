package com.luckyagent.android.ui

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostResourcesTest {
    @Test
    fun missingFieldsStayAsPlaceholders() {
        val lines = formatHostResources(null).lines
        assertEquals(
            listOf("主机资源暂无", "RSS — · CPU —", "磁盘 —"),
            lines,
        )
        assertFalse(lines.any { it.contains("MB") || it.contains("%") })

        val empty = formatHostResources(buildJsonObject { }).lines
        assertEquals(listOf("主机资源暂无", "RSS — · CPU —", "磁盘 —"), empty)
    }

    @Test
    fun heapAloneDoesNotInventRssCpuOrDisk() {
        val system = buildJsonObject {
            put("heap_alloc_mb", 12.5)
            put("heap_sys_mb", 40.0)
            put("num_cpu", 8)
            put("num_goroutine", 12)
        }
        val lines = formatHostResources(system).lines
        assertEquals(
            listOf("RSS — · CPU —", "磁盘 —", "Go 堆 12.5 MB"),
            lines,
        )
        assertFalse(lines.any { it.startsWith("RSS ") && !it.contains("—") })
        assertFalse(lines.joinToString(" ").contains("40"))
        assertFalse(lines.joinToString(" ").contains("8"))
    }

    @Test
    fun presentRssCpuAndDiskUseUnits() {
        val system = buildJsonObject {
            put("process_rss_bytes", 1_048_576)
            put("process_cpu_percent", 3.2)
            put("process_cpu_sample_ms", 1000)
            put("luckyagent_dir_bytes", 4096)
            put("heap_alloc_mb", 12.5)
        }
        assertEquals(
            listOf("RSS 1 MB · CPU 3.2% / 1s", "磁盘 4 KB", "Go 堆 12.5 MB"),
            formatHostResources(system).lines,
        )
    }

    @Test
    fun detailRootReadsSystemAndKeepsMissingDisk() {
        val root = buildJsonObject {
            put("system", buildJsonObject {
                put("rss_mb", 80)
                put("cpu_percent", 1)
            })
        }
        val lines = hostResourcesFromDetail(root).lines
        assertEquals(listOf("RSS 80 MB · CPU 1%", "磁盘 —"), lines)
        assertTrue(lines.none { it.contains("主机资源暂无") })
    }
}
