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
    /** On-disk size when the server reports it (segment_v1 / legacy md). */
    @SerialName("byte_size") val byteSize: Long? = null,
    /** Session storage format: legacy_md or segment_v1. */
    val format: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SessionHistory(
    val id: String? = null,
    val title: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    val format: String? = null,
    @SerialName("byte_size") val byteSize: Long? = null,
    /** True when message bodies were capped; pass include=full for complete text. */
    @SerialName("content_truncated") val contentTruncated: Boolean? = null,
    val messages: List<ProviderMessage> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
    val returned: Int? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
)

/** POST /api/v1/sessions/{id}/compact response. */
@Serializable
data class CompactSessionResult(
    @SerialName("boundary_id") val boundaryId: String? = null,
    val trigger: String? = null,
    val summary: String? = null,
    @SerialName("from_message") val fromMessage: Int? = null,
    @SerialName("to_message") val toMessage: Int? = null,
    @SerialName("pre_token_estimate") val preTokenEstimate: Int? = null,
    @SerialName("post_token_estimate") val postTokenEstimate: Int? = null,
    @SerialName("summary_tokens") val summaryTokens: Int? = null,
    @SerialName("dropped_messages") val droppedMessages: Int? = null,
    @SerialName("retained_messages") val retainedMessages: Int? = null,
    @SerialName("restored_attachments") val restoredAttachments: Int? = null,
    @SerialName("summary_source") val summarySource: String? = null,
    @SerialName("dry_run") val dryRun: Boolean? = null,
    val display: CompactDisplay? = null,
)

@Serializable
data class CompactDisplay(
    val title: String? = null,
    val subtitle: String? = null,
    val message: String? = null,
)

@Serializable
data class CompactSessionRequest(
    @SerialName("dry_run") val dryRun: Boolean = false,
    @SerialName("force_local") val forceLocal: Boolean = false,
)

@Serializable
data class ProviderMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("content_parts") val contentParts: List<ProviderContentPart> = emptyList(),
    val attachments: List<MediaAttachment> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    val usage: TokenUsage? = null,
    val name: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<HistoryToolCall> = emptyList(),
    /** Present when large tool/assistant bodies are externalized server-side. */
    @SerialName("blob_hash") val blobHash: String? = null,
    @SerialName("blob_bytes") val blobBytes: Int? = null,
)

@Serializable
data class ProviderContentPart(
    val type: String? = null,
    val text: String? = null,
    val image: ProviderImagePart? = null,
)

@Serializable
data class ProviderImagePart(
    val url: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
)

@Serializable
data class TokenUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("cached_input_tokens") val cachedInputTokens: Int = 0,
    val model: String? = null,
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
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("run_id") val runId: String? = null,
    val timestamp: String? = null,
    val data: JsonElement? = null,
    val error: String? = null,
)

data class WsEvent(
    val connectionId: String,
    val sessionId: String,
    val envelope: WsEnvelope,
)

data class WsChatHandle(
    val connectionId: String,
    val sessionId: String,
    val requestId: String,
    val ownsLease: Boolean = true,
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

/** Model kinds supported by the LuckyAgent runtime model switch API. */
enum class FunctionalModelKind(val wireValue: String, val label: String) {
    Chat("chat", "对话"),
    Vision("vision", "视觉"),
    Embedding("embedding", "向量"),
    Transcription("transcription", "转写"),
    Image("image", "生图"),
    Tts("tts", "语音"),
    Reranker("reranker", "重排"),
}

val FunctionalModelKinds: List<FunctionalModelKind> = FunctionalModelKind.entries

fun modelKindLabel(kind: String): String =
    FunctionalModelKind.entries.firstOrNull { it.wireValue == kind.lowercase() }?.label
        ?: kind.ifBlank { "其他" }

/** A model advertised by GET /api/v1/models. */
@Serializable
data class ModelRef(
    val id: String,
    val kind: String,
    val provider: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("api_base") val apiBase: String? = null,
    val protocol: String? = null,
    val capabilities: JsonElement? = null,
    val current: Boolean = false,
)

@Serializable
data class ModelsResponse(
    val models: List<ModelRef> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class SwitchModelRequest(
    val kind: String,
    val model: String,
    val provider: String? = null,
)

/** Keeps grouping behavior deterministic for the composer sheet and unit tests. */
fun modelsByKind(models: List<ModelRef>): Map<String, List<ModelRef>> =
    models.groupBy { it.kind.trim().lowercase() }

@Serializable
data class ChatOutbound(
    val type: String = "chat",
    val id: String,
    val data: ChatOutboundData,
)

@Serializable
data class ReconnectOutbound(
    val type: String = "reconnect",
    val data: ReconnectOutboundData,
)

@Serializable
data class ReconnectOutboundData(
    @SerialName("last_message_id") val lastMessageId: String = "",
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
