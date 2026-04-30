package com.linktolinux.wifidirect.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.linktolinux.wifidirect.network.models.SocketMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class SocketClient {
    private val tag = "SocketClient"
    private val serverIp = "192.168.49.1"
    private val serverPort = 5005

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val _incomingMessages = MutableSharedFlow<SocketMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Connecting to $serverIp:$serverPort...")
            socket = Socket()
            socket?.connect(InetSocketAddress(serverIp, serverPort), 10000)
            
            writer = BufferedWriter(OutputStreamWriter(socket?.getOutputStream(), "UTF-8"))
            reader = BufferedReader(InputStreamReader(socket?.getInputStream(), "UTF-8"))
            
            Log.d(tag, "Connected to Linux Server!")
            startListening()
            
            // Initial Handshake
            sendMessage(SocketMessage(
                type = "HANDSHAKE",
                sender_id = android.os.Build.MODEL,
                payload = "Hello from Android",
                timestamp = System.currentTimeMillis() / 1000
            ))
        } catch (e: Exception) {
            Log.e(tag, "Connection failed: ${e.message}", e)
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    if (line.isNotBlank()) {
                        try {
                            val msg = Json.decodeFromString<SocketMessage>(line)
                            Log.d(tag, "Received: $msg")
                            _incomingMessages.emit(msg)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to parse message: $line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Read loop error: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    suspend fun sendMessage(message: SocketMessage) = withContext(Dispatchers.IO) {
        try {
            val json = Json.encodeToString(message)
            writer?.write(json)
            writer?.write("\n")
            writer?.flush()
            Log.d(tag, "Sent: $json")
        } catch (e: Exception) {
            Log.e(tag, "Send failed: ${e.message}", e)
        }
    }

    fun disconnect() {
        listenJob?.cancel()
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error during disconnect: ${e.message}", e)
        }
        writer = null
        reader = null
        socket = null
        Log.d(tag, "Disconnected from server")
    }
}
