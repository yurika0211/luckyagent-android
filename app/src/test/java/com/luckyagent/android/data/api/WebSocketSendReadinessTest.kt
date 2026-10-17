package com.luckyagent.android.data.api

import com.luckyagent.android.data.settings.ClientSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WebSocketSendReadinessTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettings
    private lateinit var transport: OkHttpClient
    private lateinit var client: LuckyAgentWsClient

    private class FakeSettings(@Volatile var current: ClientSettings) : WsSettings {
        override fun snapshot() = current
        override fun eventCursor(sessionId: String) = ""
        override fun saveEventCursor(sessionId: String, cursor: String) = Unit
    }

    private class Peer : WebSocketListener() {
        val frames = LinkedBlockingQueue<String>()
        val sockets = LinkedBlockingQueue<WebSocket>()
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            sockets.offer(webSocket)
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            frames.offer(text)
        }
        fun nextType(): String {
            val frame = frames.poll(5, TimeUnit.SECONDS) ?: error("Missing WebSocket frame")
            return Json.parseToJsonElement(frame).jsonObject.getValue("type").jsonPrimitive.content
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        settings = FakeSettings(ClientSettings(apiBase = server.url("/").toString(), apiKey = "test-key", sessionId = "one"))
        transport = OkHttpClient.Builder().build()
        client = LuckyAgentWsClient(settings, transport)
    }

    @After
    fun tearDown() {
        client.disconnect()
        transport.dispatcher.executorService.shutdownNow()
        transport.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun waitsForSlowUpgradeAndSendsOnlyAfterOnOpen() = runBlocking {
        val peer = Peer()
        server.enqueue(MockResponse().withWebSocketUpgrade(peer).setHeadersDelay(900, TimeUnit.MILLISECONDS))
        val id = client.connect("one")
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNull("An enqueued pre-upgrade frame is not a sent message", client.sendChat("hello", sessionId = "one"))
        assertEquals(id, client.connect("one"))
        assertEquals(SocketState.Connecting, client.state.value)
        assertTrue("A handshake longer than the old 450ms retry must succeed", client.awaitConnected("one", 5_000))
        assertNotNull(client.sendChat("hello", sessionId = "one"))
        assertEquals("reconnect", peer.nextType())
        assertEquals("chat", peer.nextType())
        assertNull(peer.frames.poll(100, TimeUnit.MILLISECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun doesNotFallBackToADifferentSession() = runBlocking {
        val peer = Peer()
        server.enqueue(MockResponse().withWebSocketUpgrade(peer))
        assertTrue(client.awaitConnected("one", 5_000))
        assertEquals("reconnect", peer.nextType())
        assertNull(client.sendChat("wrong session", sessionId = "two"))
        assertFalse(client.sendLucky("two", "on"))
        assertFalse(client.sendApprovalResponse("two", "request", "runtime", "approve"))
        assertNull(peer.frames.poll(100, TimeUnit.MILLISECONDS))
        assertNotNull(client.sendChat("correct session", sessionId = "one"))
        assertEquals("chat", peer.nextType())
    }

    @Test
    fun changedKeyCannotReuseALeasedSocketWithOldCredentials() = runBlocking {
        val oldPeer = Peer()
        val newPeer = Peer()
        server.enqueue(MockResponse().withWebSocketUpgrade(oldPeer))
        assertTrue(client.awaitConnected("one", 5_000))
        assertNotNull(client.sendChat("keep old run leased", sessionId = "one"))
        assertEquals("reconnect", oldPeer.nextType())
        assertEquals("chat", oldPeer.nextType())
        settings.current = settings.current.copy(apiKey = "replacement-key")
        assertNull(client.sendChat("new key", sessionId = "one"))
        server.enqueue(MockResponse().withWebSocketUpgrade(newPeer))
        assertTrue(client.awaitConnected("one", 5_000))
        assertNotNull(client.sendChat("new key", sessionId = "one"))
        assertEquals("reconnect", newPeer.nextType())
        assertEquals("chat", newPeer.nextType())
        assertNull(oldPeer.frames.poll(100, TimeUnit.MILLISECONDS))
        assertEquals("Bearer test-key", server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("Authorization"))
        assertEquals("Bearer replacement-key", server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("Authorization"))
    }

    @Test
    fun changedEndpointCannotReuseALeasedSocketForSameSession() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            val oldPeer = Peer()
            val newPeer = Peer()
            server.enqueue(MockResponse().withWebSocketUpgrade(oldPeer))
            assertTrue(client.awaitConnected("one", 5_000))
            assertNotNull(client.sendChat("keep old run leased", sessionId = "one"))
            assertEquals("reconnect", oldPeer.nextType())
            assertEquals("chat", oldPeer.nextType())
            settings.current = settings.current.copy(apiBase = other.url("/").toString())
            assertNull(client.sendChat("new endpoint", sessionId = "one"))
            other.enqueue(MockResponse().withWebSocketUpgrade(newPeer))
            assertTrue(client.awaitConnected("one", 5_000))
            assertNotNull(client.sendChat("new endpoint", sessionId = "one"))
            assertEquals("reconnect", newPeer.nextType())
            assertEquals("chat", newPeer.nextType())
            assertNull(oldPeer.frames.poll(100, TimeUnit.MILLISECONDS))
        } finally {
            client.disconnect()
            other.shutdown()
        }
    }

    @Test
    fun rejectedKeyStopsRetryingAndReportsAuthenticationFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFalse(client.awaitConnected("one", 5_000))
        assertEquals(SocketState.Error, client.state.value)
        assertTrue(client.lastError.value.orEmpty().contains("401"))
        assertNull(client.sendChat("unauthorized", sessionId = "one"))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNull("A bad key must not trigger a reconnect storm", server.takeRequest(800, TimeUnit.MILLISECONDS))
    }

    @Test
    fun forbiddenResponseAlsoStopsRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFalse(client.awaitConnected("one", 5_000))
        assertTrue(client.lastError.value.orEmpty().contains("403"))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNull(server.takeRequest(800, TimeUnit.MILLISECONDS))
    }

    @Test
    fun transientHandshakeFailureWaitsForReconnectBeforeSending() = runBlocking {
        val peer = Peer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().withWebSocketUpgrade(peer))
        assertTrue(client.awaitConnected("one", 5_000))
        assertNotNull(client.sendChat("after transient failure", sessionId = "one"))
        assertEquals("reconnect", peer.nextType())
        assertEquals("chat", peer.nextType())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun timeoutLeavesMessageUnsent() = runBlocking {
        val peer = Peer()
        server.enqueue(MockResponse().withWebSocketUpgrade(peer).setHeadersDelay(1, TimeUnit.SECONDS))
        assertFalse(client.awaitConnected("one", 100))
        assertNull(client.sendChat("not connected", sessionId = "one"))
        assertTrue(client.awaitConnected("one", 5_000))
        assertEquals("reconnect", peer.nextType())
        assertNull(peer.frames.poll(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun reconnectIgnoresOldCallbacksAndReplaysBeforeNewChat() = runBlocking {
        val first = Peer()
        val replacement = Peer()
        server.enqueue(MockResponse().withWebSocketUpgrade(first))
        assertTrue(client.awaitConnected("one", 5_000))
        assertEquals("reconnect", first.nextType())
        server.enqueue(MockResponse().withWebSocketUpgrade(replacement).setHeadersDelay(700, TimeUnit.MILLISECONDS))
        client.reconnectNow()
        assertNull(client.sendChat("too early", sessionId = "one"))
        assertTrue(client.awaitConnected("one", 5_000))
        assertNotNull(client.sendChat("after reconnect", sessionId = "one"))
        assertEquals("reconnect", replacement.nextType())
        assertEquals("chat", replacement.nextType())
        assertEquals(SocketState.Running, client.state.value)
        assertEquals(2, server.requestCount)
    }
}
