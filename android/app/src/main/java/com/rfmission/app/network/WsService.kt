package com.rfmission.app.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*

class WsService {
    private val client = OkHttpClient()
    private var ws: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _messages = MutableSharedFlow<String>(replay = 0)
    val messages: SharedFlow<String> = _messages

    fun connect(url: String, token: String?) {
        disconnect()
        val req = Request.Builder().url(url).apply {
            token?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) { _isConnected.tryEmit(true) }
            override fun onMessage(ws: WebSocket, text: String) { _messages.tryEmit(text) }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { _isConnected.tryEmit(false) }
            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { _isConnected.tryEmit(false) }
        })
    }

    fun disconnect() {
        ws?.close(1000, "disconnect")
        ws = null
        _isConnected.tryEmit(false)
    }
}
