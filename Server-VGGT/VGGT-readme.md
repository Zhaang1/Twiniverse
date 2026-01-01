## VGGT Backend Operation Guide

This guide focuses exclusively on the backend components within this repository (TCP Service + Inference + Database). By following these steps, you can successfully execute login, video/image reconstruction, and GLB retrieval without modifying the existing code.

### 1. Environment & Dependencies
-   **Python 3.10+**: It is recommended to use a virtual environment: `python -m venv .venv && .venv/Scripts/activate` (Windows) or `source .venv/bin/activate` (Linux/macOS).
-   **Hardware**: A GPU and CUDA drivers are required for inference. Ensure `model.pt` is placed in the repository root.
-   **Installation**: Run `pip install -r requirements.txt` (Run `pip install -r requirements_demo.txt` if additional demo dependencies are needed).

### 1.1 Download Pretrained Weights (`model.pt`)

The backend inference code expects a **PyTorch checkpoint file** named **`model.pt`** placed in the **repository root** (same directory level as `Manager.py`). If the model file is missing or not found, inference will not start.

You can obtain `model.pt` in either of the following ways:

**Option A — Auto-download via `from_pretrained` (recommended)**
This method uses Hugging Face caching and is the most reliable if your environment can access Hugging Face.

```bash
python - <<'PY'
from vggt.models.vggt import VGGT
# The first run will download weights and may take a while.
VGGT.from_pretrained("facebook/VGGT-1B")
print("Done: weights downloaded (Hugging Face cache).")
PY
```

After the download completes, **copy the downloaded `model.pt` to the repository root** and ensure its name is exactly:

```
model.pt
```

**Option B — Manual download from Hugging Face**
If auto-download is slow or blocked, manually download **`model.pt`** from the Hugging Face model page for `facebook/VGGT-1B`, then place it in the repository root and rename to `model.pt` if needed.

**Option C — Commercial checkpoint**
If your use case requires a commercial license, apply for access to the **`VGGT-1B-Commercial`** checkpoint on Hugging Face and download the corresponding `model.pt`. Replace the repository-root `model.pt` with the commercial checkpoint file.

> Tip: Keep only one active checkpoint at a time (one `model.pt` in the repository root) to avoid confusion.


### 2. Initialize Database
The backend utilizes SQLite (default path: `./vggt_app.db`, relative to the current working directory). **Ensure all operations are performed from the repository root.**

1) **Create Tables** (Optional, scripts generally handle this automatically):
```bash
python - <<'PY'
from database.db import Base, engine
Base.metadata.create_all(bind=engine)
PY
```

2) **Create User** (Passwords are stored in plain text for the testing phase):
```bash
python database/create_user.py demo 123456 true   # Username: demo, Password: 123456, VIP: Yes
```

3) **Verify User List** (Confirm you are writing to the correct database file):
```bash
python - <<'PY'
import sqlite3, os
db = sqlite3.connect('vggt_app.db')
print("DB path:", os.path.abspath('vggt_app.db'))
print(db.execute("select id, username, password_hash, is_vip from users").fetchall())
db.close()
PY
```

### 3. Start Backend Service (TCP)
Execute the following command from the repository root:
```bash
python Manager.py
```
Keep the window open. Successful listening is indicated by the output: `Manager TCP Server running on port 4567`.

**`Manager.py` Protocol and Commands:**
-   `CMD_LOGIN=1`: Payload is JSON `{"u":"username","p":"password"}`. Returns a flat boolean array `[ok, is_vip]`.
-   `CMD_IMAGE=2`: Sends 4-byte image count, followed by a loop of [4-byte file length + file data]. Returns "filename length + filename + GLB length + GLB binary".
-   `CMD_VIDEO=3`: Payload is the entire video byte stream. Returns format identical to `CMD_IMAGE`.
-   `CMD_GET_GLB=4`: Payload is the hash string. Returns the corresponding GLB file.

> **Note:** The default video frame extraction rate is controlled by the `VIDEO_FPS` constant in `Manager.py` (Current value: 0.4; lower values result in fewer frames and faster processing).

### 4. Local Connectivity Self-Test
Open a separate terminal (keeping the service running) and use the following Python script to perform a "Login + Send Video + Save Returned GLB" workflow:

