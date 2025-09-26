package com.example.wiimotebridge

import android.hardware.input.InputManager
import android.os.Build
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class HidWiimoteReader(private val sender: NetworkSender) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = true

    fun start() {
        scope.launch {
            try {
                Log.i("WiimoteBridge", "Starting HID Wiimote polling")
                pollLoop()
            } catch (e: Exception) {
                Log.e("WiimoteBridge", "Error in HID reader: ${e.message}", e)
            }
        }
    }

    private suspend fun pollLoop() {
        while (running) {
            val devices = InputDevice.getDeviceIds().toList()
                .mapNotNull { id -> InputDevice.getDevice(id) }
                .filter { device -> device.name.contains("Nintendo", true) || device.name.contains("Wii", true) }

            Log.d("WiimoteBridge", "HID poll: found ${devices.size} candidate devices")
            for (dev in devices) {
                Log.d("WiimoteBridge", "Polling device: ${dev.name} (id=${dev.id})")
                // Map standard buttons
                val buttonMap = mutableMapOf<String, Int>()
                val keyCodes = listOf(
                    KeyEvent.KEYCODE_BUTTON_A to "A",
                    KeyEvent.KEYCODE_BUTTON_B to "B",
                    KeyEvent.KEYCODE_BUTTON_START to "PLUS",
                    KeyEvent.KEYCODE_DPAD_UP to "DPAD_UP",
                    KeyEvent.KEYCODE_DPAD_DOWN to "DPAD_DOWN",
                    KeyEvent.KEYCODE_DPAD_LEFT to "DPAD_LEFT",
                    KeyEvent.KEYCODE_DPAD_RIGHT to "DPAD_RIGHT"
                )

                for ((code, name) in keyCodes) {
                    val states = dev.hasKeys(code)
                    // hasKeys returns BooleanArray, so use first element
                    val pressed = states.isNotEmpty() && states[0]
                    buttonMap[name] = if (pressed) 1 else 0
                    Log.v("WiimoteBridge", "Button $name (code $code): ${if (pressed) "pressed" else "released"}")
                }

                // Map axes (if device reports motion events)
                val axes = mutableMapOf<String, Double>()
                val axisList = listOf(
                    MotionEvent.AXIS_X to "X",
                    MotionEvent.AXIS_Y to "Y",
                    MotionEvent.AXIS_Z to "Z",
                    MotionEvent.AXIS_RX to "RX",
                    MotionEvent.AXIS_RY to "RY"
                )

                for ((axis, name) in axisList) {
                    val range = dev.getMotionRange(axis)
                    if (range != null) {
                        // Use the midpoint between min and max as center
                        val center = (range.min + range.max) / 2.0
                        val norm = ((center - range.min) / (range.max - range.min) * 2 - 1).coerceIn(-1.0, 1.0)
                        axes[name] = norm
                        Log.v("WiimoteBridge", "Axis $name (axis $axis): normalized=$norm (min=${range.min}, max=${range.max})")
                    }
                }

                // Send event
                val msg = mapOf(
                    "buttons" to buttonMap,
                    "axes" to axes,
                    "triggers" to mapOf<String, Double>()
                )

                Log.d("WiimoteBridge", "Sending HID event: $msg")
                sender.sendEvent(msg)
            }
            delay(30)
        }
    }

    fun stop() {
        running = false
        scope.cancel()
    }
}
