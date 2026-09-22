package com.luckyagent.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionsResponse(
    val sessions: List<RuntimeSession> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class RuntimeSession(
    val id: String,
    val title: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SessionHistory(
    val id: String? = null,
    val title: String? = null,
    val messages: List<ProviderMessage> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
    val returned: Int? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
)

@Serializable
data class ProviderMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val name: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<HistoryToolCall> = emptyList(),
)

@Serializable
data class HistoryToolCall(
    val id: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class HealthLive(
    val status: String? = null,
    val ok: Boolean? = null,
)

@Serializable
data class WsEnvelope(
    val type: String,
    @SerialName("session_id") val sessionId: String? = null,
    val id: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val data: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class ChatOutboundData(
    val message: String,
    val stream: Boolean = true,
    @SerialName("max_iterations") val maxIterations: Int = 8,
)

@Serializable
data class ChatOutbound(
    val type: String = "chat",
    val data: ChatOutboundData,
)

@Serializable
data class MemoryListResponse(
    val entries: List<MemoryEntry> = emptyList(),
    val results: List<MemoryEntry> = emptyList(),
    val count: Int? = null,
    val total: Int? = null,
    val stats: MemoryStats? = null,
)

@Serializable
data class MemoryStats(
    val total: Int? = null,
    val active: Int? = null,
    val categories: Int? = null,
    val short: Int? = null,
    val medium: Int? = null,
    val long: Int? = null,
    @SerialName("long_term") val longTerm: Int? = null,
)

@Serializable
data class MemoryEntry(
    val id: String? = null,
    val content: String? = null,
    val category: String? = null,
    val tier: String? = null,
    val importance: Double? = null,
    val tags: List<String> = emptyList(),
    @SerialName("access_count") val accessCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("state_key") val stateKey: String? = null,
    @SerialName("state_value") val stateValue: String? = null,
)

@Serializable
data class MemoryGraphResponse(
    val nodes: List<MemoryGraphNode> = emptyList(),
    val edges: List<MemoryGraphEdge> = emptyList(),
    @SerialName("total_notes") val totalNotes: Int? = null,
    @SerialName("total_edges") val totalEdges: Int? = null,
    @SerialName("isolated_count") val isolatedCount: Int? = null,
    val unresolved: Int? = null,
    val truncated: Boolean? = null,
    val categories: List<String> = emptyList(),
)

@Serializable
data class MemoryGraphNode(
    val id: String,
    val title: String? = null,
    val category: String? = null,
    val tier: String? = null,
    val path: String? = null,
    val tags: List<String> = emptyList(),
    val importance: Double? = null,
    val degree: Int? = null,
    val resolved: Boolean? = null,
)

@Serializable
data class MemoryGraphEdge(
    val source: String,
    val target: String,
    val weight: Int? = null,
)

@Serializable
data class SkillsResponse(
    val skills: List<JsonObject> = emptyList(),
)

@Serializable
data class GatewaysResponse(
    val gateways: List<JsonObject> = emptyList(),
    val items: List<JsonObject> = emptyList(),
)
