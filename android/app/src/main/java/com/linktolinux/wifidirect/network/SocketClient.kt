package com.linktolinux.wifidirect.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.linktolinux.wifidirect.network.models.SocketMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class SocketClient {
    private val tag = "SocketClient"
    private val serverPort = 5005

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val _incomingMessages = MutableSharedFlow<SocketMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<State>(State.Disconnected)
    val connectionState: StateFlow<State> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null

    private val jsonConfig = Json { ignoreUnknownKeys = true }

    sealed class State {
        object Disconnected : State()
        object Connecting : State()
        object Connected : State()
        data class Error(val message: String) : State()
    }

   suspend fun connect(serverIp: String) = withContext(Dispatchers.IO) {
      if (_connectionState.value is State.Connecting || _connectionState.value is State.Connected) return@withContext
      Log.d(tag, "Attempting to connect to $serverIp:$serverPort")

      try {
         _connectionState.value = State.Connecting
         val newSocket = Socket()
         newSocket.soTimeout = 15000
         Log.i(tag, "Connecting to server at $serverIp:$serverPort with 10s timeout")
         newSocket.connect(InetSocketAddress(serverIp, serverPort), 10000)
         socket = newSocket

         writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
         reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))

         _connectionState.value = State.Connected
         startListening()
      } catch (e: Exception) {
         _connectionState.value = State.Error(e.message ?: "Unknown error")
         disconnect()
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
                            val msg = jsonConfig.decodeFromString<SocketMessage>(line)
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
        _connectionState.value = State.Disconnected
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
