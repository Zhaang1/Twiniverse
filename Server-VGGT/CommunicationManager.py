import socket
import threading
import struct
import json
import time
import os

# Configuration
HOST = '0.0.0.0'
PORT = 4567

# Path settings
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DEMO_GLB_IMG = os.path.join(BASE_DIR, 'demo1.glb')
DEMO_GLB_VID = os.path.join(BASE_DIR, 'demo2.glb')


def get_timestamp_str():
    return str(int(time.time()))


def setFilename(filename):
    if not filename:
        return "NullName"
    return filename


# 【核心修复】确保读取指定长度的字节，解决 TCP 分片导致的 unpack 错误
def recv_exact(sock, n):
    data = b''
    while len(data) < n:
        packet = sock.recv(n - len(data))
        if not packet:
            return None  # Connection closed
        data += packet
    return data


def loginRequest(data_bytes):
    result = [True, True]
    return json.dumps(result).encode('utf-8')


def genByImageRequest(data_bytes):
    offset = 0
    try:
        if len(data_bytes) < 4: return b''
        count = struct.unpack('>I', data_bytes[offset:offset + 4])[0]
        offset += 4

        print(f"[*] Processing {count} images...")

        timestamp = get_timestamp_str()
        for i in range(count):
            img_size = struct.unpack('>I', data_bytes[offset:offset + 4])[0]
            offset += 4
            img_data = data_bytes[offset:offset + img_size]
            offset += img_size

            filename = f"received_img_{timestamp}_{i + 1}.jpg"
            filepath = os.path.join(BASE_DIR, filename)
            with open(filepath, 'wb') as f:
                f.write(img_data)
            print(f"    -> Saved: {filename}")

        if os.path.exists(DEMO_GLB_IMG):
            with open(DEMO_GLB_IMG, 'rb') as f:
                return f.read()
        else:
            print("[!] Error: demo1.glb not found on server.")
            return b''

    except Exception as e:
        print(f"[!] Error parsing image request: {e}")
        return b''


def genByVideoRequest(data_bytes):
    try:
        timestamp = get_timestamp_str()
        filename = f"received_vid_{timestamp}.mp4"
        filepath = os.path.join(BASE_DIR, filename)

        print(f"[*] Saving video ({len(data_bytes)} bytes)...")
        with open(filepath, 'wb') as f:
            f.write(data_bytes)
        print(f"    -> Saved: {filename}")

        if os.path.exists(DEMO_GLB_VID):
            with open(DEMO_GLB_VID, 'rb') as f:
                return f.read()
        else:
            print("[!] Error: demo2.glb not found on server.")
            return b''

    except Exception as e:
        print(f"[!] Error processing video request: {e}")
        return b''


def handle_client(conn, addr):
    client_id = f"{addr[0]}:{addr[1]}"
    print(f"[+] Connected: {client_id}")

    try:
        while True:
            # 1. Read Header (5 bytes) using recv_exact
            header_data = recv_exact(conn, 5)
            if not header_data: break

            cmd_type = header_data[0]
            data_length = struct.unpack('>I', header_data[1:5])[0]

            cmd_name = "UNKNOWN"
            if cmd_type == 1:
                cmd_name = "LOGIN"
            elif cmd_type == 2:
                cmd_name = "IMAGE_GEN"
            elif cmd_type == 3:
                cmd_name = "VIDEO_GEN"

            print(f"[*] Request Received: [{cmd_name}] (Type: {cmd_type}), Payload Size: {data_length} bytes")

            # 2. Read Body using recv_exact
            received_data = recv_exact(conn, data_length)
            if received_data is None or len(received_data) != data_length:
                print("[!] Incomplete data received.")
                break

            # 3. Process
            response_data = b''
            target_filename = "NullName"

            if cmd_type == 1:
                response_data = loginRequest(received_data)
            elif cmd_type == 2:
                response_data = genByImageRequest(received_data)
                target_filename = "demo1.glb"
            elif cmd_type == 3:
                response_data = genByVideoRequest(received_data)
                target_filename = "demo2.glb"

            # 4. Response
            if cmd_type == 2 or cmd_type == 3:
                safe_name = setFilename(target_filename)
                name_bytes = safe_name.encode('utf-8')

                conn.sendall(struct.pack('>I', len(name_bytes)))
                conn.sendall(name_bytes)
                conn.sendall(struct.pack('>I', len(response_data)))
                conn.sendall(response_data)
            else:
                conn.sendall(struct.pack('>I', len(response_data)))
                conn.sendall(response_data)

    except Exception as e:
        print(f"[!] Exception: {e}")
    finally:
        conn.close()
        print(f"[-] Disconnected: {client_id}")


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        server.bind((HOST, PORT))
        server.listen(5)
        print(f"==========================================")
        print(f" Python TCP Server Running on Port {PORT}")
        print(f" Waiting for connections...")
        print(f"==========================================")

        while True:
            conn, addr = server.accept()
            t = threading.Thread(target=handle_client, args=(conn, addr))
            t.start()
    except Exception as e:
        print(f"[!] Server Startup Error: {e}")


if __name__ == '__main__':
    main()