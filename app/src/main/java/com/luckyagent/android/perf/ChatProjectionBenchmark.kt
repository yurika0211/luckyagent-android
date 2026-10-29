package com.luckyagent.android.perf

import com.luckyagent.android.data.api.HistoryToolCall
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.toHistoryBubbles

/**
 * A/B harness for joining a tool result back to its call.
 *
 * Both arms start from the same projected bubbles. Control scans every earlier
 * bubble, which is what long histories used to do. Treatment keeps an id index,
 * which is what [projectHistoryBubbles] does on the chat screen.
 */
object ChatProjectionBenchmark {
    fun compare(
        messages: List<ProviderMessage>,
        runs: Int = 5,
        probe: PerfProbe = PerfProbe(enabled = true, memory = MemoryProbe { MemorySample(0, 0, 1) }),
    ): AbComparison {
        require(runs > 0)
        val projected = messages.flatMapIndexed { index, message -> message.toHistoryBubbles(index) }
        repeat(runs) {
            probe.measure("history.join", PerfVariant.CONTROL, messages.size) {
                joinByScan(projected)
            }
            probe.measure("history.join", PerfVariant.TREATMENT, messages.size) {
                joinByIndex(projected)
            }
        }
        return PerfReport.compare(probe.snapshot()).single { it.section == "history.join" }
    }

    internal fun joinByScan(bubbles: List<ChatBubble>): Int {
        var joins = 0
        for (bubble in bubbles) {
            val callId = bubble.stepId ?: continue
            if (bubble.toolOutput == null) continue
            val callIndex = bubbles.indexOfLast { candidate ->
                candidate.stepId == callId && candidate.toolOutput == null
            }
            if (callIndex >= 0) joins += 1
        }
        return joins
    }

    internal fun joinByIndex(bubbles: List<ChatBubble>): Int {
        val calls = HashMap<String, Int>(bubbles.size)
        var joins = 0
        for (index in bubbles.indices) {
            val bubble = bubbles[index]
            val callId = bubble.stepId ?: continue
            if (bubble.toolOutput == null) {
                calls[callId] = index
            } else if (calls.containsKey(callId)) {
                joins += 1
            }
        }
        return joins
    }

    fun syntheticHistory(rounds: Int): List<ProviderMessage> {
        val messages = ArrayList<ProviderMessage>(rounds * 2)
        repeat(rounds) { round ->
            val callId = "call-$round"
            messages += ProviderMessage(
                role = "assistant",
                content = "round $round",
                toolCalls = listOf(HistoryToolCall(id = callId, name = "terminal", arguments = "{}")),
            )
            messages += ProviderMessage(
                role = "tool",
                content = "out-$round",
                toolCallId = callId,
                name = "terminal",
            )
        }
        return messages
    }
}
