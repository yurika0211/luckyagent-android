package com.luckyagent.android.data.cache

import android.content.Context
import androidx.room.Room
import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

data class CachedOutboundMessage(
    val sessionId: String,
    val clientMessageId: String,
    val preview: String,
    val message: String,
    val attachments: List<MediaAttachment>,
    val status: String,
    val requestId: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

class SessionCacheRepository internal constructor(
    private val dao: SessionCacheDao,
    private val json: Json,
) {
    constructor(
        context: Context,
        database: SessionCacheDatabase = Room.databaseBuilder(
            context.applicationContext,
            SessionCacheDatabase::class.java,
            "luckyagent_session_cache.db",
        ).addMigrations(SessionCacheDatabase.MIGRATION_2_3, SessionCacheDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build(),
        json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    ) : this(database.dao(), json)

    /**
     * Isolate cached data by both runtime URL and credential. The same runtime
     * can serve different accounts, so apiBase alone is not a safe namespace.
     */
    fun endpointKey(apiBase: String): String = apiBase.trim().trimEnd('/').lowercase()

    private fun cacheKey(apiBase: String, apiKey: String): String {
        val normalizedBase = endpointKey(apiBase)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "$normalizedBase#key:$digest"
    }

    suspend fun listSessions(apiBase: String, apiKey: String = ""): List<RuntimeSession> =
        dao.listSessions(cacheKey(apiBase, apiKey)).map { row ->
            RuntimeSession(
                id = row.sessionId,
                title = row.title,
                pinned = row.pinned,
                project = row.project,
                messageCount = row.messageCount,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
            )
        }

    suspend fun sessionMeta(apiBase: String, sessionId: String, apiKey: String = ""): CachedSessionMeta? {
        val row = dao.session(cacheKey(apiBase, apiKey), sessionId) ?: return null
        if (row.startIndex < 0) return null
        val historyCount = row.historyMessageCount ?: return null
        return CachedSessionMeta(
            messageCount = historyCount,
            updatedAt = row.historyUpdatedAt,
            reachedOldest = row.reachedOldest,
        )
    }

    suspend fun loadPage(apiBase: String, sessionId: String, apiKey: String = ""): MessagePage? {
        val endpoint = cacheKey(apiBase, apiKey)
        return readPage(endpoint, dao.session(endpoint, sessionId))
    }

    suspend fun listOutbound(apiBase: String, apiKey: String = ""): List<CachedOutboundMessage> =
        dao.outbound(cacheKey(apiBase, apiKey)).mapNotNull { entity ->
            val attachments = runCatching {
                json.decodeFromString<List<MediaAttachment>>(entity.attachmentsJson)
            }.getOrNull() ?: return@mapNotNull null
            CachedOutboundMessage(
                sessionId = entity.sessionId,
                clientMessageId = entity.clientMessageId,
                preview = entity.preview,
                message = entity.message,
                attachments = attachments,
                status = entity.status,
                requestId = entity.requestId,
                error = entity.error,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }

    suspend fun saveOutbound(
        apiBase: String,
        item: CachedOutboundMessage,
        apiKey: String = "",
    ) {
        dao.upsertOutbound(
            CachedOutboundEntity(
                endpoint = cacheKey(apiBase, apiKey),
                sessionId = item.sessionId,
                clientMessageId = item.clientMessageId,
                preview = item.preview,
                message = item.message,
                attachmentsJson = json.encodeToString(item.attachments),
                status = item.status,
                requestId = item.requestId,
                error = item.error,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
            ),
        )
    }

    suspend fun removeOutbound(
        apiBase: String,
        sessionId: String,
        clientMessageId: String,
        apiKey: String = "",
    ) {
        dao.deleteOutbound(cacheKey(apiBase, apiKey), sessionId, clientMessageId)
    }

    /**
     * The stored page, or null when the cache is missing, undecodable, or no
     * longer a contiguous run from [CachedSessionEntity.startIndex]. Diffing
     * against a page with gaps would patch the wrong ordinals.
     */
    private suspend fun readPage(endpoint: String, session: CachedSessionEntity?): MessagePage? {
        if (session == null || session.startIndex < 0) return null
        val stored = dao.messages(endpoint, session.sessionId)
        if (stored.isEmpty() && session.messageCount > 0) return null
        val messages = withContext(Dispatchers.Default) {
            stored.map { entity -> messageFromCache(entity) ?: return@withContext null }
        } ?: return null
        if (stored.withIndex().any { (index, entity) -> entity.ordinal != session.startIndex + index }) return null
        return MessagePage(
            messages = messages,
            startIndex = session.startIndex,
            // Before v4 the listing may have advanced this count without saving
            // bodies. These rows are still a useful offline page, not proof that
            // the remote tail was fetched.
            messageCount = session.historyMessageCount ?: (session.startIndex + stored.size),
            reachedOldest = session.reachedOldest,
        )
    }

    suspend fun touchOpened(apiBase: String, sessionId: String, openedAt: Long = System.currentTimeMillis(), apiKey: String = "") {
        dao.touchOpened(cacheKey(apiBase, apiKey), sessionId, openedAt)
    }

    suspend fun saveSessionList(apiBase: String, sessions: List<RuntimeSession>, now: Long = System.currentTimeMillis(), apiKey: String = "") {
        val endpoint = cacheKey(apiBase, apiKey)
        dao.saveSessionList(
            endpoint,
            sessions.map { remote ->
                CachedSessionListing(
                    sessionId = remote.id,
                    title = remote.title,
                    pinned = remote.pinned,
                    project = remote.project,
                    messageCount = remote.messageCount,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                )
            },
            now,
        )
    }

    suspend fun savePage(
        apiBase: String,
        sessionId: String,
        title: String?,
        updatedAt: String?,
        createdAt: String?,
        page: MessagePage,
        opened: Boolean,
        now: Long = System.currentTimeMillis(),
        apiKey: String = "",
        /** A page the caller already read via [loadPage]; avoids decoding the cache twice. */
        stored: MessagePage? = null,
    ) {
        val endpoint = cacheKey(apiBase, apiKey)
        val existing = dao.session(endpoint, sessionId)
        val storedPage = stored ?: readPage(endpoint, existing)
        val session = sessionEntity(endpoint, sessionId, title, updatedAt, createdAt, page, existing, opened, now)
        when (val write = historyCacheWrite(storedPage, page)) {
            HistoryCacheWrite.Unchanged -> {
                if (existing == null || sessionRowChanged(existing, session)) dao.upsertSession(session)
            }
            is HistoryCacheWrite.Patch -> {
                val inserts = withContext(Dispatchers.Default) {
                    write.inserts.map { item ->
                        messageEntity(endpoint, sessionId, item.ordinal, item.message)
                    }
                }
                dao.patchMessages(
                    session = session,
                    inserts = inserts,
                    deleteBefore = write.deleteBefore,
                    deleteFrom = write.deleteFrom,
                )
            }
            HistoryCacheWrite.Replace -> {
                val messages = withContext(Dispatchers.Default) {
                    page.messages.mapIndexed { index, message ->
                        messageEntity(endpoint, sessionId, page.startIndex + index, message)
                    }
                }
                dao.replaceMessages(
                    session = session,
                    messages = messages,
                    minOrdinal = page.startIndex,
                )
            }
        }
    }

    private fun sessionRowChanged(existing: CachedSessionEntity, next: CachedSessionEntity): Boolean =
        existing.title != next.title ||
            existing.pinned != next.pinned ||
            existing.project != next.project ||
            existing.messageCount != next.messageCount ||
            existing.createdAt != next.createdAt ||
            existing.updatedAt != next.updatedAt ||
            existing.lastOpenedAt != next.lastOpenedAt ||
            existing.reachedOldest != next.reachedOldest ||
            existing.startIndex != next.startIndex ||
            existing.historyMessageCount != next.historyMessageCount ||
            existing.historyUpdatedAt != next.historyUpdatedAt

    private fun sessionEntity(
        endpoint: String,
        sessionId: String,
        title: String?,
        updatedAt: String?,
        createdAt: String?,
        page: MessagePage,
        existing: CachedSessionEntity?,
        opened: Boolean,
        now: Long,
    ) = CachedSessionEntity(
        endpoint = endpoint,
        sessionId = sessionId,
        title = title ?: existing?.title,
        pinned = existing?.pinned ?: false,
        project = existing?.project,
        messageCount = page.messageCount,
        createdAt = createdAt ?: existing?.createdAt,
        updatedAt = updatedAt ?: existing?.updatedAt,
        cachedAt = now,
        lastOpenedAt = if (opened) now else existing?.lastOpenedAt ?: 0L,
        reachedOldest = page.reachedOldest,
        startIndex = page.startIndex,
        historyMessageCount = page.messageCount,
        // A listing timestamp is not a validator for the body just fetched.
        historyUpdatedAt = updatedAt,
    )

    private fun messageEntity(
        endpoint: String,
        sessionId: String,
        ordinal: Int,
        message: ProviderMessage,
    ) = CachedMessageEntity(
        endpoint = endpoint,
        sessionId = sessionId,
        ordinal = ordinal,
        role = message.role,
        content = message.content,
        createdAt = message.createdAt,
        toolCallId = message.toolCallId,
        // A preview is not the stored body. Strip the capped text, but keep
        // attachments and tool calls. blob_hash marks the row even when the
        // server marker is absent. An empty rawJson is the older preview form.
        rawJson = if (message.contentTruncated) {
            json.encodeToString(message.copy(content = null, blobHash = PREVIEW_BLOB))
        } else {
            json.encodeToString(message)
        },
    )

    suspend fun removeSession(apiBase: String, sessionId: String, apiKey: String = "") {
        val endpoint = cacheKey(apiBase, apiKey)
        dao.deleteMessages(endpoint, sessionId)
        dao.deleteSession(endpoint, sessionId)
        dao.deleteOutboundSession(endpoint, sessionId)
    }

    suspend fun invalidate(apiBase: String, sessionId: String, apiKey: String = "") {
        val endpoint = cacheKey(apiBase, apiKey)
        val existing = dao.session(endpoint, sessionId) ?: return
        dao.deleteMessages(endpoint, sessionId)
        dao.upsertSession(
            existing.copy(
                startIndex = -1,
                reachedOldest = false,
                historyMessageCount = null,
                historyUpdatedAt = null,
                cachedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Plain rows rebuild from the structured columns. rawJson is decoded only
     * when the row carries fields those columns cannot hold, such as attachments
     * or tool calls. An empty rawJson, or a body that still ends with the server
     * truncation marker, is a preview, not a full body.
     */
    private fun messageFromCache(entity: CachedMessageEntity): ProviderMessage? {
        val preview = entity.rawJson.isEmpty() ||
            rawJsonHasBlobHash(entity.rawJson, PREVIEW_BLOB) ||
            entity.content?.endsWith(HISTORY_TRUNCATION_MARKER) == true
        if (entity.rawJson.isEmpty() || !rawJsonNeeded(entity.rawJson)) {
            return ProviderMessage(
                role = entity.role,
                content = entity.content,
                createdAt = entity.createdAt,
                toolCallId = entity.toolCallId,
                contentTruncated = preview,
            )
        }
        val decoded = decodeMessage(entity.rawJson) ?: return null
        val message = if (decoded.blobHash == PREVIEW_BLOB) decoded.copy(blobHash = null) else decoded
        if (!preview) return message
        // Older builds stored the capped text as the complete JSON. The content
        // column is that same preview; do not treat the decoded copy as full.
        return message.copy(content = entity.content, contentTruncated = true)
    }

    /** Match the encoded field, not the word anywhere in tool arguments or URLs. */
    private fun rawJsonHasBlobHash(raw: String, value: String): Boolean {
        return raw.contains("\"blob_hash\":\"$value\"")
    }

    private fun rawJsonNeeded(raw: String): Boolean {
        // Empty lists and nulls are omitted (explicitNulls = false), so a hit
        // means the row actually carries that field.
        if (raw.contains("\"attachments\"")) return true
        if (raw.contains("\"tool_calls\"")) return true
        if (raw.contains("\"content_parts\"")) return true
        if (raw.contains("\"reasoning_content\"")) return true
        if (raw.contains("\"usage\"")) return true
        if (raw.contains("\"blob_hash\"")) return true
        if (raw.contains("\"blob_bytes\"")) return true
        if (raw.contains("\"client_message_id\"")) return true
        if (raw.contains("\"name\"")) return true
        return false
    }

    private fun decodeMessage(raw: String): ProviderMessage? =
        runCatching { json.decodeFromString(ProviderMessage.serializer(), raw) }.getOrNull()

    private companion object {
        /** Not a server hash. Marks a preview row whose capped body was not stored. */
        const val PREVIEW_BLOB = "preview"
    }
}
