package com.example.wiimotebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.example.wiimotebridge.DolphinInputSender
import com.example.wiimotebridge.HidWiimoteReader

class WiimoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private var networkSender: NetworkSender? = null
    private var btClient: BluetoothWiimoteClient? = null
    private var dolphinSender: DolphinInputSender? = null
    private var hidReader: HidWiimoteReader? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val dolphinMode = intent?.getBooleanExtra("dolphin_mode", false) ?: false
        val action = intent?.action
        Log.i("WiimoteBridge", "onStartCommand called with action: $action")
        if (action == "STOP_SERVICE") {
            Log.i("WiimoteBridge", "Stopping service via notification action")
            stopSelf()
            return START_NOT_STICKY
        } else if (action == "SHOW_LOGS") {
            Log.i("WiimoteBridge", "Show logs action triggered (no-op in service)")
            // Optionally: send a broadcast or use another mechanism to show logs in the UI
        }

        val host = intent?.getStringExtra("host") ?: "192.168.1.100"
        val port = intent?.getIntExtra("port", 5555) ?: 5555

        // Notification actions

        val stopIntent = Intent(this, WiimoteService::class.java).apply { setAction("STOP_SERVICE") }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val showLogsIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val showLogsPending = if (showLogsIntent != null) {
            PendingIntent.getActivity(this, 2, showLogsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else null


        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_media_pause,
            "Stop",
            stopPending
        ).build()

        val builder = Notification.Builder(this, "wiimote_bridge_ch")
            .setContentTitle("Wiimote Bridge")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(stopAction)
            .setOngoing(true)

        if (showLogsPending != null) {
            val logsAction = Notification.Action.Builder(
                android.R.drawable.ic_menu_info_details,
                "Show Logs",
                showLogsPending
            ).build()
            builder.addAction(logsAction)
        }
        val notif = builder.build()
        startForeground(1, notif)

        if (dolphinMode) {
            Log.i("WiimoteBridge", "Dolphin mode enabled. Starting Dolphin input sender to $host:$port")
            dolphinSender = DolphinInputSender(host, port)
            dolphinSender?.start(scope)
        } else {
            Log.i("WiimoteBridge", "Starting network sender to $host:$port")
            networkSender = NetworkSender(host, port)
            // Use HID Wiimote reader instead of BluetoothWiimoteClient
            try {
                hidReader = HidWiimoteReader(networkSender!!)
                hidReader?.start()
                btClient = null // no L2CAP needed
            } catch (e: Exception) {
                Log.e("WiimoteService", "HID reader error: ${e.message}", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("WiimoteBridge", "Service destroyed")
        scope.cancel()
        btClient?.close()
        networkSender?.close()
        dolphinSender?.stop()
        hidReader?.stop()
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
