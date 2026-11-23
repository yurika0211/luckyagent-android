package com.luckyagent.android.perf

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    /** Set only while an opted-in diagnostic capture is active. */
    @Volatile
    var sink: ((PerfSample) -> Unit)? = null

    fun <T> measure(
        section: String,
        variant: PerfVariant = PerfVariant.TREATMENT,
        inputUnits: Int = 0,
        attributes: () -> Map<String, String> = { emptyMap() },
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
                    attributes = attributes(),
                ),
            )
        }
    }

    fun record(sample: PerfSample) {
        synchronized(samples) {
            while (samples.size >= capacity) samples.removeFirst()
            samples.addLast(sample)
        }
        sink?.invoke(sample)
    }

    /**
     * Records an already aggregated event without reading memory or capturing
     * a stack. This is the only form allowed on high-frequency UI paths.
     */
    fun recordLightweight(
        section: String,
        elapsedNanos: Long,
        variant: PerfVariant = PerfVariant.TREATMENT,
        inputUnits: Int = 0,
        attributes: Map<String, String> = emptyMap(),
    ) {
        if (!enabled) return
        record(
            PerfSample(
                section = section,
                variant = variant,
                elapsedNanos = elapsedNanos.coerceAtLeast(0L),
                memoryBefore = MemorySample(0L, 0L, 0L),
                memoryAfter = MemorySample(0L, 0L, 0L),
                stack = StackSnapshot("aggregated", emptyList()),
                inputUnits = inputUnits,
                attributes = attributes,
            ),
        )
    }

    fun bumpCounter(name: String) {
        if (!enabled) return
        counters.computeIfAbsent(name) { AtomicLong() }.incrementAndGet()
    }

    fun drainCounter(name: String): Long = counters.remove(name)?.get() ?: 0L

    fun snapshot(): List<PerfSample> = synchronized(samples) { samples.toList() }

    fun clear() {
        synchronized(samples) { samples.clear() }
        counters.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY = 256

        val shared = PerfProbe(enabled = false)
    }
}
