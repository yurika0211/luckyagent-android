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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class SocketState {
    Idle, Connecting, Connected, Running, Reconnecting, Error, Closed
}

/**
 * Mirrors GUI App.tsx WebSocket usage against lh serve:
 *   ws://host/api/v1/ws?session=<id>
 *   send { type: "chat", data: { message, stream, max_iterations } }
 *   cancel { type: "cancel", session_id, data: { session_id } }
 *
 * Server event types (internal/websocket/message.go):
 *   stream_chunk / stream_end / tool_call / tool_result / status / error / reasoning / pong
 * Legacy aliases (assistant_delta / final / done) are still accepted for older runtimes.
 */
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketRef = AtomicReference<WebSocket?>(null)
    private val desiredSession = AtomicReference("android-main")
    private val userClosed = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)
    private var reconnectJob: Job? = null

    private val _state = MutableStateFlow(SocketState.Idle)
    val state: StateFlow<SocketState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEnvelope>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsEnvelope> = _events.asSharedFlow()

    private val _raw = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val raw: SharedFlow<String> = _raw.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _reconnectInfo = MutableStateFlow<String?>(null)
    val reconnectInfo: StateFlow<String?> = _reconnectInfo.asStateFlow()

    fun connect(sessionId: String = settingsRepository.snapshot().sessionId) {
        userClosed.set(false)
        reconnectJob?.cancel()
        reconnectAttempt.set(0)
        desiredSession.set(sessionId.ifBlank { "android-main" })
        openSocket(desiredSession.get(), isReconnect = false)
    }

    fun reconnectNow() {
        userClosed.set(false)
        reconnectJob?.cancel()
        openSocket(desiredSession.get(), isReconnect = true)
    }

    private fun openSocket(sessionId: String, isReconnect: Boolean) {
        socketRef.getAndSet(null)?.close(1000, "reconnect")
        val snap = settingsRepository.snapshot()
        val base = snap.apiBase.trim().trimEnd('/')
        if (base.isEmpty()) {
            _state.value = SocketState.Error
            _lastError.value = "API Base is empty"
            return
        }
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            base.startsWith("wss://") || base.startsWith("ws://") -> base
            else -> "ws://$base"
        }
        val url = "$wsBase/api/v1/ws?session=${java.net.URLEncoder.encode(sessionId, Charsets.UTF_8.name())}"
        val builder = Request.Builder().url(url)
        val key = snap.apiKey.trim()
        if (key.isNotEmpty()) {
            if (snap.useBearer) builder.header("Authorization", "Bearer $key")
            else builder.header("X-API-Key", key)
        }
        _state.value = if (isReconnect) SocketState.Reconnecting else SocketState.Connecting
        _lastError.value = null
        if (isReconnect) {
            _reconnectInfo.value = "reconnect ${reconnectAttempt.get()}/$MAX_RECONNECT"
        } else {
            _reconnectInfo.value = null
        }
        val ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt.set(0)
                _reconnectInfo.value = null
                _state.value = SocketState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _raw.tryEmit(text)
                runCatching { json.decodeFromString(WsEnvelope.serializer(), text) }
                    .onSuccess { env ->
                        when (env.type) {
                            "stream_chunk", "assistant_delta", "delta", "chunk",
                            "tool_call", "tool", "running", "status", "reasoning",
                            -> {
                                val stateHint = (env.data as? kotlinx.serialization.json.JsonObject)
                                    ?.get("state")
                                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                                if (stateHint == "idle") {
                                    _state.value = SocketState.Connected
                                } else {
                                    _state.value = SocketState.Running
                                }
                            }
                            "stream_end", "final", "done", "chat_done", "assistant_message" -> {
                                _state.value = SocketState.Connected
                            }
                            "error" -> _state.value = SocketState.Error
                            "cancel", "cancelled" -> _state.value = SocketState.Connected
                        }
                        _events.tryEmit(env)
                    }
                    .onFailure {
                        _events.tryEmit(WsEnvelope(type = "raw", data = null, error = text.take(500)))
                    }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketRef.compareAndSet(webSocket, null)
                _lastError.value = t.message ?: "WebSocket failure"
                _state.value = SocketState.Error
                scheduleReconnect("failure: ${t.message ?: "unknown"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketRef.compareAndSet(webSocket, null)
                _state.value = SocketState.Closed
                if (code != 1000) {
                    scheduleReconnect("closed $code $reason")
                }
            }
        })
        socketRef.set(ws)
    }

    private fun scheduleReconnect(reason: String) {
        if (userClosed.get()) return
        val attempt = reconnectAttempt.incrementAndGet()
        if (attempt > MAX_RECONNECT) {
            _reconnectInfo.value = "reconnect exhausted ($MAX_RECONNECT)"
            _lastError.value = "WebSocket reconnect failed after $MAX_RECONNECT tries ($reason)"
            _state.value = SocketState.Error
            return
        }
        val delayMs = (500L * (1L shl (attempt - 1).coerceAtMost(4))).coerceAtMost(8000L)
        _state.value = SocketState.Reconnecting
        _reconnectInfo.value = "reconnect $attempt/$MAX_RECONNECT in ${delayMs}ms"
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userClosed.get()) {
                openSocket(desiredSession.get(), isReconnect = true)
            }
        }
    }

    fun sendChat(message: String, maxIterations: Int = 8): Boolean {
        val ws = socketRef.get() ?: return false
        if (_state.value != SocketState.Connected && _state.value != SocketState.Running) return false
        val payload = json.encodeToString(
            ChatOutbound.serializer(),
            ChatOutbound(
                data = ChatOutboundData(
                    message = message,
                    stream = true,
                    maxIterations = maxIterations,
                ),
            ),
        )
        _state.value = SocketState.Running
        return ws.send(payload)
    }

    fun cancel(sessionId: String = settingsRepository.snapshot().sessionId): Boolean {
        val ws = socketRef.get() ?: return false
        val sid = sessionId.ifBlank { desiredSession.get() }
        val payload =
            """{"type":"cancel","session_id":${json.encodeToString(sid)},"data":{"session_id":${json.encodeToString(sid)}}}"""
        return ws.send(payload)
    }

    fun disconnect() {
        userClosed.set(true)
        reconnectJob?.cancel()
        reconnectAttempt.set(0)
        _reconnectInfo.value = null
        socketRef.getAndSet(null)?.close(1000, "client disconnect")
        _state.value = SocketState.Closed
    }

    companion object {
        const val MAX_RECONNECT = 8
    }
}
