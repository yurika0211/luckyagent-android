package com.luckyagent.android.data.api

import com.luckyagent.android.data.settings.SettingsRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class SocketState {
    Idle, Connecting, Connected, Running, Error, Closed
}

/**
 * Mirrors GUI App.tsx WebSocket usage:
 *   ws://host/api/v1/ws?session=<id>
 *   send { type: "chat", data: { message, stream, max_iterations } }
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
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived socket
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val socketRef = AtomicReference<WebSocket?>(null)

    private val _state = MutableStateFlow(SocketState.Idle)
    val state: StateFlow<SocketState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEnvelope>(
        extraBufferCapacity = 64,
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

    fun connect(sessionId: String = settingsRepository.snapshot().sessionId) {
        disconnect()
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
        _state.value = SocketState.Connecting
        _lastError.value = null
        val ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = SocketState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _raw.tryEmit(text)
                runCatching { json.decodeFromString(WsEnvelope.serializer(), text) }
                    .onSuccess { env ->
                        if (env.type == "assistant_delta" || env.type == "tool_call" || env.type == "running") {
                            _state.value = SocketState.Running
                        }
                        if (env.type == "done" || env.type == "final" || env.type == "chat_done") {
                            _state.value = SocketState.Connected
                        }
                        _events.tryEmit(env)
                    }
                    .onFailure {
                        _events.tryEmit(
                            WsEnvelope(type = "raw", data = null, error = text.take(500)),
                        )
                    }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = SocketState.Error
                _lastError.value = t.message ?: "WebSocket failure"
                socketRef.compareAndSet(webSocket, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = SocketState.Closed
                socketRef.compareAndSet(webSocket, null)
            }
        })
        socketRef.set(ws)
    }

    fun sendChat(message: String, maxIterations: Int = 8): Boolean {
        val ws = socketRef.get() ?: return false
        val payload = json.encodeToString(
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
        val payload =
            """{"type":"cancel","session_id":${json.encodeToString(sessionId)},"data":{"session_id":${json.encodeToString(sessionId)}}}"""
        return ws.send(payload)
    }

    fun disconnect() {
        socketRef.getAndSet(null)?.close(1000, "client disconnect")
        if (_state.value != SocketState.Idle) _state.value = SocketState.Closed
    }
}
