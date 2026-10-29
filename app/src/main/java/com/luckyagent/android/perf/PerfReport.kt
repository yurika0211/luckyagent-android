package com.luckyagent.android.perf

/**
 * Aggregated timings for one section. [p95Nanos] is the value a chat scroll
 * actually feels; the mean hides a single slow parse.
 */
data class PerfAggregate(
    val section: String,
    val variant: PerfVariant,
    val count: Int,
    val totalNanos: Long,
    val minNanos: Long,
    val maxNanos: Long,
    val p95Nanos: Long,
    val meanMemoryDeltaBytes: Long,
    val hottestStack: String,
) {
    val meanNanos: Long
        get() = if (count == 0) 0L else totalNanos / count
}

data class AbComparison(
    val section: String,
    val control: PerfAggregate,
    val treatment: PerfAggregate,
) {
    val speedup: Double
        get() {
            val treated = treatment.meanNanos
            if (treated <= 0L) return 0.0
            return control.meanNanos.toDouble() / treated.toDouble()
        }

    val memorySavedBytes: Long
        get() = control.meanMemoryDeltaBytes - treatment.meanMemoryDeltaBytes

    val treatmentWins: Boolean
        get() = treatment.meanNanos < control.meanNanos
}

object PerfReport {
    fun aggregate(samples: List<PerfSample>): List<PerfAggregate> {
        if (samples.isEmpty()) return emptyList()
        return samples
            .groupBy { it.section to it.variant }
            .map { (key, group) -> summarize(key.first, key.second, group) }
            .sortedWith(compareBy({ it.section }, { it.variant }))
    }

    fun compare(samples: List<PerfSample>): List<AbComparison> {
        val bySection = aggregate(samples).groupBy { it.section }
        return bySection.mapNotNull { (section, variants) ->
            val control = variants.firstOrNull { it.variant == PerfVariant.CONTROL } ?: return@mapNotNull null
            val treatment = variants.firstOrNull { it.variant == PerfVariant.TREATMENT } ?: return@mapNotNull null
            AbComparison(section, control, treatment)
        }.sortedBy { it.section }
    }

    internal fun nearestRank(count: Int, percentile: Double): Int {
        if (count <= 1) return 0
        return kotlin.math.ceil(percentile * count).toInt().coerceIn(1, count) - 1
    }

    private fun summarize(section: String, variant: PerfVariant, samples: List<PerfSample>): PerfAggregate {
        val timings = samples.map { it.elapsedNanos }.sorted()
        val p95Index = nearestRank(timings.size, 0.95)
        val hottest = samples
            .groupingBy { it.stack.topLabel }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        return PerfAggregate(
            section = section,
            variant = variant,
            count = samples.size,
            totalNanos = timings.sum(),
            minNanos = timings.first(),
            maxNanos = timings.last(),
            p95Nanos = timings[p95Index],
            meanMemoryDeltaBytes = samples.sumOf { it.memoryDeltaBytes } / samples.size,
            hottestStack = hottest,
        )
    }
}
