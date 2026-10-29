package com.luckyagent.android.data.cache

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

/** Opens real v2/v3 SQLite files through Room's production migration chain. */
@RunWith(AndroidJUnit4::class)
class SessionCacheMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "history-migration-${UUID.randomUUID()}.db"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val apiBase = "http://cache-test.invalid"
    private val endpoint = "$apiBase#key:" + MessageDigest.getInstance("SHA-256")
        .digest(ByteArray(0)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private var database: SessionCacheDatabase? = null
    private val history = listOf(
        ProviderMessage(role = "user", content = "hello", clientMessageId = "already-sent"),
        ProviderMessage(role = "assistant", content = "saved answer"),
    )

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun v3MigrationPreservesHistoryAndPendingSendButRejectsOldValidators() = runBlocking {
        createLegacyDatabase(version = 3)
        val cache = openCache()

        assertLegacyHistoryIsAvailableButUnverified(cache)
        val pending = cache.listOutbound(apiBase).single()
        assertEquals("not-sent", pending.clientMessageId)
        assertEquals("hello", pending.message)
        assertEquals("Queued", pending.status)

        cache.savePage(apiBase, "s", "verified", "t3", null, page(), opened = true)
        assertEquals(CachedSessionMeta(2, "t3", reachedOldest = true), cache.sessionMeta(apiBase, "s"))

        // A later listing must not certify messages it did not fetch.
        cache.saveSessionList(apiBase, listOf(RuntimeSession(id = "s", messageCount = 9, updatedAt = "t4")))
        assertEquals(CachedSessionMeta(2, "t3", reachedOldest = true), cache.sessionMeta(apiBase, "s"))
        assertEquals(9, cache.listSessions(apiBase).single().messageCount)
        assertEquals(page(), cache.loadPage(apiBase, "s"))
        assertEquals(listOf(pending), cache.listOutbound(apiBase))
    }

    @Test
    fun v2MigrationChainsThroughTheOutboxMigrationWithoutDeletingHistory() = runBlocking {
        createLegacyDatabase(version = 2)
        val cache = openCache()

        assertLegacyHistoryIsAvailableButUnverified(cache)
        assertEquals(emptyList<CachedOutboundMessage>(), cache.listOutbound(apiBase))
        val pending = CachedOutboundMessage(
            sessionId = "s", clientMessageId = "new-pending", preview = "pending",
            message = "pending", attachments = emptyList(), status = "Queued",
            requestId = null, error = null, createdAt = 10, updatedAt = 10,
        )
        cache.saveOutbound(apiBase, pending)
        assertEquals(listOf(pending), cache.listOutbound(apiBase))
    }

    @Test
    fun concurrentListingRefreshesCannotRollBackTheHistorySnapshot() = runBlocking {
        val cache = openCache()
        cache.savePage(apiBase, "s", null, "t1", null, page(), opened = true)
        val updated = page().copy(
            messages = history + ProviderMessage(role = "user", content = "next", clientMessageId = "next"),
            messageCount = 3,
        )

        coroutineScope {
            val listingRefreshes = (1..20).map { count ->
                async {
                    cache.saveSessionList(
                        apiBase,
                        listOf(RuntimeSession(id = "s", messageCount = count + 3, updatedAt = "listing-$count")),
                    )
                }
            }
            val historySave = async { cache.savePage(apiBase, "s", null, "t2", null, updated, opened = true) }
            listingRefreshes.awaitAll()
            historySave.await()
        }

        assertEquals(CachedSessionMeta(3, "t2", reachedOldest = true), cache.sessionMeta(apiBase, "s"))
        assertEquals(updated, cache.loadPage(apiBase, "s"))
    }

    private suspend fun assertLegacyHistoryIsAvailableButUnverified(cache: SessionCacheRepository) {
        assertNull(cache.sessionMeta(apiBase, "s"))
        assertEquals(page(), cache.loadPage(apiBase, "s"))
        // Legacy listing metadata can be newer than the two actual cached rows.
        assertEquals(7, cache.listSessions(apiBase).single().messageCount)
        assertEquals(
            HistorySync.Invalidate,
            historySyncAction(cache.sessionMeta(apiBase, "s"), RuntimeSession(id = "s", messageCount = 7, updatedAt = "t2")),
        )
    }

    private fun page() = MessagePage(history, startIndex = 0, messageCount = 2, reachedOldest = true)

    private fun openCache(): SessionCacheRepository {
        val db = Room.databaseBuilder(context, SessionCacheDatabase::class.java, databaseName)
            .addMigrations(SessionCacheDatabase.MIGRATION_2_3, SessionCacheDatabase.MIGRATION_3_4)
            .build()
        database = db
        return SessionCacheRepository(db.dao(), json)
    }

    private fun createLegacyDatabase(version: Int) {
        context.getDatabasePath(databaseName).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE cached_sessions (
                    endpoint TEXT NOT NULL, sessionId TEXT NOT NULL, title TEXT, pinned INTEGER NOT NULL,
                    project TEXT, messageCount INTEGER NOT NULL, createdAt TEXT, updatedAt TEXT,
                    cachedAt INTEGER NOT NULL, lastOpenedAt INTEGER NOT NULL, reachedOldest INTEGER NOT NULL,
                    startIndex INTEGER NOT NULL, PRIMARY KEY(endpoint, sessionId)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_cached_sessions_endpoint_lastOpenedAt ON cached_sessions(endpoint, lastOpenedAt)")
            db.execSQL(
                """
                CREATE TABLE cached_messages (
                    endpoint TEXT NOT NULL, sessionId TEXT NOT NULL, ordinal INTEGER NOT NULL,
                    role TEXT, content TEXT, createdAt TEXT, toolCallId TEXT, rawJson TEXT NOT NULL,
                    PRIMARY KEY(endpoint, sessionId, ordinal)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX index_cached_messages_endpoint_sessionId ON cached_messages(endpoint, sessionId)")
            db.insertOrThrow("cached_sessions", null, ContentValues().apply {
                put("endpoint", endpoint)
                put("sessionId", "s")
                put("title", "legacy")
                put("pinned", 0)
                put("messageCount", 7)
                put("updatedAt", "t2")
                put("cachedAt", 1)
                put("lastOpenedAt", 1)
                put("reachedOldest", 1)
                put("startIndex", 0)
            })
            history.forEachIndexed { index, message ->
                db.insertOrThrow("cached_messages", null, ContentValues().apply {
                    put("endpoint", endpoint)
                    put("sessionId", "s")
                    put("ordinal", index)
                    put("role", message.role)
                    put("content", message.content)
                    put("rawJson", json.encodeToString(message))
                })
            }
            if (version == 3) {
                db.execSQL(
                    """
                    CREATE TABLE cached_outbox (
                        endpoint TEXT NOT NULL, sessionId TEXT NOT NULL, clientMessageId TEXT NOT NULL,
                        preview TEXT NOT NULL, message TEXT NOT NULL, attachmentsJson TEXT NOT NULL,
                        status TEXT NOT NULL, requestId TEXT, error TEXT,
                        createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(endpoint, sessionId, clientMessageId)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX index_cached_outbox_endpoint_sessionId_createdAt ON cached_outbox(endpoint, sessionId, createdAt)")
                db.insertOrThrow("cached_outbox", null, ContentValues().apply {
                    put("endpoint", endpoint)
                    put("sessionId", "s")
                    put("clientMessageId", "not-sent")
                    put("preview", "hello")
                    put("message", "hello")
                    put("attachmentsJson", "[]")
                    put("status", "Queued")
                    put("createdAt", 10)
                    put("updatedAt", 10)
                })
            }
            db.version = version
        }
    }
}
