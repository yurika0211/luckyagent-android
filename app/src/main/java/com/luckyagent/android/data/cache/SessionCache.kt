package com.luckyagent.android.data.cache

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "cached_sessions",
    primaryKeys = ["endpoint", "sessionId"],
    indices = [Index(value = ["endpoint", "lastOpenedAt"])],
)
data class CachedSessionEntity(
    val endpoint: String,
    val sessionId: String,
    val title: String?,
    val pinned: Boolean = false,
    val project: String? = null,
    val messageCount: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val cachedAt: Long,
    val lastOpenedAt: Long,
    /** True once the oldest stored row is the session's first message. */
    val reachedOldest: Boolean,
    /** Server index of the oldest stored message. -1 when the body was never stored. */
    val startIndex: Int,
    /** Snapshot that produced cached_messages, not the newer session listing. */
    val historyMessageCount: Int? = null,
    val historyUpdatedAt: String? = null,
)

@Entity(
    tableName = "cached_messages",
    primaryKeys = ["endpoint", "sessionId", "ordinal"],
    indices = [Index(value = ["endpoint", "sessionId"])],
)
data class CachedMessageEntity(
    val endpoint: String,
    val sessionId: String,
    /** Oldest-first index in the server session, not a page number. */
    val ordinal: Int,
    val role: String?,
    val content: String?,
    val createdAt: String?,
    val toolCallId: String?,
    /**
     * Message JSON without the capped body when the row is a preview.
     * Empty on rows written before that split. [content] is the preview either way
     * and must not be treated as the stored body when it ends with the truncation marker.
     */
    val rawJson: String,
)

