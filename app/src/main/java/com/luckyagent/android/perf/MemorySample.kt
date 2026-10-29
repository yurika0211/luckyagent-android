package com.luckyagent.android.perf

/**
 * Process memory at one probe point. [usedBytes] is the JVM heap actually retained,
 * not the reserved heap, so a growing chat window shows up as a real increase.
 */
data class MemorySample(
    val usedBytes: Long,
    val totalBytes: Long,
    val maxBytes: Long,
    val nativeBytes: Long = 0L,
) {
    val usedRatio: Double
        get() = if (maxBytes <= 0L) 0.0 else usedBytes.toDouble() / maxBytes.toDouble()
}

fun interface MemoryProbe {
    fun sample(): MemorySample

    companion object {
        val runtime: MemoryProbe = MemoryProbe {
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory()
            val free = runtime.freeMemory()
            MemorySample(
                usedBytes = (total - free).coerceAtLeast(0L),
                totalBytes = total,
                maxBytes = runtime.maxMemory(),
            )
        }
    }
}
