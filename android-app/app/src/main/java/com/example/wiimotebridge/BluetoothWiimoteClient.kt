package com.example.wiimotebridge

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import java.io.InputStream
import java.lang.reflect.Method
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class BluetoothWiimoteClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val sender: NetworkSender
) {
    private var socket: BluetoothSocket? = null
    @Volatile private var running = true

    // Wiimote L2CAP control/data PSMs historically: 17 (control) and 19 (interrupt)
    private val PSM_CONTROL = 17
    private val PSM_INTERRUPT = 19

    fun runLoop() {
        thread(start = true) {
            try {
                Log.i("WiimoteBridge", "Attempting to connect to device: ${device.name} (${device.address})")
                connect()
                Log.i("WiimoteBridge", "Connected, starting read loop")
                readLoop()
            } catch (e: Exception) {
                Log.e("WiimoteBridge", "error: ${e.message}", e)
            } finally {
                Log.i("WiimoteBridge", "Closing Bluetooth client")
                close()
            }
        }
    }

    private fun connect() {
        // Try modern L2CAP API if available (Android 10+ has createInsecureL2capChannel(int) on BluetoothDevice)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val method: Method? = try {
                    device.javaClass.getMethod("createInsecureL2capChannel", Int::class.javaPrimitiveType)
                } catch (e: NoSuchMethodException) {
                    null
                }
                if (method != null) {
                    Log.i("WiimoteBridge", "Using createInsecureL2capChannel via reflection")
                    // create channel to interrupt PSM
                    socket = method.invoke(device, PSM_INTERRUPT) as BluetoothSocket
                    socket?.connect()
                    Log.i("WiimoteBridge", "Connected to Wiimote via L2CAP PSM $PSM_INTERRUPT")
                    return
                }
            }
        } catch (e: Exception) {
            Log.w("WiimoteBridge", "L2CAP reflection failed: ${e.message}")
        }

        // Fallback: try RFCOMM to the well-known Bluetooth HID UUID or random SPP (this might fail for Wiimote)
        try {
            val uuid = java.util.UUID.fromString("00001124-0000-1000-8000-00805f9b34fb") // HID profile UUID
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            Log.i("WiimoteBridge", "Connected to Wiimote via RFCOMM (HID UUID) fallback")
            return
        } catch (e: Exception) {
            Log.w("WiimoteBridge", "RFCOMM fallback failed: ${e.message}")
        }

        throw IllegalStateException("Unable to open BT socket to Wiimote on this device/OS.")
    }

    private fun readLoop() {
        val `is`: InputStream = socket!!.inputStream
        val buf = ByteArray(1024)
        try {
            while (running) {
                val r = `is`.read(buf)

                if (r <= 0) {
                    Thread.sleep(30)
                    continue
                }

                
                val packet = buf.copyOf(r)
                Log.i("WiimoteBridge", "Read $r bytes: ${packet.joinToString { String.format("%02X", it) }}")
                handlePacket(packet)
            }
        } catch (ste: SocketTimeoutException) {
            Log.w("WiimoteBridge", "read timeout")
        } catch (e: Exception) {
            Log.e("WiimoteBridge", "read error ${e.message}", e)
        }
    }

    private fun handlePacket(packet: ByteArray) {
        // Simple MSI: Wiimote HID reports vary. We'll parse button bytes for common formats.
        // For many Wiimote reports, the first bytes are report type / length; buttons often in bytes 2..3.
        // This is not perfect for all firmwares — improve if you need more controls (accel, ir, ext nunchuk).
        if (packet.size < 3) return
        // naive example: extract two button bytes
    // val b1 = packet[1].toInt() and 0xFF // removed unused variable
         val b2 = packet[2].toInt() and 0xFF

        Log.i("WiimoteBridge", "Raw packet for parsing: ${packet.joinToString { String.format("%02X", it) }}")


        // Map bits to logical Wiimote buttons: (this mapping may need tweaking per Wiimote model)
        val buttonMap = mutableMapOf<String, Int>()
        // Example mapping (common):
        // b1 bit0: A, bit1: B, bit2: 1, bit3: 2, bit4: - (plus?), etc.
        buttonMap["A"] = if ((b2 and 0x08) != 0) 1 else 0 // sample bit mapping -- real mapping may differ
        buttonMap["B"] = if ((b2 and 0x04) != 0) 1 else 0


        // Send JSON event to server
        val msg = mapOf(
            "buttons" to buttonMap,
            "axes" to mapOf<String, Double>(),
            "triggers" to mapOf<String, Double>()
        )
        Log.d("WiimoteBridge", "Sending event: $msg")
        sender.sendEvent(msg)
    }

    fun close() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
    }
}
