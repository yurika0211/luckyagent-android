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

@Entity(
    tableName = "cached_sessions",
    primaryKeys = ["endpoint", "sessionId"],
    indices = [Index(value = ["endpoint", "lastOpenedAt"])],
)
data class CachedSessionEntity(
    val endpoint: String,
    val sessionId: String,
    val title: String?,
    val messageCount: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val cachedAt: Long,
    val lastOpenedAt: Long,
    /** True once the oldest stored row is the session's first message. */
    val reachedOldest: Boolean,
    /** Server index of the oldest stored message. -1 when the body was never stored. */
    val startIndex: Int,
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
    val rawJson: String,
)

data class CachedSessionRow(
    val sessionId: String,
    val title: String?,
    val messageCount: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val lastOpenedAt: Long,
    val reachedOldest: Boolean,
    val startIndex: Int,
)

@Dao
interface SessionCacheDao {
    @Query(
        """
        SELECT sessionId, title, messageCount, createdAt, updatedAt, lastOpenedAt, reachedOldest, startIndex
        FROM cached_sessions
        WHERE endpoint = :endpoint
        ORDER BY updatedAt DESC, lastOpenedAt DESC
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: CachedSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(entities: List<CachedMessageEntity>)

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
        SELECT sessionId FROM cached_sessions
        WHERE endpoint = :endpoint
        ORDER BY lastOpenedAt DESC
        """,
    )
    suspend fun sessionIdsByRecentOpen(endpoint: String): List<String>

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
}

@Database(
    entities = [CachedSessionEntity::class, CachedMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SessionCacheDatabase : RoomDatabase() {
    abstract fun dao(): SessionCacheDao
}
