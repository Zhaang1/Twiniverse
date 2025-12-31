import os
import glob
import shutil
from typing import Optional, Tuple

import cv2
import numpy as np
import torch

# Ensure local package imports work when used as a script
import sys as _sys
_sys.path.append("vggt/")

from vggt.models.vggt import VGGT
from vggt.utils.load_fn import load_and_preprocess_images
from vggt.utils.pose_enc import pose_encoding_to_extri_intri
from vggt.utils.geometry import unproject_depth_map_to_point_map


def load_model(checkpoint_path: str, device: Optional[str] = None) -> VGGT:
    """Load VGGT from a checkpoint path and move to device.

    Args:
        checkpoint_path: Path to `model.pt` weights.
        device: 'cuda' or 'cpu'. If None, auto-detect.

    Returns:
        An eval-mode VGGT instance on the chosen device.
    """
    device = device or ("cuda" if torch.cuda.is_available() else "cpu")

    # Load weights to CPU first (safer), then move model to device
    state_dict = torch.load(checkpoint_path, map_location="cpu")
    model = VGGT()
    model.load_state_dict(state_dict)
    model.eval()
    return model.to(device)


def _ensure_fresh_dir(path: str) -> None:
    if os.path.exists(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def stage_images_from_dir(images_dir: str, target_dir: Optional[str] = None) -> str:
    """Copy images from `images_dir` into a fresh `<target_dir>/images` folder.

    Returns the created `target_dir` path.
    """
    if not os.path.isdir(images_dir):
        raise ValueError(f"images_dir not found: {images_dir}")

    target_dir = target_dir or os.path.abspath(f"input_images_{os.getpid()}_{int(torch.randint(0, 1_000_000, (1,)).item())}")
    images_out = os.path.join(target_dir, "images")
    _ensure_fresh_dir(images_out)

    for p in sorted(glob.glob(os.path.join(images_dir, "*"))):
        if os.path.isfile(p):
            shutil.copy(p, os.path.join(images_out, os.path.basename(p)))
    return target_dir


def stage_images_from_video(video_path: str, target_dir: Optional[str] = None, fps: float = 1.0) -> str:
    """Extract frames from video at `fps` and place into `<target_dir>/images`.

    Returns the created `target_dir` path.
    """
    if not os.path.isfile(video_path):
        raise ValueError(f"video_path not found: {video_path}")

    target_dir = target_dir or os.path.abspath(f"input_images_{os.getpid()}_{int(torch.randint(0, 1_000_000, (1,)).item())}")
    images_out = os.path.join(target_dir, "images")
    _ensure_fresh_dir(images_out)

    vs = cv2.VideoCapture(video_path)
    if not vs.isOpened():
        raise ValueError(f"Failed to open video: {video_path}")

    src_fps = vs.get(cv2.CAP_PROP_FPS) or 30.0
    frame_interval = max(int(round(src_fps / max(fps, 1e-6))), 1)

    count = 0
    saved = 0
    while True:
        ok, frame = vs.read()
        if not ok:
            break
        count += 1
        if count % frame_interval == 0:
            out_path = os.path.join(images_out, f"{saved:06}.png")
            # Try saving with OpenCV; if it fails (often due to unicode path issues on Windows),
            # fall back to encoding in-memory and writing via Python IO.
            ok_write = bool(cv2.imwrite(out_path, frame))
            if not ok_write:
                try:
                    ok_buf, buf = cv2.imencode(".png", frame)
                    if ok_buf:
                        with open(out_path, "wb") as f:
                            f.write(buf.tobytes())
                        ok_write = True
                except Exception:
                    ok_write = False

            if ok_write and os.path.exists(out_path):
                saved += 1
    vs.release()

    if saved == 0:
        raise RuntimeError(
            "No frames extracted from video. Possible causes: unsupported codec or write failure on non-ASCII paths. "
            "Try installing codecs, converting the video, or pass an ASCII-only --work_dir like C:\\vggt_work."
        )
    return target_dir


def run_model_on_target_dir(target_dir: str, model: VGGT) -> dict:
    """Run VGGT on images under `<target_dir>/images` and return predictions dict.

    The returned dict is ready for `visual_util.predictions_to_glb`.
    """
    if not torch.cuda.is_available():
        raise ValueError("CUDA is not available. Please ensure a CUDA-enabled environment.")

    device = next(model.parameters()).device

    image_names = sorted(glob.glob(os.path.join(target_dir, "images", "*")))
    if len(image_names) == 0:
        raise ValueError(f"No images found in {os.path.join(target_dir, 'images')}")

    images = load_and_preprocess_images(image_names).to(device)

    # Use bfloat16 for >= sm_80 GPUs, else float16
    dtype = torch.bfloat16 if torch.cuda.get_device_capability()[0] >= 8 else torch.float16

    with torch.no_grad():
        with torch.cuda.amp.autocast(dtype=dtype):
            predictions = model(images)

    # Derive camera extrinsics/intrinsics and add to predictions
    extrinsic, intrinsic = pose_encoding_to_extri_intri(predictions["pose_enc"], images.shape[-2:])
    predictions["extrinsic"] = extrinsic
    predictions["intrinsic"] = intrinsic

    # Convert tensors to numpy and remove batch dimension
    for k in list(predictions.keys()):
        if isinstance(predictions[k], torch.Tensor):
            predictions[k] = predictions[k].detach().cpu().numpy().squeeze(0)
    # Optional cleanup of large lists
    if "pose_enc_list" in predictions:
        predictions["pose_enc_list"] = None

    # Compute world points from depth
    depth_map = predictions["depth"]
    world_points = unproject_depth_map_to_point_map(depth_map, predictions["extrinsic"], predictions["intrinsic"])
    predictions["world_points_from_depth"] = world_points

    torch.cuda.empty_cache()
    return predictions


__all__ = [
    "load_model",
    "stage_images_from_dir",
    "stage_images_from_video",
    "run_model_on_target_dir",
]
