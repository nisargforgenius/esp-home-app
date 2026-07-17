package com.example.iotcontroller.data

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.*

private const val RECONNECT_DELAY_MS = 3000L

class WebSocketManager(private val client: OkHttpClient) {
    private var webSocket: WebSocket? = null
    private var lastUrl: String? = null
    private var shouldReconnect = false
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var reconnectJob: Job? = null

    // extraBufferCapacity + DROP_OLDEST ensures tryEmit() never silently fails
    // just because a collector hasn't attached yet (e.g. right after a screen
    // recomposes) -- with the default 0-capacity SharedFlow, that race drops
    // relay-state updates with no error, which is why toggles fired but the UI
    // never reflected the confirmed state.
    private val _events = MutableSharedFlow<WsEvent>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<WsEvent> = _events

    fun connect(url: String) {
        // Skip if we're already connected/connecting to this exact URL --
        // avoids tearing down and rebuilding a perfectly good connection every
        // time refreshStatus() runs (e.g. on each dashboard poll or manual
        // refresh tap).
        if (url == lastUrl && webSocket != null) return

        // If we have a socket for a different URL (e.g. device's IP changed),
        // close it cleanly first. Without this, calling connect() repeatedly
        // piles up orphaned sockets that the ESP's WebSocketsServer keeps
        // broadcasting to, while this class only ever tracks and sends through
        // the newest one -- confusing both sides and sometimes preventing this
        // client from receiving its own broadcast back.
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        lastUrl = url
        shouldReconnect = true
        openSocket(url)
    }

    private fun openSocket(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = gson.fromJson(text, WsEvent::class.java)
                    if (event.event == "update") {
                        _events.tryEmit(event)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // 1000 = normal closure we initiated ourselves via disconnect()
                if (code != 1000) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val url = lastUrl ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect) openSocket(url)
        }
    }

    fun sendCommand(command: WsCommand) {
        val json = gson.toJson(command)
        webSocket?.send(json)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "App closed")
    }
}
