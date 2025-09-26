package com.example.wiimotebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class WiimoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private var networkSender: NetworkSender? = null
    private var btClient: BluetoothWiimoteClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra("host") ?: "192.168.1.100"
        val port = intent?.getIntExtra("port", 5555) ?: 5555

        // Start foreground with a simple notification
        val notif: Notification = Notification.Builder(this, "wiimote_bridge_ch")
            .setContentTitle("Wiimote Bridge")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notif)

        networkSender = NetworkSender(host, port)
        // Attempt to connect to the wiimote that is already paired
        scope.launch {
            val paired = BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
            // Choose the first device with name containing "Nintendo" or "Wii"
            val candidate = paired.firstOrNull { it.name?.contains("Nintendo", true) == true || it.name?.contains("Wii", true) == true }
            if (candidate != null) {
                btClient = BluetoothWiimoteClient(this@WiimoteService, candidate, networkSender!!)
                btClient?.runLoop()
            } else {
                Log.w("WiimoteService", "No paired wiimote found. Please pair the Wiimote in Settings first.")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        btClient?.close()
        networkSender?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("wiimote_bridge_ch", "Wiimote Bridge", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }
}
