package com.example.wiimotebridge

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Minimal UI: a button to start the service and host IP field
        val ipField = EditText(this)
        ipField.hint = "PC IP (eg. 192.168.1.100)"
        val portField = EditText(this)
        portField.hint = "Port (default 5555)"
        val start = Button(this)
        start.text = "Start Service"

        val layout = androidx.constraintlayout.widget.ConstraintLayout(this)
        layout.addView(ipField)
        layout.addView(portField)
        layout.addView(start)
        setContentView(layout)

        start.setOnClickListener {
            val ip = ipField.text.toString().ifEmpty { "192.168.1.100" }
            val port = portField.text.toString().toIntOrNull() ?: 5555
            val intent = Intent(this, WiimoteService::class.java)
            intent.putExtra("host", ip)
            intent.putExtra("port", port)
            startForegroundService(intent)
        }
    }
}
