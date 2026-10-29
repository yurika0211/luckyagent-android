package com.luckyagent.android.perf

enum class PerfVariant { CONTROL, TREATMENT }

data class PerfSample(
    val section: String,
    val variant: PerfVariant,
    val elapsedNanos: Long,
    val memoryBefore: MemorySample,
    val memoryAfter: MemorySample,
    val stack: StackSnapshot,
    val inputUnits: Int = 0,
) {
    val memoryDeltaBytes: Long
        get() = memoryAfter.usedBytes - memoryBefore.usedBytes
}
