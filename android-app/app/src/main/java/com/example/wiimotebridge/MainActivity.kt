
package com.example.wiimotebridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.example.wiimotebridge.WiimoteService
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import android.view.View
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    private fun showLogsDialog() {
        // Read logs using logcat process (works for debug/dev)
        try {
            val process = Runtime.getRuntime().exec("logcat -d -s WiimoteBridge:V")
            val reader = process.inputStream.bufferedReader()
            val logs = reader.readText()
            reader.close()
            val scroll = ScrollView(this)
            val textView = TextView(this)
            textView.text = logs.ifEmpty { "No logs found." }
            textView.setPadding(16, 16, 16, 16)
            scroll.addView(textView)
            AlertDialog.Builder(this)
                .setTitle("WiimoteBridge Logs")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private val REQUEST_BT_PERMISSIONS = 1001
    private val REQUEST_NOTIFICATION_PERM = 1002
    private var selectedDevice: BluetoothDevice? = null
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
    // Use setContentView(R.layout.activity_main) if you have a layout XML, otherwise build UI programmatically
    // setContentView(R.layout.activity_main)

    // If you want to use a programmatic layout, ensure you do NOT import the wrong setContentView
    val layout = LinearLayout(this)
    layout.orientation = LinearLayout.VERTICAL
    layout.setPadding(32, 32, 32, 32)

    // Toggle for Dolphin or Custom server
    val toggleLabel = TextView(this)
    toggleLabel.text = "Connection Type:"
    layout.addView(toggleLabel)

    val toggleSwitch = Switch(this)
    toggleSwitch.text = "Dolphin Mode"
    toggleSwitch.isChecked = false // default to custom
    layout.addView(toggleSwitch)
        super.onCreate(savedInstanceState)

        Log.d("WiimoteBridge", "onCreate called")

        // Request Bluetooth and notification permissions at runtime for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_BT_PERMISSIONS)
            }
        }



        val title = TextView(this)
        title.text = "Wiimote Bridge"
        title.textSize = 24f
        title.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        layout.addView(title)

        // Devices tab
        val deviceLabel = TextView(this)
        deviceLabel.text = "Select Wiimote Device:"
        layout.addView(deviceLabel)

        val deviceSpinner = Spinner(this)
    val bluetoothManager = getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    val adapter = bluetoothManager.adapter
    val pairedDevices = adapter?.bondedDevices?.filter { it.name?.contains("Nintendo", true) == true || it.name?.contains("Wii", true) == true }?.toList() ?: emptyList()
        val deviceNames = pairedDevices.map { it.name + " (" + it.address + ")" }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceNames)
        deviceSpinner.adapter = spinnerAdapter
        layout.addView(deviceSpinner)

        deviceSpinner.setSelection(0, false)
        if (pairedDevices.isNotEmpty()) {
            selectedDevice = pairedDevices[0]
        }
        deviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDevice = pairedDevices.getOrNull(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedDevice = null
            }
        }

        // IP/Port
        val ipLabel = TextView(this)
        ipLabel.text = "PC IP Address:"
        layout.addView(ipLabel)

        val ipField = EditText(this)
        ipField.hint = "e.g. 192.168.1.100"
        layout.addView(ipField)

        val portLabel = TextView(this)
        portLabel.text = "Port:"
        layout.addView(portLabel)

        val portField = EditText(this)
        portField.hint = "default 5555"
        layout.addView(portField)

        val start = Button(this)
        start.text = "Start Service"
        layout.addView(start)

        val stop = Button(this)
        stop.text = "Stop Service"
        stop.isEnabled = false
        layout.addView(stop)

        val logBtn = Button(this)
        logBtn.text = "Show Logs"
        layout.addView(logBtn)

        setContentView(layout)

        start.setOnClickListener {
            if (selectedDevice == null) {
                Toast.makeText(this, "Please select a paired Wiimote device.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Log.d("WiimoteBridge", "Start button clicked")
            val ip = ipField.text.toString().ifEmpty { "192.168.1.100" }
            val port = portField.text.toString().toIntOrNull() ?: 5555
            val intent = Intent(this, WiimoteService::class.java)
            intent.putExtra("host", ip)
            intent.putExtra("port", port)
            intent.putExtra("device_address", selectedDevice?.address)
            intent.putExtra("dolphin_mode", toggleSwitch.isChecked)
            startForegroundService(intent)
            start.text = "Service Started"
            start.isEnabled = false
            stop.isEnabled = true
            isServiceRunning = true
            Toast.makeText(this, "Service started. Sending to $ip:$port", Toast.LENGTH_LONG).show()
        }

        stop.setOnClickListener {
            Log.d("WiimoteBridge", "Stop button clicked")
            val intent = Intent(this, WiimoteService::class.java)
            stopService(intent)
            start.text = "Start Service"
            start.isEnabled = true
            stop.isEnabled = false
            isServiceRunning = false
            Toast.makeText(this, "Service stopped.", Toast.LENGTH_SHORT).show()
        }

        logBtn.setOnClickListener {
            showLogsDialog()
        }
    }
}
