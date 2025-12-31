"""TCP server bridging CommunicationManager protocol with VGGT + SQLite."""

from __future__ import annotations

import json
import os
import shutil
import socket
import struct
import tempfile
import threading
import time
from pathlib import Path
from typing import Optional

from core.infer import (
    load_model,
    run_model_on_target_dir,
    stage_images_from_dir,
    stage_images_from_video,
)
from database.db import Base, SessionLocal, engine
from database.models import GlbFile, User
from glb_storage import save_glb_with_record
from visual_util import predictions_to_glb

# Helper utilities -----------------------------------------------------------


def get_timestamp_str() -> str:
    return str(int(time.time()))


def setFilename(filename: str) -> str:
    if not filename:
        return "NullName"
    return filename


def recv_exact(sock: socket.socket, size: int) -> Optional[bytes]:
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            return None
        data += chunk
    return data


# Network protocol constants -------------------------------------------------
HOST = "0.0.0.0"
PORT = 4567

CMD_LOGIN = 1
CMD_IMAGE = 2
CMD_VIDEO = 3
CMD_GET_GLB = 4

# Reconstruction defaults ----------------------------------------------------
CHECKPOINT_PATH = os.environ.get("VGGT_CHECKPOINT", "model.pt")
CONF_THRES = 3.0
FRAME_FILTER = "All"
MASK_BLACK_BG = False
MASK_WHITE_BG = False
SHOW_CAM = False
MASK_SKY = False
PREDICTION_MODE = "Pointmap Regression"
VIDEO_FPS = 0.4  # 自己根据演示需求随时改

BASE_DIR = Path(__file__).resolve().parent
_LAST_USER_BY_IP: dict[str, int] = {}
_SESSION_LOCK = threading.Lock()

# Ensure required tables exist (users + glb_files)
Base.metadata.create_all(bind=engine)


