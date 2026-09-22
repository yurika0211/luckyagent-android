package com.luckyagent.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionsResponse(
    val sessions: List<RuntimeSession> = emptyList(),
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
    val count: Int? = null,
    val total: Int? = null,
)

@Serializable
data class MemoryEntry(
    val id: String? = null,
    val content: String? = null,
    val category: String? = null,
    val tier: String? = null,
    val importance: Double? = null,
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
