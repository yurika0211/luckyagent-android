package com.luckyagent.android.data.cache

import android.content.Context
import androidx.room.Room
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionCacheRepository(
    context: Context,
    private val database: SessionCacheDatabase = Room.databaseBuilder(
        context.applicationContext,
        SessionCacheDatabase::class.java,
        "luckyagent_session_cache.db",
    ).fallbackToDestructiveMigration().build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    private val dao = database.dao()

    fun endpointKey(apiBase: String): String = apiBase.trim().trimEnd('/').lowercase()

    suspend fun listSessions(apiBase: String): List<RuntimeSession> =
        dao.listSessions(endpointKey(apiBase)).map { row ->
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

    suspend fun sessionMeta(apiBase: String, sessionId: String): CachedSessionMeta? {
        val row = dao.session(endpointKey(apiBase), sessionId) ?: return null
        if (row.startIndex < 0) return null
        return CachedSessionMeta(
            messageCount = row.messageCount,
            updatedAt = row.updatedAt,
            reachedOldest = row.reachedOldest,
        )
    }

    suspend fun loadPage(apiBase: String, sessionId: String): MessagePage? {
        val endpoint = endpointKey(apiBase)
        val session = dao.session(endpoint, sessionId) ?: return null
        if (session.startIndex < 0) return null
        val stored = dao.messages(endpoint, sessionId)
        if (stored.isEmpty() && session.messageCount > 0) return null
        val messages = stored.mapNotNull { entity -> decodeMessage(entity.rawJson) }
        if (messages.size != stored.size) return null
        return MessagePage(
            messages = messages,
            startIndex = session.startIndex,
            messageCount = session.messageCount,
            reachedOldest = session.reachedOldest,
        )
    }

    suspend fun touchOpened(apiBase: String, sessionId: String, openedAt: Long = System.currentTimeMillis()) {
        val endpoint = endpointKey(apiBase)
        val existing = dao.session(endpoint, sessionId) ?: return
        dao.upsertSession(existing.copy(lastOpenedAt = openedAt))
    }

    suspend fun saveSessionList(apiBase: String, sessions: List<RuntimeSession>, now: Long = System.currentTimeMillis()) {
        val endpoint = endpointKey(apiBase)
        val remoteIds = sessions.map { it.id }.toSet()
        sessions.forEach { remote ->
            val existing = dao.session(endpoint, remote.id)
            dao.upsertSession(
                CachedSessionEntity(
                    endpoint = endpoint,
                    sessionId = remote.id,
                    title = remote.title ?: existing?.title,
                    pinned = remote.pinned,
                    project = remote.project,
                    messageCount = remote.messageCount ?: existing?.messageCount ?: 0,
                    createdAt = remote.createdAt ?: existing?.createdAt,
                    updatedAt = remote.updatedAt ?: existing?.updatedAt,
                    cachedAt = now,
                    lastOpenedAt = existing?.lastOpenedAt ?: 0L,
                    reachedOldest = existing?.reachedOldest ?: false,
                    startIndex = existing?.startIndex ?: -1,
                ),
            )
        }
        dao.sessionIdsByRecentOpen(endpoint)
            .filter { it !in remoteIds }
            .forEach { stale ->
                dao.deleteMessages(endpoint, stale)
                dao.deleteSession(endpoint, stale)
            }
        evictClosedSessions(endpoint)
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
    ) {
        val endpoint = endpointKey(apiBase)
        val capped = capLatestMessages(page)
        val existing = dao.session(endpoint, sessionId)
        val entities = capped.messages.mapIndexed { index, message ->
            CachedMessageEntity(
                endpoint = endpoint,
                sessionId = sessionId,
                ordinal = capped.startIndex + index,
                role = message.role,
                content = message.content,
                createdAt = message.createdAt,
                toolCallId = message.toolCallId,
                rawJson = json.encodeToString(message),
            )
        }
        dao.replaceMessages(
            session = CachedSessionEntity(
                endpoint = endpoint,
                sessionId = sessionId,
                title = title ?: existing?.title,
                pinned = existing?.pinned ?: false,
                project = existing?.project,
                messageCount = capped.messageCount,
                createdAt = createdAt ?: existing?.createdAt,
                updatedAt = updatedAt ?: existing?.updatedAt,
                cachedAt = now,
                lastOpenedAt = if (opened) now else existing?.lastOpenedAt ?: 0L,
                reachedOldest = capped.reachedOldest,
                startIndex = capped.startIndex,
            ),
            messages = entities,
            minOrdinal = capped.startIndex,
        )
        evictClosedSessions(endpoint)
    }

    suspend fun removeSession(apiBase: String, sessionId: String) {
        val endpoint = endpointKey(apiBase)
        dao.deleteMessages(endpoint, sessionId)
        dao.deleteSession(endpoint, sessionId)
    }

    suspend fun invalidate(apiBase: String, sessionId: String) {
        val endpoint = endpointKey(apiBase)
        val existing = dao.session(endpoint, sessionId) ?: return
        dao.deleteMessages(endpoint, sessionId)
        dao.upsertSession(
            existing.copy(
                startIndex = -1,
                reachedOldest = false,
                cachedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun evictClosedSessions(endpoint: String) {
        val ids = dao.sessionIdsByRecentOpen(endpoint)
        if (ids.size <= SessionCachePolicy.MAX_SESSIONS) return
        ids.drop(SessionCachePolicy.MAX_SESSIONS).forEach { stale ->
            val row = dao.session(endpoint, stale) ?: return@forEach
            if (row.lastOpenedAt <= 0L) return@forEach
            dao.deleteMessages(endpoint, stale)
            dao.deleteSession(endpoint, stale)
        }
    }

    private fun decodeMessage(raw: String): ProviderMessage? =
        runCatching { json.decodeFromString(ProviderMessage.serializer(), raw) }.getOrNull()
}