class ModelHolder:
    """Lazy VGGT loader shared across handler threads."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._model = None

    def get(self):
        with self._lock:
            if self._model is None:
                if not os.path.isfile(CHECKPOINT_PATH):
                    raise FileNotFoundError(f"Checkpoint not found: {CHECKPOINT_PATH}")
                self._model = load_model(CHECKPOINT_PATH)
            return self._model


MODEL_HOLDER = ModelHolder()


# Login ----------------------------------------------------------------------

def loginRequest(payload: bytes) -> tuple[bytes, Optional[int]]:
    try:
        data = json.loads(payload.decode("utf-8"))
    except json.JSONDecodeError:
        return json.dumps([False, False]).encode("utf-8"), None

    username = data.get("u")
    password = data.get("p")
    if not username or not password:
        return json.dumps([False, False]).encode("utf-8"), None

    db = SessionLocal()
    try:
        user: Optional[User] = db.query(User).filter(User.username == username).first()
        ok = bool(user and user.password_hash == password)
        resp_arr = [ok, bool(user.is_vip) if ok and user else False]
        user_id = int(user.id) if ok and user else None
        return json.dumps(resp_arr).encode("utf-8"), user_id
    except:
        return json.dumps([False, False]).encode("utf-8"), None
    finally:
        db.close()


# Payload staging ------------------------------------------------------------

def _save_images_payload(payload: bytes) -> Path:
    offset = 0
    if len(payload) < 4:
        raise ValueError("Invalid payload: missing count")
    count = struct.unpack(">I", payload[offset : offset + 4])[0]
    offset += 4
    if count <= 0:
        raise ValueError("Image count must be positive")

    tmp_dir = Path(tempfile.mkdtemp(prefix="mgr_images_", dir=str(BASE_DIR)))
    for idx in range(count):
        if offset + 4 > len(payload):
            raise ValueError("Payload truncated (size)")
        img_size = struct.unpack(">I", payload[offset : offset + 4])[0]
        offset += 4
        if offset + img_size > len(payload):
            raise ValueError("Payload truncated (data)")
        img_bytes = payload[offset : offset + img_size]
        offset += img_size

        out_path = tmp_dir / f"img_{idx:03}.jpg"
        with open(out_path, "wb") as fp:
            fp.write(img_bytes)
    return tmp_dir


def _save_video_payload(payload: bytes) -> Path:
    fd, tmp_path = tempfile.mkstemp(prefix="mgr_video_", suffix=".mp4", dir=str(BASE_DIR))
    with os.fdopen(fd, "wb") as fp:
        fp.write(payload)
    return Path(tmp_path)


def _build_scene(target_dir: str):
    model = MODEL_HOLDER.get()
    predictions = run_model_on_target_dir(target_dir, model)
    return predictions_to_glb(
        predictions,
        conf_thres=CONF_THRES,
        filter_by_frames=FRAME_FILTER,
        mask_black_bg=MASK_BLACK_BG,
        mask_white_bg=MASK_WHITE_BG,
        show_cam=SHOW_CAM,
        mask_sky=MASK_SKY,
        target_dir=target_dir,
        prediction_mode=PREDICTION_MODE,
    )


def _store_and_read(glb_scene, *, user_id: int, prefix: str) -> tuple[bytes, bytes]:
    original_name = setFilename(f"{prefix}_{int(time.time())}.glb")
    record = save_glb_with_record(glb_scene, user_id=user_id, original_name=original_name)
    abs_path = (BASE_DIR / record.file_path).resolve()
    name_bytes = setFilename(f"{record.hashed_name}.glb").encode("utf-8")
    return name_bytes, abs_path.read_bytes()


# Reconstruction handlers ----------------------------------------------------

def genByImageRequest(payload: bytes, user_id: Optional[int]) -> tuple[bytes, bytes]:
    if not user_id:
        return b"", b"ERROR_NOT_LOGGED_IN"

    staging_dir: Optional[Path] = None
    # target_dir: Optional[str] = None
    target_dir = stage_images_from_video(str(video_path), fps=VIDEO_FPS)

    try:
        staging_dir = _save_images_payload(payload)
        target_dir = stage_images_from_dir(str(staging_dir))
        print("[*] Starting VGGT inference for images")
        glb_scene = _build_scene(target_dir)
        print("[*] VGGT inference finished for images")
        return _store_and_read(glb_scene, user_id=user_id, prefix="images")
    except Exception as exc:  # pragma: no cover - runtime logging
        import traceback
        traceback.print_exc()
        print(f"[!] genByImageRequest error: {exc}")
        return b"", b"ERROR_IMAGE_REQUEST"
    finally:
        if target_dir:
            shutil.rmtree(target_dir, ignore_errors=True)
        if staging_dir:
            shutil.rmtree(staging_dir, ignore_errors=True)


def genByVideoRequest(payload: bytes, user_id: Optional[int]) -> tuple[bytes, bytes]:
    if not user_id:
        return b"", b"ERROR_NOT_LOGGED_IN"

    video_path: Optional[Path] = None
    # target_dir: Optional[str] = None
    target_dir = stage_images_from_video(str(video_path), fps=VIDEO_FPS)
    try:
        video_path = _save_video_payload(payload)
        target_dir = stage_images_from_video(str(video_path))
        print("[*] Starting VGGT inference for video")
        glb_scene = _build_scene(target_dir)
        print("[*] VGGT inference finished for video")
        return _store_and_read(glb_scene, user_id=user_id, prefix="video")
    except Exception as exc:  # pragma: no cover
        import traceback
        traceback.print_exc()
        print(f"[!] genByVideoRequest error: {exc}")
        return b"", b"ERROR_VIDEO_REQUEST"
    finally:
        if target_dir:
            shutil.rmtree(target_dir, ignore_errors=True)
        if video_path and video_path.exists():
            video_path.unlink(missing_ok=True)


def getGLBRequest(payload: bytes) -> bytes:
    hashed = payload.decode("utf-8").strip()
    if not hashed:
        return b"ERROR_INVALID_HASH"

    db = SessionLocal()
    try:
        record: Optional[GlbFile] = db.query(GlbFile).filter(GlbFile.hashed_name == hashed).first()
        if not record:
            return b"ERROR_HASH_NOT_FOUND"
        abs_path = (BASE_DIR / record.file_path).resolve()
        if not abs_path.exists():
            return b"ERROR_FILE_MISSING"
        return abs_path.read_bytes()
    finally:
        db.close()


# Socket server loop ---------------------------------------------------------

def handle_client(conn: socket.socket, addr) -> None:
    client_id = f"{addr[0]}:{addr[1]}"
    print(f"[+] Connected: {client_id}")
    with _SESSION_LOCK:
        current_user_id: Optional[int] = _LAST_USER_BY_IP.get(addr[0])

    try:
        while True:
            header = recv_exact(conn, 5)
            if not header:
                break
            cmd_type = header[0]
            data_length = struct.unpack(">I", header[1:5])[0]
            payload = recv_exact(conn, data_length)
            if payload is None or len(payload) != data_length:
                print("[!] Incomplete payload received.")
                break

            print(f"[*] Command received: type={cmd_type}, len={data_length}, current_user={current_user_id}")
            if cmd_type == CMD_LOGIN:
                resp_bytes, user_id = loginRequest(payload)
                if user_id is not None:
                    current_user_id = user_id
                    with _SESSION_LOCK:
                        _LAST_USER_BY_IP[addr[0]] = user_id
                response = resp_bytes
            elif cmd_type == CMD_IMAGE:
                name_bytes, payload_bytes = genByImageRequest(payload, current_user_id)
                conn.sendall(struct.pack(">I", len(name_bytes)))
                if name_bytes:
                    conn.sendall(name_bytes)
                conn.sendall(struct.pack(">I", len(payload_bytes)))
                conn.sendall(payload_bytes)
                continue
            elif cmd_type == CMD_VIDEO:
                name_bytes, payload_bytes = genByVideoRequest(payload, current_user_id)
                conn.sendall(struct.pack(">I", len(name_bytes)))
                if name_bytes:
                    conn.sendall(name_bytes)
                conn.sendall(struct.pack(">I", len(payload_bytes)))
                conn.sendall(payload_bytes)
                continue
            elif cmd_type == CMD_GET_GLB:
                response = getGLBRequest(payload)
            else:
                response = b"UNKNOWN_COMMAND"

            conn.sendall(struct.pack(">I", len(response)))
            conn.sendall(response)
    except Exception as exc:  # pragma: no cover
        print(f"[!] Client handler error: {exc}")
    finally:
        conn.close()
        print(f"[-] Disconnected: {client_id}")


def main() -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        server.bind((HOST, PORT))
        server.listen(5)
        print("==========================================")
        print(f" Manager TCP Server running on port {PORT}")
        print(" Waiting for connections...")
        print("==========================================")
        while True:
            conn, addr = server.accept()
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
    except Exception as exc:
        print(f"[!] Server error: {exc}")
    finally:
        server.close()


if __name__ == "__main__":
    main()
