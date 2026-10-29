package com.luckyagent.android.perf

/**
 * Wall clock used by probes. Tests inject a counter so timings stay deterministic.
 */
fun interface PerfClock {
    fun nanos(): Long

    companion object {
        val system: PerfClock = PerfClock { System.nanoTime() }
    }
}
