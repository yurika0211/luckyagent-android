package com.luckyagent.android.perf

import java.util.ArrayDeque

/**
 * Records elapsed time, heap delta and a short stack for one named section.
 * Disabled probes still run the work, so a release build does not change behavior.
 */
class PerfProbe(
    var enabled: Boolean = false,
    private val clock: PerfClock = PerfClock.system,
    private val memory: MemoryProbe = MemoryProbe.runtime,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val samples = ArrayDeque<PerfSample>(capacity)

    fun <T> measure(
        section: String,
        variant: PerfVariant = PerfVariant.TREATMENT,
        inputUnits: Int = 0,
        block: () -> T,
    ): T {
        if (!enabled) return block()
        val before = memory.sample()
        val started = clock.nanos()
        val stack = StackSampler.capture(skip = 1)
        try {
            return block()
        } finally {
            record(
                PerfSample(
                    section = section,
                    variant = variant,
                    elapsedNanos = (clock.nanos() - started).coerceAtLeast(0L),
                    memoryBefore = before,
                    memoryAfter = memory.sample(),
                    stack = stack,
                    inputUnits = inputUnits,
                ),
            )
        }
    }

    fun record(sample: PerfSample) {
        synchronized(samples) {
            while (samples.size >= capacity) samples.removeFirst()
            samples.addLast(sample)
        }
    }

    fun snapshot(): List<PerfSample> = synchronized(samples) { samples.toList() }

    fun clear() {
        synchronized(samples) { samples.clear() }
    }

    companion object {
        const val DEFAULT_CAPACITY = 256

        val shared = PerfProbe(enabled = false)
    }
}
