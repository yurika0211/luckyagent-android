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
    val attachments: List<MediaAttachment> = emptyList(),
)

@Serializable
data class MediaAttachment(
    val type: String = "document",
    @SerialName("file_id") val fileId: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
)

@Serializable
data class UploadResponse(val attachments: List<MediaAttachment> = emptyList())

@Serializable
data class ChatOutbound(
    val type: String = "chat",
    val data: ChatOutboundData,
)

@Serializable
data class SessionCreateRequest(
    val title: String? = null,
)

@Serializable
data class SessionPatchRequest(
    val title: String? = null,
)

@Serializable
data class CommandCatalogResponse(
    val commands: List<RuntimeCommand> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class RuntimeCommand(
    val name: String,
    val usage: String,
    val description: String,
    val group: String = "other",
)

@Serializable
data class CommandRequest(
    val command: String,
    val args: String = "",
    @SerialName("session_id") val sessionId: String = "",
)

@Serializable
data class CommandExecution(
    val command: String = "",
    val ok: Boolean = false,
    val output: String = "",
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
data class MemoryTraceNode(
    val id: String,
    val ref: String? = null,
    val category: String? = null,
    val tier: String? = null,
    val score: Double? = null,
    @SerialName("direct_score") val directScore: Double? = null,
    @SerialName("graph_score") val graphScore: Double? = null,
    @SerialName("content_preview") val contentPreview: String? = null,
    val rank: Int? = null,
)

@Serializable
data class MemoryTraceHop(
    val depth: Int,
    @SerialName("from_id") val fromId: String,
    @SerialName("from_ref") val fromRef: String? = null,
    @SerialName("to_id") val toId: String,
    @SerialName("to_ref") val toRef: String? = null,
    val via: String? = null,
    val kind: String? = null,
    val weight: Double? = null,
    val boost: Double? = null,
    @SerialName("source_score") val sourceScore: Double? = null,
    @SerialName("target_score") val targetScore: Double? = null,
)

@Serializable
data class MemoryTraceFilters(
    val category: String? = null,
    val tier: String? = null,
    @SerialName("include_inactive") val includeInactive: Boolean? = null,
    @SerialName("include_expired") val includeExpired: Boolean? = null,
    @SerialName("as_of") val asOf: String? = null,
)

@Serializable
data class MemorySearchTrace(
    val query: String = "",
    val mode: String? = null,
    val source: String? = null,
    val limit: Int? = null,
    @SerialName("graph_depth") val graphDepth: Int = 1,
    val filters: MemoryTraceFilters? = null,
    val seeds: List<MemoryTraceNode> = emptyList(),
    val hops: List<MemoryTraceHop> = emptyList(),
    val results: List<MemoryTraceNode> = emptyList(),
    @SerialName("temporal_notes") val temporalNotes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    @SerialName("duration_ms") val durationMs: Long? = null,
)

data class ReceivedMemoryTrace(val trace: MemorySearchTrace, val receivedAt: Long)

@Serializable
data class ToolTraceRecord(
    val name: String = "",
    val arguments: String? = null,
    val result: String? = null,
    val success: Boolean = true,
    val error: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val annotation: String? = null,
)

@Serializable
data class SessionToolTrace(
    @SerialName("session_id") val sessionId: String? = null,
    val tools: List<ToolTraceRecord> = emptyList(),
    @SerialName("total_calls") val totalCalls: Int? = null,
    val successes: Int? = null,
    val failures: Int? = null,
    @SerialName("success_rate") val successRate: Double? = null,
)

@Serializable
data class GatewayStats(
    @SerialName("MessagesSent") val messagesSent: Long? = null,
    @SerialName("MessagesReceived") val messagesReceived: Long? = null,
    @SerialName("Errors") val errors: Long? = null,
)

@Serializable
data class GatewayStatus(
    val name: String = "",
    val running: Boolean = false,
    val stats: GatewayStats? = null,
    val platform: String? = null,
    val connected: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class GatewaysResponse(
    val gateways: List<GatewayStatus> = emptyList(),
    val items: List<GatewayStatus> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class SkillTool(
    val name: String = "",
    @SerialName("full_name") val fullName: String? = null,
    val description: String? = null,
    @SerialName("expose_to_model") val exposeToModel: Boolean? = null,
    val registered: Boolean? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class SkillSummary(
    val name: String = "",
    val description: String? = null,
    val summary: String? = null,
    val state: String? = null,
    val dir: String? = null,
    val aliases: List<String> = emptyList(),
    val tools: List<SkillTool> = emptyList(),
    @SerialName("tool_count") val toolCount: Int? = null,
    val available: Boolean? = null,
    val version: String? = null,
    val author: String? = null,
    @SerialName("loaded_at") val loadedAt: String? = null,
    val error: String? = null,
    @SerialName("unhealthy_tools") val unhealthyTools: List<String> = emptyList(),
    val managed: Boolean? = null,
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillSummary> = emptyList(),
    val count: Int? = null,
    @SerialName("skills_dir") val skillsDir: String? = null,
)

/** Keep loose JsonObject fallback for unknown dashboard blobs. */
@Serializable
data class JsonBlobResponse(
    val data: JsonObject? = null,
)
