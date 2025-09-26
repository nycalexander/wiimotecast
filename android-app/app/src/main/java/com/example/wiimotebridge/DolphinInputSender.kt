package com.example.wiimotebridge

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DolphinInputSender(private val host: String, private val port: Int) {
    private var socket: DatagramSocket? = null
    private var running = false

    fun start(scope: CoroutineScope) {
        running = true
        scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket()
                Log.i("DolphinInputSender", "Dolphin UDP sender started for $host:$port")
                // This is a placeholder loop. Replace with actual input sending logic.
                while (running) {
                    // Example: send a dummy packet every second
                    val data = byteArrayOf(0x00, 0x01, 0x02, 0x03)
                    val packet = DatagramPacket(data, data.size, InetAddress.getByName(host), port)
                    socket?.send(packet)
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
                Log.e("DolphinInputSender", "Error: ${e.message}", e)
            } finally {
                socket?.close()
                Log.i("DolphinInputSender", "Dolphin UDP sender stopped")
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
    }
}