```bash
python - <<'PY'
import json, socket, struct, pathlib
video_path = r"D:\PythonProjects\VGGT\1.mp4"   # Replace with your test video path
output_glb = pathlib.Path("test_return.glb")
with socket.create_connection(("127.0.0.1", 4567)) as s:
    # Login
    login_payload = json.dumps({"u": "demo", "p": "123456"}).encode()
    s.sendall(struct.pack(">BI", 1, len(login_payload)) + login_payload)
    resp_len = struct.unpack(">I", s.recv(4))[0]
    resp = s.recv(resp_len)
    print("Login reply:", resp)
    
    # Send Video
    data = pathlib.Path(video_path).read_bytes()
    s.sendall(struct.pack(">BI", 3, len(data)))
    s.sendall(data)
    
    # Receive Filename + GLB
    name_len = struct.unpack(">I", s.recv(4))[0]
    raw_name = s.recv(name_len)
    file_name = raw_name.decode("utf-8", errors="ignore") or raw_name.hex()
    resp_len = struct.unpack(">I", s.recv(4))[0]
    glb_bytes = s.recv(resp_len)
    output_glb.write_bytes(glb_bytes)
    print("Server file name:", file_name)
    print("Saved GLB to:", output_glb.resolve())
PY
```
If the GLB is saved successfully and the server log contains "Starting/finished VGGT inference", the inference pipeline is functioning correctly.

### 5. Independent Export Test (TCP-Free)
To verify inference/export functionality in isolation, you can use `export_glb.py` directly:

-   **Video Input to File:**
```bash
python export_glb.py --video_path "D:\PythonProjects\VGGT\1.mp4" --out outputs --fps 0.4 --prediction_mode "Predicted Pointmap"
```

-   **Video Input to Database** (Automatically stored in `out/YYYY-MM-DD/{hash}.glb`):
```bash
python export_glb.py --video_path "D:\PythonProjects\VGGT\1.mp4" --save_to_db --user_id 1 --original_name demo.glb --fps 0.4
```

**Adjustable Parameters:**
-   `--fps`: Lower values = faster speed, fewer frames.
-   `--conf_thres`: Higher values = faster speed, more points discarded.
-   `--prediction_mode`: `Predicted Pointmap` is lighter.
-   Other parameters like `mask` or `show_cam` default to off.

### 6. File Generation & Database Structure
-   **GLB Entity:** Stored in `out/YYYY-MM-DD/{hashed_name}.glb`.
-   **Metadata:** Stored in SQLite table `glb_files` (Fields: `hashed_name`, `original_name`, `file_path`, `user_id`, `file_size`, `created_at`, `is_public`).

### 7. Troubleshooting
-   **Login Failed / User Not Found:** Confirm that `Manager.py` and `create_user.py` are run from the same directory and ensure both point to the same `vggt_app.db`.
-   **No Inference Logs / GPU Idle:** Ensure login occurs before sending video on the same connection; confirm `model.pt` exists and CUDA is available; check the `Manager.py` console for exception stack traces.
-   **File Too Small / Few Bytes:** This usually indicates a "Not Logged In" error code return, or the client failed to read the protocol order "Filename + Length + Data" correctly.

### 8. License Notice
The `LICENSE.txt` file in the repository root is the **VGGT License** (published by Meta), **not** the MIT License. Please read and adhere to its terms for commercial or demonstration use.

### 9. Resource Monitoring Script (`Monitor.py`) (Optional)

To help profile backend inference performance, we provide a lightweight monitoring script `Monitor.py`.

**What it does**
- Prompts you to enter the number of monitoring sessions `n` (recommended: 1–5).
- For each session:
  - Press **Enter** to **start** monitoring.
  - Press **Enter** again to **stop** monitoring.
- While monitoring is active, it samples the following resources every **0.1s**:
  - **CPU utilization (%)**
  - **GPU utilization (%)** *(NVIDIA only)*
  - **VRAM usage (GB)** *(NVIDIA only)*
  - **System RAM usage (GB)**

After all sessions complete, it prints simple statistics and displays plots for each metric.

**Dependencies**
- Required: `psutil`, `matplotlib`, `numpy`
- Optional (NVIDIA GPU metrics): `pynvml` (and an NVIDIA driver)

If GPU is not NVIDIA or `pynvml` is not available, GPU/VRAM metrics will show as `0`.

**How to run**
From the repository root:

```bash
python Monitor.py
```

**Suggested usage**
- Start `Manager.py` in one terminal.
- Run `Monitor.py` in another terminal.
- Start a monitoring session right before sending a video/image request, then stop it after inference finishes.
