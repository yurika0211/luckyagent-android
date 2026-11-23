package com.luckyagent.android.perf

class PerfFrameTracker(
    private val section: String,
    private val probe: PerfProbe = PerfProbe.shared,
    private val extraAttributes: Map<String, String> = emptyMap(),
    private val counterName: String? = null,
) {
    private var startedAt = 0L
    private var lastFrameAt = 0L
    private val frameDurations = ArrayList<Long>()
    private var droppedFrames = 0

    fun begin(frameNanos: Long = System.nanoTime()) {
        startedAt = frameNanos
        lastFrameAt = frameNanos
        frameDurations.clear()
        droppedFrames = 0
    }

    fun frame(frameNanos: Long) {
        if (startedAt == 0L) begin(frameNanos)
        val duration = (frameNanos - lastFrameAt).coerceAtLeast(0L)
        if (duration > 0L) {
            frameDurations += duration
            if (duration > 16_000_000L) droppedFrames += 1
        }
        lastFrameAt = frameNanos
    }

    fun end(frameNanos: Long = System.nanoTime()) {
        if (startedAt == 0L) return
        frame(frameNanos)
        val sorted = frameDurations.sorted()
        val counterAttributes = counterName?.let { mapOf(it.substringAfterLast('.') to probe.drainCounter(it).toString()) }.orEmpty()
        val attributes = extraAttributes + counterAttributes + mapOf(
            "frame_count" to sorted.size.toString(),
            "dropped_frames" to droppedFrames.toString(),
            "p50_frame_nanos" to percentile(sorted, 0.50).toString(),
            "p95_frame_nanos" to percentile(sorted, 0.95).toString(),
        )
        probe.recordLightweight(
            section = section,
            elapsedNanos = (frameNanos - startedAt).coerceAtLeast(0L),
            inputUnits = sorted.size,
            attributes = attributes,
        )
        startedAt = 0L
        lastFrameAt = 0L
        frameDurations.clear()
    }

    private fun percentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0L
        val index = kotlin.math.ceil(values.size * percentile).toInt().coerceIn(1, values.size) - 1
        return values[index]
    }
}
