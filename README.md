# WiimoteCast

## Overview
WiimoteCast is an Android application and companion Python server that enables you to use a Nintendo Wiimote as a controller universally (over ViGEmBus [vgamepad] as an XBOX360 controller) for your PC, including support for Dolphin Emulator, any game with controller support in general and custom input servers. Unfortunately at the moment, L2CAP is restricted on Android, hence why this project is not functional on modern Android versions.

---

## Table of Contents
- [Project Goals](#project-goals)
- [Architecture](#architecture)
- [Android App](#android-app)
  - [Permissions](#permissions)
  - [Bluetooth Limitations](#bluetooth-limitations)
  - [HID vs. Bluetooth L2CAP](#hid-vs-bluetooth-l2cap)
  - [Foreground Service & Notifications](#foreground-service--notifications)
  - [UI/UX Features](#uiux-features)
  - [Dolphin Mode](#dolphin-mode)
- [Python Server](#python-server)
- [Networking Protocol](#networking-protocol)
- [Development & Build](#development--build)
- [Troubleshooting](#troubleshooting)
- [References](#references)

---

## Project Goals
- **Bridge Wiimote input to PC**: Use your Android device as a Bluetooth/HID relay for Wiimote input, forwarding events to a PC server.
- **Support Dolphin Emulator**: Provide a mode compatible with Dolphin's UDP input protocol.
- **User-friendly UI**: Device selection, service control, log viewing, and persistent notifications.

---

## Architecture
```
[Wiimote] <-> [Android Device: WiimoteCast App] <-> [WiFi/Network] <-> [PC: WiimoteCast Python Server/Dolphin]
```
- **Wiimote**: Connects to Android via Bluetooth (if possible) or as a HID device (via adapters or OTG).
- **Android App**: Reads input, translates to JSON/UDP, and forwards to the server.
- **PC Server**: Receives input, emulates gamepad or feeds Dolphin Emulator.

---

## Android App
### Permissions
- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_PRIVILEGED`: Required for classic and modern Bluetooth operations.
- `INTERNET`: For sending input to the PC server.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`: For persistent background operation.
- `POST_NOTIFICATIONS`: For showing persistent notifications.

### Bluetooth Limitations
- **Android 8+**: Reflection-based L2CAP and insecure RFCOMM are blocked. Wiimote's non-standard pairing is not supported by public APIs.

### Foreground Service & Notifications
- Runs as a foreground service to avoid being killed by the OS.
- Persistent notification with actions (Stop, Show Logs).
- All input and connection events are logged for debugging.

### UI/UX Features
- **Device Selection**: Lists paired devices, allows user to select which Wiimote to use.
- **IP/Port Entry**: User specifies the PC/server to send input to.
- **Dolphin Mode Toggle**: Switch between custom server and Dolphin UDP protocol.
- **Start/Stop Service**: Control the background service from the UI.
- **Log Viewer**: View recent logs directly in the app.

### Dolphin Mode
- When enabled, the app sends input in a format compatible with Dolphin Emulator's UDP input protocol.
- Uses a separate sender class (`DolphinInputSender`) to format and transmit packets.
- Can be toggled at runtime from the UI.

---

## Python Server
- Receives JSON or Dolphin UDP packets from the app.
- Emulates a gamepad using `vgamepad` or similar libraries.
- Logs all received packets and input actions for debugging.
- Can be extended to support other emulators or custom input schemes.

---

## Networking Protocol
- **Custom Mode**: JSON-encoded input events over TCP/UDP.
- **Dolphin Mode**: Binary UDP packets matching Dolphin's protocol.
- **Security**: No authentication by default; run on trusted networks only.

---

## Development & Build
- **Android**: Kotlin, Android Studio/Gradle. Programmatic UI (no XML layouts).
- **Python**: Python 3.8+, `vgamepad`, `socket`, `logging`.
- **Permissions**: All required permissions are requested at runtime.

---

## Troubleshooting
- **Bluetooth connection fails**: Most likely due to Android security. Use an older device.
- **No devices listed**: Ensure Wiimote is paired.
- **No input on PC**: Check server logs, firewall, and network connectivity.
- **App killed in background**: Foreground service and notification are required; do not swipe away the app.
- **Dolphin not receiving input**: Ensure correct IP/port and that Dolphin UDP server is enabled.

---

## Research Notes
### Android Bluetooth Security
- Android 8+ blocks insecure RFCOMM and L2CAP reflection, making direct Wiimote connections nearly impossible without root or system hacks.

### HID Polling (failed attempt at working around the Bluetooth block)
- Uses `InputDevice.getDeviceIds()` and filters by name.
- Polls every 30ms for input state.
- Maps standard buttons and axes; can be extended for more features.

### Foreground Service
- Required for persistent operation on modern Android.
- Notification channel is created for user awareness and control.

### UI Design
- All UI is programmatic for maximum flexibility and to avoid XML merge issues.
- Log viewer uses `logcat` output for real-time debugging.

---

## References
- [Dolphin Emulator UDP Protocol](https://wiki.dolphin-emu.org/index.php?title=UDP_Wiimote)
- [Android Bluetooth Security Changes](https://developer.android.com/guide/topics/connectivity/bluetooth)
- [vgamepad Python Library](https://pypi.org/project/vgamepad/)
- [Android InputDevice API](https://developer.android.com/reference/android/view/InputDevice)

---
