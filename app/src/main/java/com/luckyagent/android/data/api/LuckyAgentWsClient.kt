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
        closeIdleConnections(except = connection.id)
        openSocket(connection, isReconnect = false)
        return connection.id
    }

    fun reconnectNow() {
        val id = currentConnectionId.get() ?: return
        val connection = connections[id] ?: return
        connection.userClosed.set(false)
        connection.reconnectAttempt.set(0)
        openSocket(connection, isReconnect = true)
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
                "reconnect ${connection.reconnectAttempt.get()}/$MAX_RECONNECT"
            } else {
                null
            }
        }

        val ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connection.socketRef.set(webSocket)
                connection.reconnectAttempt.set(0)
                if (isCurrent(connection)) {
                    _reconnectInfo.value = null
                    _state.value = SocketState.Connected
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _raw.tryEmit(text)
                runCatching { json.decodeFromString(WsEnvelope.serializer(), text) }
                    .onSuccess { env ->
                        if (isCurrent(connection)) {
                            when (env.type) {
                                "stream_chunk", "assistant_delta", "delta", "chunk",
                                "tool_call", "tool", "running", "status", "reasoning",
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
                    .onFailure {
                        _events.tryEmit(
                            WsEvent(
                                connectionId = connection.id,
                                sessionId = connection.config.sessionId,
                                envelope = WsEnvelope(type = "raw", data = null, error = text.take(500)),
                            ),
                        )
                    }
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

    private fun scheduleReconnect(connection: ManagedConnection, reason: String) {
        if (connection.userClosed.get() || connections[connection.id] !== connection) return
        val attempt = connection.reconnectAttempt.incrementAndGet()
        if (attempt > MAX_RECONNECT) {
            if (isCurrent(connection)) {
                _reconnectInfo.value = "reconnect exhausted ($MAX_RECONNECT)"
                _lastError.value = "WebSocket reconnect failed after $MAX_RECONNECT tries ($reason)"
                _state.value = SocketState.Error
            }
            return
        }
        val delayMs = (500L * (1L shl (attempt - 1).coerceAtMost(4))).coerceAtMost(8000L)
        if (isCurrent(connection)) {
            _state.value = SocketState.Reconnecting
            _reconnectInfo.value = "reconnect $attempt/$MAX_RECONNECT in ${delayMs}ms"
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
    ): WsChatHandle? {
        val id = currentConnectionId.get() ?: return null
        val connection = connections[id] ?: return null
        val payload = json.encodeToString(
            ChatOutbound.serializer(),
            ChatOutbound(
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
            connection.leases += 1
        }
        if (isCurrent(connection)) _state.value = SocketState.Running
        return WsChatHandle(connection.id, connection.config.sessionId)
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
        val close = synchronized(connection) {
            connection.leases = (connection.leases - 1).coerceAtLeast(0)
            connection.leases == 0 && currentConnectionId.get() != connection.id
        }
        if (close) closeConnection(connection)
    }

    private fun closeIdleConnections(except: String) {
        connections.values
            .filter { it.id != except && it.leases == 0 }
            .forEach(::closeConnection)
    }

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
        const val MAX_RECONNECT = 8
    }
}
