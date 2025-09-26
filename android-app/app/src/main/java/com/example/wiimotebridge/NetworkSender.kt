package com.example.wiimotebridge

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class NetworkSender(private val host: String, private val port: Int) {
    private val gson = Gson()
    private val seq = AtomicLong(1)
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private var inp: DataInputStream? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ackTimeoutMs = 1000L
    private val maxRetries = 5

    init {
        connect()
    }

    private fun connect() {
        scope.launch {
            while (socket == null) {
                try {
                    Log.i("WiimoteBridge", "Attempting to connect to $host:$port")
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 2000)
                    socket = s
                    out = DataOutputStream(s.getOutputStream())
                    inp = DataInputStream(s.getInputStream())
                    Log.i("WiimoteBridge", "Connected to $host:$port")
                } catch (e: Exception) {
                    Log.w("WiimoteBridge", "Connect failed: ${e.message}. Retrying in 2s.")
                    delay(2000)
                }
            }
        }
    }

    fun sendEvent(payload: Any) {
        val s = seq.getAndIncrement()
        val frame = mapOf("seq" to s, "payload" to payload)
        val raw = gson.toJson(frame)
        Log.d("WiimoteBridge", "Sending event: $raw")
        scope.launch {
            var tries = 0
            while (tries < maxRetries) {
                try {
                    val o = out
                    if (o == null) {
                        delay(500)
                        tries++
                        continue
                    }
                    // write length-prefixed (int32 BE) + JSON bytes
                    val bytes = raw.toByteArray(Charsets.UTF_8)
                    o.writeInt(bytes.size)
                    o.write(bytes)
                    o.flush()

                    // wait for ack (blocking read with timeout via coroutine)
                    val ack = withTimeoutOrNull(ackTimeoutMs) {
                        // read ack int length, then payload (expect {"ack": seq})
                        val inpLocal = inp ?: return@withTimeoutOrNull null
                        val len = inpLocal.readInt()
                        val buf = ByteArray(len)
                        inpLocal.readFully(buf)
                        String(buf, Charsets.UTF_8)
                    }

                    if (ack != null) {
                        // parse ack with safe cast
                        val ackMap = gson.fromJson(ack, Map::class.java)
                        val ackSeq = ( (ackMap as? Map<*, *>)?.get("ack") as? Double)?.toLong() ?: -1L
                        if (ackSeq == s) {
                            Log.d("WiimoteBridge", "Received ack for seq $s")
                            // success
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w("WiimoteBridge", "send attempt failed: ${e.message}")
                }
                tries++
                delay(200)
            }
            Log.w("WiimoteBridge", "Failed to send after $maxRetries tries.")
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
    }
}
