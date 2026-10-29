package com.luckyagent.android.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfProbeTest {
    @Test
    fun disabledProbeRunsWorkWithoutRecording() {
        val probe = PerfProbe(enabled = false, clock = PerfClock { 0L })

        val value = probe.measure("quiet") { 7 }

        assertEquals(7, value)
        assertTrue(probe.snapshot().isEmpty())
    }

    @Test
    fun recordsElapsedMemoryAndCallingFrame() {
        var tick = 0L
        val probe = PerfProbe(
            enabled = true,
            clock = PerfClock { tick.also { tick += 1_000L } },
            memory = sequenced(10, 14),
        )

        probe.measure("chat.render", PerfVariant.TREATMENT, inputUnits = 3) { }

        val sample = probe.snapshot().single()
        assertEquals("chat.render", sample.section)
        assertEquals(1_000L, sample.elapsedNanos)
        assertEquals(4L, sample.memoryDeltaBytes)
        assertEquals(3, sample.inputUnits)
        assertTrue(sample.stack.frames.any { it.methodName == "recordsElapsedMemoryAndCallingFrame" })
    }

    @Test
    fun dropsOldestSampleAfterCapacity() {
        val probe = PerfProbe(enabled = true, clock = PerfClock { 1L }, memory = MemoryProbe { zero() }, capacity = 2)
        probe.record(sample("a"))
        probe.record(sample("b"))
        probe.record(sample("c"))

        assertEquals(listOf("b", "c"), probe.snapshot().map { it.section })
    }

    @Test
    fun reportKeepsP95AndHottestStack() {
        val slow = sample("parse", elapsed = 100, stack = "Parser.parse:4")
        val fast = sample("parse", elapsed = 10, stack = "Parser.parse:4")
        val other = sample("parse", elapsed = 40, stack = "Other.run:1")

        val aggregate = PerfReport.aggregate(listOf(slow, fast, other)).single()

        assertEquals(3, aggregate.count)
        assertEquals(10L, aggregate.minNanos)
        assertEquals(100L, aggregate.maxNanos)
        assertEquals(100L, aggregate.p95Nanos)
        assertEquals("Parser.parse:4", aggregate.hottestStack)
    }

    @Test
    fun abComparisonNamesTheFasterArm() {
        val samples = listOf(
            sample("join", PerfVariant.CONTROL, elapsed = 80),
            sample("join", PerfVariant.TREATMENT, elapsed = 20),
        )

        val comparison = PerfReport.compare(samples).single()

        assertEquals(4.0, comparison.speedup, 0.001)
        assertTrue(comparison.treatmentWins)
        assertFalse(comparison.treatment.meanNanos >= comparison.control.meanNanos)
    }

    private fun sequenced(first: Long, second: Long): MemoryProbe {
        val values = ArrayDeque(listOf(first, second))
        return MemoryProbe { MemorySample(values.removeFirst(), totalBytes = 32, maxBytes = 64) }
    }

    private fun zero() = MemorySample(0, 0, 1)

    private fun sample(
        section: String,
        variant: PerfVariant = PerfVariant.TREATMENT,
        elapsed: Long = 1L,
        stack: String = "Test.sample:1",
    ) = PerfSample(
        section = section,
        variant = variant,
        elapsedNanos = elapsed,
        memoryBefore = zero(),
        memoryAfter = zero(),
        stack = StackSnapshot(
            "test",
            listOf(
                StackFrame(
                    stack.substringBefore('.'),
                    stack.substringAfter('.').substringBefore(':'),
                    stack.substringAfterLast(':').toInt(),
                ),
            ),
        ),
    )
}
