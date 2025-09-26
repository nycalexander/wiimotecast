# wiimote_bridge_server.py
import socket
import struct
import json
import threading
import sys
import traceback

# pip install vgamepad
try:
    import vgamepad as vg
    from vgamepad import XUSB_BUTTON
except Exception as e:
    print("ERROR: vgamepad import failed. Install vgamepad and ViGEmBus. pip install vgamepad")
    raise

HOST = "0.0.0.0"
PORT = 4968

# Map friendly button names to XUSB_BUTTON constants
BUTTON_MAP = {
    "A": XUSB_BUTTON.XUSB_GAMEPAD_A,
    "B": XUSB_BUTTON.XUSB_GAMEPAD_B,
    "X": XUSB_BUTTON.XUSB_GAMEPAD_X,
    "Y": XUSB_BUTTON.XUSB_GAMEPAD_Y,
    "L": XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
    "R": XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
    "BACK": XUSB_BUTTON.XUSB_GAMEPAD_BACK,
    "START": XUSB_BUTTON.XUSB_GAMEPAD_START,
    "DPAD_UP": XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
    "DPAD_DOWN": XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
    "DPAD_LEFT": XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
    "DPAD_RIGHT": XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,
    # Add more if needed
}

# Create single virtual Xbox 360 controller
gamepad = vg.VX360Gamepad()
print("[*] Virtual Xbox360 controller created via ViGEmBus (vgamepad).")

def recv_all(sock, n):
    data = b''
    while len(data) < n:
        packet = sock.recv(n - len(data))
        if not packet:
            return None
        data += packet
    return data

def send_frame(sock, obj):
    raw = json.dumps(obj).encode('utf-8')
    header = struct.pack('>I', len(raw))
    sock.sendall(header + raw)

def handle_message(msg):
    """
    Interpret msg payload and apply to virtual gamepad.
    msg expected: { "payload": { "buttons": {...}, "axes": {...}, "triggers": {...} } }
    """
    print(f"[LOG] Received packet: {json.dumps(msg)}")
    payload = msg.get("payload", {})
    buttons = payload.get("buttons", {})
    axes = payload.get("axes", {})
    triggers = payload.get("triggers", {})

    # Buttons
    for name, val in buttons.items():
        btn = BUTTON_MAP.get(name.upper())
        if btn is None:
            print(f"[LOG] Unknown button: {name}")
            continue
        if bool(val):
            print(f"[LOG] Press button: {name}")
            gamepad.press_button(button=btn)
        else:
            print(f"[LOG] Release button: {name}")
            gamepad.release_button(button=btn)

    # Axes - expect floats -1..1
    lx = float(axes.get("left_x", 0.0))
    ly = float(axes.get("left_y", 0.0))
    rx = float(axes.get("right_x", 0.0))
    ry = float(axes.get("right_y", 0.0))
    print(f"[LOG] Axes: left_x={lx}, left_y={ly}, right_x={rx}, right_y={ry}")
    try:
        gamepad.left_joystick_float(x_value=lx, y_value=ly)
        gamepad.right_joystick_float(x_value=rx, y_value=ry)
    except Exception:
        # older versions may require scaled ints:
        gamepad.left_joystick(x_value=int((lx + 1) * 32767 / 2), y_value=int((1 - ly) * 32767 / 2))
        gamepad.right_joystick(x_value=int((rx + 1) * 32767 / 2), y_value=int((1 - ry) * 32767 / 2))

    # Triggers - expect 0..1
    lt = float(triggers.get("lt", 0.0))
    rt = float(triggers.get("rt", 0.0))
    print(f"[LOG] Triggers: lt={lt}, rt={rt}")
    try:
        gamepad.left_trigger(int(max(0, min(1, lt)) * 255))
        gamepad.right_trigger(int(max(0, min(1, rt)) * 255))
    except Exception:
        pass

    gamepad.update()

def client_thread(conn, addr):
    print(f"[+] Client {addr} connected")
    try:
        while True:
            # read 4 byte length header
            header = recv_all(conn, 4)
            if not header:
                break
            length = struct.unpack('>I', header)[0]
            raw = recv_all(conn, length)
            if raw is None:
                break
            try:
                msg = json.loads(raw.decode('utf-8'))
            except Exception as e:
                print("[!] JSON decode error:", e)
                continue

            # Process message
            try:
                handle_message(msg)
            except Exception as e:
                print("[!] handle_message error:", e)
                traceback.print_exc()

            # Reply with ack
            ack_obj = {"ack": msg.get("seq")}
            send_frame(conn, ack_obj)

    except Exception as e:
        print("[!] Connection error:", e)
    finally:
        print(f"[-] Client {addr} disconnected")
        try:
            conn.close()
        except:
            pass

def start_server(host=HOST, port=PORT):
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((host, port))
    srv.listen(5)
    print(f"[*] Listening on {host}:{port}")
    try:
        while True:
            conn, addr = srv.accept()
            t = threading.Thread(target=client_thread, args=(conn, addr), daemon=True)
            t.start()
    except KeyboardInterrupt:
        print("[*] Shutting down")
    finally:
        srv.close()

if __name__ == "__main__":
    start_server()