@Entity(
    tableName = "cached_outbox",
    primaryKeys = ["endpoint", "sessionId", "clientMessageId"],
    indices = [Index(value = ["endpoint", "sessionId", "createdAt"])],
)
data class CachedOutboundEntity(
    val endpoint: String,
    val sessionId: String,
    /** Stable id sent as the WebSocket chat frame id and server idempotency key. */
    val clientMessageId: String,
    val preview: String,
    val message: String,
    val attachmentsJson: String,
    val status: String,
    val requestId: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CachedSessionRow(
    val sessionId: String,
    val title: String?,
    val pinned: Boolean,
    val project: String?,
    val messageCount: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val lastOpenedAt: Long,
    val reachedOldest: Boolean,
    val startIndex: Int,
)

/** Listing metadata can advance without fetching any history bodies. */
data class CachedSessionListing(
    val sessionId: String,
    val title: String?,
    val pinned: Boolean,
    val project: String?,
    val messageCount: Int?,
    val createdAt: String?,
    val updatedAt: String?,
)

@Dao
interface SessionCacheDao {
    @Query(
        """
        SELECT sessionId, title, pinned, project, messageCount, createdAt, updatedAt, lastOpenedAt, reachedOldest, startIndex
        FROM cached_sessions
        WHERE endpoint = :endpoint
        ORDER BY pinned DESC, updatedAt DESC, lastOpenedAt DESC
        """,
    )
    suspend fun listSessions(endpoint: String): List<CachedSessionRow>

    @Query(
        """
        SELECT * FROM cached_sessions
        WHERE endpoint = :endpoint AND sessionId = :sessionId
        LIMIT 1
        """,
    )
    suspend fun session(endpoint: String, sessionId: String): CachedSessionEntity?

    @Query(
        """
        SELECT * FROM cached_messages
        WHERE endpoint = :endpoint AND sessionId = :sessionId
        ORDER BY ordinal ASC
        """,
    )
    suspend fun messages(endpoint: String, sessionId: String): List<CachedMessageEntity>

    @Query(
        """
        SELECT * FROM cached_outbox
        WHERE endpoint = :endpoint
        ORDER BY createdAt ASC, clientMessageId ASC
        """,
    )
    suspend fun outbound(endpoint: String): List<CachedOutboundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: CachedSessionEntity)

    @Query("UPDATE cached_sessions SET lastOpenedAt = :openedAt WHERE endpoint = :endpoint AND sessionId = :sessionId")
    suspend fun touchOpened(endpoint: String, sessionId: String, openedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(entities: List<CachedMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbound(entity: CachedOutboundEntity)

    @Query(
        """
        DELETE FROM cached_outbox
        WHERE endpoint = :endpoint AND sessionId = :sessionId AND clientMessageId = :clientMessageId
        """,
    )
    suspend fun deleteOutbound(endpoint: String, sessionId: String, clientMessageId: String)

    @Query("DELETE FROM cached_outbox WHERE endpoint = :endpoint AND sessionId = :sessionId")
    suspend fun deleteOutboundSession(endpoint: String, sessionId: String)

    @Query("DELETE FROM cached_messages WHERE endpoint = :endpoint AND sessionId = :sessionId")
    suspend fun deleteMessages(endpoint: String, sessionId: String)

    @Query("DELETE FROM cached_sessions WHERE endpoint = :endpoint AND sessionId = :sessionId")
    suspend fun deleteSession(endpoint: String, sessionId: String)

    @Query(
        """
        DELETE FROM cached_messages
        WHERE endpoint = :endpoint AND sessionId = :sessionId AND ordinal < :minOrdinal
        """,
    )
    suspend fun deleteMessagesBefore(endpoint: String, sessionId: String, minOrdinal: Int)

    @Query(
        """
        DELETE FROM cached_messages
        WHERE endpoint = :endpoint AND sessionId = :sessionId AND ordinal >= :fromOrdinal
        """,
    )
    suspend fun deleteMessagesFrom(endpoint: String, sessionId: String, fromOrdinal: Int)

    @Query(
        """
        SELECT sessionId FROM cached_sessions
        WHERE endpoint = :endpoint
        ORDER BY CASE WHEN lastOpenedAt > 0 THEN 0 ELSE 1 END,
                 lastOpenedAt DESC,
                 updatedAt DESC
        """,
    )
    suspend fun sessionIdsByRecentOpen(endpoint: String): List<String>

    /**
     * Keep the listing refresh atomic with history writes. Reading a session
     * before a concurrent savePage and replacing that old copy would roll back
     * its history snapshot, even if the listing itself never changes those fields.
     */
    @Transaction
    suspend fun saveSessionList(endpoint: String, listings: List<CachedSessionListing>, now: Long) {
        listings.forEach { listing ->
            val existing = session(endpoint, listing.sessionId)
            upsertSession(
                CachedSessionEntity(
                    endpoint = endpoint,
                    sessionId = listing.sessionId,
                    title = listing.title ?: existing?.title,
                    pinned = listing.pinned,
                    project = listing.project,
                    messageCount = listing.messageCount ?: existing?.messageCount ?: 0,
                    createdAt = listing.createdAt ?: existing?.createdAt,
                    updatedAt = listing.updatedAt ?: existing?.updatedAt,
                    cachedAt = now,
                    lastOpenedAt = existing?.lastOpenedAt ?: 0L,
                    reachedOldest = existing?.reachedOldest ?: false,
                    startIndex = existing?.startIndex ?: -1,
                    historyMessageCount = existing?.historyMessageCount,
                    historyUpdatedAt = existing?.historyUpdatedAt,
                ),
            )
        }
        val remoteIds = listings.mapTo(HashSet()) { it.sessionId }
        sessionIdsByRecentOpen(endpoint).filter { it !in remoteIds }.forEach { stale ->
            deleteMessages(endpoint, stale)
            deleteSession(endpoint, stale)
            deleteOutboundSession(endpoint, stale)
        }
    }

    @Transaction
    suspend fun replaceMessages(
        session: CachedSessionEntity,
        messages: List<CachedMessageEntity>,
        minOrdinal: Int,
    ) {
        upsertSession(session)
        deleteMessages(session.endpoint, session.sessionId)
        if (messages.isNotEmpty()) upsertMessages(messages)
        deleteMessagesBefore(session.endpoint, session.sessionId, minOrdinal)
    }

    /**
     * Insert [inserts] and drop rows outside the new window. Rows already stored
     * inside that window stay in place.
     */
    @Transaction
    suspend fun patchMessages(
        session: CachedSessionEntity,
        inserts: List<CachedMessageEntity>,
        deleteBefore: Int,
        deleteFrom: Int,
    ) {
        upsertSession(session)
        deleteMessagesFrom(session.endpoint, session.sessionId, deleteFrom)
        deleteMessagesBefore(session.endpoint, session.sessionId, deleteBefore)
        if (inserts.isNotEmpty()) upsertMessages(inserts)
    }
}

@Database(
    entities = [CachedSessionEntity::class, CachedMessageEntity::class, CachedOutboundEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class SessionCacheDatabase : RoomDatabase() {
    abstract fun dao(): SessionCacheDao

    companion object {
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_outbox (
                        endpoint TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        clientMessageId TEXT NOT NULL,
                        preview TEXT NOT NULL,
                        message TEXT NOT NULL,
                        attachmentsJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        requestId TEXT,
                        error TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(endpoint, sessionId, clientMessageId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_cached_outbox_endpoint_sessionId_createdAt
                    ON cached_outbox(endpoint, sessionId, createdAt)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Old listing metadata may already describe unfetched history.
                // Leave the new snapshot unverified; keep all bodies and outbox
                // rows available offline until the next successful history fetch.
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN historyMessageCount INTEGER")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN historyUpdatedAt TEXT")
            }
        }
    }
}
