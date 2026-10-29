package com.luckyagent.android.perf

/**
 * A short, allocation-light view of the calling thread. Full traces stay on the
 * slow path; the hot path only keeps the top frames that identify the caller.
 */
data class StackFrame(
    val className: String,
    val methodName: String,
    val lineNumber: Int,
) {
    val label: String
        get() = "$className.$methodName:$lineNumber"
}

data class StackSnapshot(
    val threadName: String,
    val frames: List<StackFrame>,
) {
    val topLabel: String
        get() = frames.firstOrNull()?.label ?: threadName
}

object StackSampler {
    fun capture(maxFrames: Int = 8, skip: Int = 0): StackSnapshot {
        val stack = Throwable().stackTrace
        val start = (skip + 1).coerceAtLeast(0)
        val frames = ArrayList<StackFrame>(maxFrames)
        var index = start
        while (index < stack.size && frames.size < maxFrames) {
            val element = stack[index]
            val className = element.className.substringAfterLast('.')
            if (!className.startsWith("StackSampler") && className != "PerfProbe") {
                frames += StackFrame(className, element.methodName, element.lineNumber)
            }
            index += 1
        }
        return StackSnapshot(Thread.currentThread().name, frames)
    }
}
