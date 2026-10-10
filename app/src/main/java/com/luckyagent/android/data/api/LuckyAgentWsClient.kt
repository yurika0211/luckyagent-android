package com.luckyagent.android.data.api

import com.luckyagent.android.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class SocketState {
    Idle, Connecting, Connected, Running, Reconnecting, Error, Closed
}

class LuckyAgentWsClient(
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private data class ConnectionConfig(
        val apiBase: String,
        val wsUrl: String,
        val apiKey: String,
        val useBearer: Boolean,
        val sessionId: String,
    )

    private class ManagedConnection(
        val id: String,
        val config: ConnectionConfig,
    ) {
        val socketRef = AtomicReference<WebSocket?>(null)
        val userClosed = AtomicBoolean(false)
        val reconnectAttempt = AtomicInteger(0)
        @Volatile var reconnectJob: Job? = null
        @Volatile var leases: Int = 0
        @Volatile var lastUsedAt: Long = System.nanoTime()

        fun touch() {
            lastUsedAt = System.nanoTime()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, ManagedConnection>()
    private val currentConnectionId = AtomicReference<String?>(null)

    private val _state = MutableStateFlow(SocketState.Idle)
    val state: StateFlow<SocketState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _raw = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val raw: SharedFlow<String> = _raw.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _reconnectInfo = MutableStateFlow<String?>(null)
    val reconnectInfo: StateFlow<String?> = _reconnectInfo.asStateFlow()

    fun connect(
        sessionId: String = settingsRepository.snapshot().sessionId,
        force: Boolean = false,
    ): String {
        val config = snapshotConfig(sessionId)
        if (!force) {
            val existing = connections.values.firstOrNull { connection ->
                connection.config == config && !connection.userClosed.get()
            }
            if (existing != null) {
                currentConnectionId.set(existing.id)
                existing.touch()
                if (existing.socketRef.get() == null && existing.reconnectJob == null) {
                    openSocket(existing, isReconnect = false)
                } else {
                    markCurrentConnected(existing)
                }
                return existing.id
            }
        }

        val connection = ManagedConnection(
            id = UUID.randomUUID().toString(),
            config = config,
        )
        connections[connection.id] = connection
        currentConnectionId.set(connection.id)
        trimIdleConnections()
        openSocket(connection, isReconnect = false)
        return connection.id
    }

    fun reconnectNow() {
        val id = currentConnectionId.get() ?: return
        val connection = connections[id] ?: return
        connection.userClosed.set(false)
        connection.reconnectAttempt.set(0)
        reopenSocket(connection, isReconnect = true)
    }

    fun replaySession(sessionId: String) {
        val connection = connections.values.firstOrNull { connection ->
            connection.config.sessionId == sessionId && !connection.userClosed.get()
        }
        if (connection == null) {
            connect(sessionId)
            return
        }
        currentConnectionId.set(connection.id)
        connection.touch()
        connection.userClosed.set(false)
        trimIdleConnections()
        // A warm socket only needs a replay request; the handshake is skipped.
        val ws = connection.socketRef.get()
        if (ws != null && sendReconnect(connection, ws)) {
            markCurrentConnected(connection)
            return
        }
        connection.reconnectAttempt.set(0)
        reopenSocket(connection, isReconnect = true)
    }

    private fun sendReconnect(connection: ManagedConnection, webSocket: WebSocket): Boolean {
        val reconnect = json.encodeToString(
            ReconnectOutbound.serializer(),
            ReconnectOutbound(
                data = ReconnectOutboundData(
                    lastMessageId = settingsRepository.eventCursor(connection.config.sessionId),
                ),
            ),
        )
        return webSocket.send(reconnect)
    }

    private fun reopenSocket(connection: ManagedConnection, isReconnect: Boolean) {
        val previous = connection.socketRef.get()
        openSocket(connection, isReconnect)
        if (previous != null && previous !== connection.socketRef.get()) {
            previous.close(1000, "reconnect")
        }
    }

    private fun snapshotConfig(sessionId: String): ConnectionConfig {
        val snap = settingsRepository.snapshot()
        return ConnectionConfig(
            apiBase = snap.apiBase.trim().trimEnd('/'),
            wsUrl = snap.wsUrl.trim(),
            apiKey = snap.apiKey.trim(),
            useBearer = snap.useBearer,
            sessionId = sessionId.ifBlank { "android-main" },
        )
    }

    private fun isCurrent(connection: ManagedConnection): Boolean =
        currentConnectionId.get() == connection.id

    private fun markCurrentConnected(connection: ManagedConnection) {
        if (!isCurrent(connection)) return
        _lastError.value = null
        _reconnectInfo.value = null
        _state.value = if (connection.socketRef.get() == null) SocketState.Connecting else SocketState.Connected
    }

    private fun openSocket(connection: ManagedConnection, isReconnect: Boolean) {
        if (connection.userClosed.get()) return
        connection.reconnectJob?.cancel()
        connection.reconnectJob = null
        connection.socketRef.getAndSet(null)?.close(1000, "reconnect")

        val sessionQ = java.net.URLEncoder.encode(connection.config.sessionId, Charsets.UTF_8.name())
        val override = connection.config.wsUrl
        val url = if (override.isNotEmpty()) {
            if (override.contains("?")) "$override&session=$sessionQ" else "$override?session=$sessionQ"
        } else {
            val base = connection.config.apiBase
            if (base.isEmpty()) {
                if (isCurrent(connection)) {
                    _state.value = SocketState.Error
                    _lastError.value = "API Base is empty"
                }
                return
            }
            val wsBase = when {
                base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
                base.startsWith("wss://") || base.startsWith("ws://") -> base
                else -> "ws://$base"
            }
            "$wsBase/api/v1/ws?session=$sessionQ"
        }

        val builder = Request.Builder().url(url)
        if (connection.config.apiKey.isNotEmpty()) {
            if (connection.config.useBearer) builder.header("Authorization", "Bearer ${connection.config.apiKey}")
            else builder.header("X-API-Key", connection.config.apiKey)
        }
        if (isCurrent(connection)) {
            _state.value = if (isReconnect) SocketState.Reconnecting else SocketState.Connecting
            _lastError.value = null
            _reconnectInfo.value = if (isReconnect) {
                "reconnect ${connection.reconnectAttempt.get()}"
            } else {
                null
            }
        }

        val ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connection.socketRef.set(webSocket)
                connection.reconnectAttempt.set(0)
                sendReconnect(connection, webSocket)
                if (isCurrent(connection)) {
                    _reconnectInfo.value = null
                    _state.value = SocketState.Connected
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleInboundFrame(connection, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connection.socketRef.compareAndSet(webSocket, null)) return
                if (isCurrent(connection)) {
                    _lastError.value = t.message ?: "WebSocket failure"
                    _state.value = SocketState.Error
                }
                scheduleReconnect(connection, "failure: ${t.message ?: "unknown"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!connection.socketRef.compareAndSet(webSocket, null)) return
                if (isCurrent(connection)) _state.value = SocketState.Closed
                if (code != 1000) scheduleReconnect(connection, "closed $code $reason")
            }
        })
        connection.socketRef.set(ws)
    }

    /**
     * Process one inbound frame.
     *
     * Oversized frames still advance the event cursor, otherwise a reconnect
     * replays the same frame forever and a dropped stream_end leaves the UI
     * stuck in Running. The raw diagnostic channel only receives a complete
     * frame or an already-marked truncation, never a sliced JSON document.
     */
    private fun handleInboundFrame(connection: ManagedConnection, text: String) {
        // A reconnect can replay a large or malformed frame. Do not let
        // callback-thread exceptions crash the process.
        if (text.length > MAX_INBOUND_MESSAGE_CHARS) {
            val cursor = inboundEventCursor(text)
            if (cursor.isNotEmpty()) {
                settingsRepository.saveEventCursor(connection.config.sessionId, cursor)
            }
            _raw.tryEmit(oversizedFrameNotice(text.length))
            if (isCurrent(connection)) {
                _lastError.value = "WebSocket message too large"
                _state.value = SocketState.Error
            }
            _events.tryEmit(
                WsEvent(
                    connectionId = connection.id,
                    sessionId = connection.config.sessionId,
                    envelope = WsEnvelope(
                        type = "error",
                        eventId = cursor.takeIf { it.isNotEmpty() },
                        error = "WebSocket message too large (${text.length} chars)",
                    ),
                ),
            )
            return
        }
        _raw.tryEmit(text)
        val parsed = runCatching {
            val env = json.decodeFromString(WsEnvelope.serializer(), text)
            settingsRepository.saveEventCursor(
                connection.config.sessionId,
                env.eventId ?: env.id.orEmpty(),
            )
            if (isCurrent(connection)) {
                when (env.type) {
                    "stream_chunk", "assistant_delta", "delta", "chunk",
                    "tool_call", "tool", "running", "status", "reasoning", "approval",
                    -> {
                        val stateHint = (env.data as? kotlinx.serialization.json.JsonObject)
                            ?.get("state")
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        _state.value = if (stateHint == "idle") SocketState.Connected else SocketState.Running
                    }
                    "stream_end", "final", "done", "chat_done", "assistant_message" -> {
                        _state.value = SocketState.Connected
                    }
                    "error" -> _state.value = SocketState.Error
                    "cancel", "cancelled" -> _state.value = SocketState.Connected
                }
            }
            _events.tryEmit(
                WsEvent(
                    connectionId = connection.id,
                    sessionId = connection.config.sessionId,
                    envelope = env,
                ),
            )
        }
        if (parsed.isFailure) {
            _events.tryEmit(
                WsEvent(
                    connectionId = connection.id,
                    sessionId = connection.config.sessionId,
                    envelope = WsEnvelope(type = "raw", data = null, error = text.take(500)),
                ),
            )
        }
    }

    private fun scheduleReconnect(connection: ManagedConnection, reason: String) {
        if (connection.userClosed.get() || connections[connection.id] !== connection) return
        val attempt = connection.reconnectAttempt.incrementAndGet()
        val delayMs = (RECONNECT_BASE_MS * attempt).coerceAtMost(RECONNECT_MAX_MS)
        if (isCurrent(connection)) {
            _state.value = SocketState.Reconnecting
            _reconnectInfo.value = "reconnect $attempt in ${delayMs}ms"
        }
        connection.reconnectJob?.cancel()
        connection.reconnectJob = scope.launch {
            delay(delayMs)
            if (!connection.userClosed.get() && connections[connection.id] === connection) {
                openSocket(connection, isReconnect = true)
            }
        }
    }

    fun sendChat(
        message: String,
        maxIterations: Int = 8,
        attachments: List<MediaAttachment> = emptyList(),
        sessionId: String? = null,
        trackLease: Boolean = true,
        requestId: String? = null,
    ): WsChatHandle? {
        val connection = connectionFor(sessionId) ?: return null
        val outboundId = requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val payload = json.encodeToString(
            ChatOutbound.serializer(),
            ChatOutbound(
                id = outboundId,
                sessionId = connection.config.sessionId,
                data = ChatOutboundData(
                    message = message,
                    stream = true,
                    maxIterations = maxIterations,
                    attachments = attachments,
                ),
            ),
        )
        synchronized(connection) {
            val ws = connection.socketRef.get() ?: return null
            if (!ws.send(payload)) return null
            if (trackLease) connection.leases += 1
        }
        if (trackLease && isCurrent(connection)) _state.value = SocketState.Running
        return WsChatHandle(connection.id, connection.config.sessionId, outboundId, ownsLease = trackLease)
    }

    fun sendApprovalResponse(sessionId: String, requestId: String, provider: String, decision: String, input: String = ""): Boolean {
        val connection = connectionFor(sessionId) ?: return false
        val payload = json.encodeToString(
            ApprovalResponseOutbound.serializer(),
            ApprovalResponseOutbound(
                id = UUID.randomUUID().toString(),
                sessionId = connection.config.sessionId,
                data = ApprovalResponseOutboundData(
                    requestId = requestId,
                    provider = provider.ifBlank { "runtime" },
                    decision = decision,
                    input = input,
                ),
            ),
        )
        synchronized(connection) {
            val ws = connection.socketRef.get() ?: return false
            return ws.send(payload)
        }
    }

    fun sendLucky(sessionId: String, action: String): Boolean {
        val connection = connectionFor(sessionId) ?: return false
        val payload = json.encodeToString(
            LuckyOutbound.serializer(),
            LuckyOutbound(
                id = UUID.randomUUID().toString(),
                sessionId = connection.config.sessionId,
                data = LuckyOutboundData(action = action),
            ),
        )
        val ws = connection.socketRef.get() ?: return false
        return ws.send(payload)
    }

    private fun connectionFor(sessionId: String?): ManagedConnection? {
        val wanted = sessionId?.trim().orEmpty()
        if (wanted.isNotEmpty()) {
            connections.values.firstOrNull { connection ->
                connection.config.sessionId == wanted && !connection.userClosed.get() && connection.socketRef.get() != null
            }?.let { return it }
        }
        val id = currentConnectionId.get() ?: return null
        return connections[id]
    }

    fun cancel(handle: WsChatHandle): Boolean {
        val connection = connections[handle.connectionId] ?: return false
        val ws = connection.socketRef.get() ?: return false
        val payload =
            """{"type":"cancel","session_id":${json.encodeToString(handle.sessionId)},"data":{"session_id":${json.encodeToString(handle.sessionId)}}}"""
        return ws.send(payload)
    }

    fun release(connectionId: String) {
        val connection = connections[connectionId] ?: return
        val idle = synchronized(connection) {
            connection.leases = (connection.leases - 1).coerceAtLeast(0)
            connection.leases == 0
        }
        if (idle) trimIdleConnections()
    }

    fun replayHandle(sessionId: String, requestId: String, connectionId: String): WsChatHandle =
        WsChatHandle(connectionId, sessionId, requestId, ownsLease = false)

    /**
     * Keep a few idle sockets warm so switching back to a recent session does
     * not pay for a new handshake. Sockets for an old endpoint or key are
     * closed once idle; leased and current sockets are never touched.
     */
    private fun trimIdleConnections() {
        val currentId = currentConnectionId.get()
        val current = currentId?.let(connections::get)
        val idle = connections.values.filter { it.id != currentId && it.leases == 0 }
        val (sameEndpoint, staleEndpoint) = idle.partition { connection ->
            current == null || sameEndpoint(connection.config, current.config)
        }
        staleEndpoint.forEach(::closeConnection)
        val evict = idleConnectionsToEvict(
            sameEndpoint.map { it.id to it.lastUsedAt },
            MAX_IDLE_CONNECTIONS,
        ).toSet()
        sameEndpoint.filter { it.id in evict }.forEach(::closeConnection)
    }

    private fun sameEndpoint(a: ConnectionConfig, b: ConnectionConfig): Boolean =
        a.apiBase == b.apiBase && a.wsUrl == b.wsUrl && a.apiKey == b.apiKey && a.useBearer == b.useBearer

    private fun closeConnection(connection: ManagedConnection) {
        if (!connections.remove(connection.id, connection)) return
        connection.userClosed.set(true)
        connection.reconnectJob?.cancel()
        connection.reconnectJob = null
        connection.socketRef.getAndSet(null)?.close(1000, "idle connection")
    }

    fun disconnect() {
        currentConnectionId.set(null)
        connections.values.toList().forEach(::closeConnection)
        _reconnectInfo.value = null
        _state.value = SocketState.Closed
    }

    companion object {
        /** Idle sockets kept open besides the current one and leased ones. */
        internal const val MAX_IDLE_CONNECTIONS = 4

        /** Ids to close: everything past the [keep] most recently used. */
        internal fun idleConnectionsToEvict(idle: List<Pair<String, Long>>, keep: Int): List<String> =
            idle.sortedByDescending { it.second }.drop(keep.coerceAtLeast(0)).map { it.first }

        const val RECONNECT_BASE_MS = 50L
        const val RECONNECT_MAX_MS = 250L
        /** Reject frames above this size instead of decoding them. */
        internal const val MAX_INBOUND_MESSAGE_CHARS = 8_000_000

        /** Diagnostic raw channel cap. Full frames at or under this size pass through. */
        internal const val MAX_RAW_MESSAGE_CHARS = 64_000

        internal fun oversizedFrameNotice(length: Int): String =
            "{\"type\":\"error\",\"error\":\"frame truncated for display\",\"chars\":$length}"

        /** Find a short event id without decoding or copying the whole frame. */
        internal fun inboundEventCursor(text: String): String =
            cursorField(text, "event_id")
                ?: cursorField(text, "\"id\"")
                ?: ""

        private fun cursorField(window: String, field: String): String? {
            val key = if (field.startsWith("\"")) field else "\"$field\""
            var from = 0
            while (from < window.length) {
                val at = window.indexOf(key, from)
                if (at < 0) return null
                var i = at + key.length
                while (i < window.length && window[i].isWhitespace()) i++
                if (i >= window.length || window[i] != ':') {
                    from = at + key.length
                    continue
                }
                i++
                while (i < window.length && window[i].isWhitespace()) i++
                if (i >= window.length || window[i] != '"') return null
                i++
                val start = i
                while (i < window.length && window[i] != '"') {
                    if (window[i] == '\\') return null
                    i++
                }
                if (i >= window.length) return null
                val value = window.substring(start, i)
                return value.takeIf { it.isNotBlank() }
            }
            return null
        }
    }
}
