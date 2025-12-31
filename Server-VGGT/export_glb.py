import argparse
import os
import time
from datetime import datetime

import numpy as np
import trimesh

from core.infer import (
    load_model,
    run_model_on_target_dir,
    stage_images_from_dir,
    stage_images_from_video,
)
from visual_util import predictions_to_glb
from glb_storage import save_glb_with_record


def _resolve_output_path(out_arg: str, target_dir: str, conf: float, frame_filter: str, maskb: bool, maskw: bool,
                         show_cam: bool, mask_sky: bool, prediction_mode: str) -> str:
    """Resolve final .glb output path from an arg which may be a directory or file path."""
    if out_arg.lower().endswith(".glb"):
        out_dir = os.path.dirname(os.path.abspath(out_arg)) or os.getcwd()
        os.makedirs(out_dir, exist_ok=True)
        return os.path.abspath(out_arg)

    out_dir = os.path.abspath(out_arg)
    os.makedirs(out_dir, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    base = f"glbscene_{conf}_{frame_filter.replace('.', '_').replace(':', '').replace(' ', '_')}" \
           f"_maskb{int(maskb)}_maskw{int(maskw)}_cam{int(show_cam)}_sky{int(mask_sky)}" \
           f"_pred{prediction_mode.replace(' ', '_')}_{stamp}.glb"
    return os.path.join(out_dir, base)


def main():
    parser = argparse.ArgumentParser(description="Export GLB from images or a video using VGGT")
    src = parser.add_mutually_exclusive_group(required=True)
    src.add_argument(
        "--images_dir",
        type=str,
        help="Directory of input images (or a video file path, automatically detected)",
    )
    src.add_argument("--video_path", type=str, help="Path to input video")

    parser.add_argument("--checkpoint", type=str, default="model.pt", help="Path to VGGT checkpoint (model.pt)")
    parser.add_argument("--out", type=str, required=False, default=None,
                        help="Output .glb file or directory (ignored when --save_to_db is set)")

    # Visualization / filtering params to pass through
    parser.add_argument("--conf_thres", type=float, default=3.0, help="Confidence threshold (percent to drop)")
    parser.add_argument("--frame_filter", type=str, default="All", help="All or index like '3: filename.png'")
    parser.add_argument("--mask_black_bg", action="store_true", help="Mask black background")
    parser.add_argument("--mask_white_bg", action="store_true", help="Mask white background")
    parser.add_argument("--show_cam", action="store_true", help="Show camera frustums")
    parser.add_argument("--mask_sky", action="store_true", help="Apply sky segmentation mask (downloads onnx if missing)")
    parser.add_argument("--prediction_mode", type=str, default="Pointmap Regression",
                        choices=["Predicted Pointmap", "Pointmap Regression", "Depthmap and Camera"],
                        help="Which branch to visualize in GLB")

    parser.add_argument("--fps", type=float, default=1.0, help="FPS for frame extraction when using a video")
    parser.add_argument("--work_dir", type=str, default=None, help="Optional working dir to stage inputs")
    parser.add_argument("--save_to_db", action="store_true",
                        help="Store the resulting GLB in the local database using hashed filenames")
    parser.add_argument("--user_id", type=int, default=None, help="User ID owning the GLB record")
    parser.add_argument("--original_name", type=str, default=None,
                        help="Original file name recorded alongside the GLB (required with --save_to_db)")

    args = parser.parse_args()

    if not os.path.isfile(args.checkpoint):
        raise SystemExit(f"Checkpoint not found: {args.checkpoint}")

    if args.save_to_db:
        if args.user_id is None:
            raise SystemExit("--user_id is required when --save_to_db is enabled")
        if not args.original_name:
            raise SystemExit("--original_name is required when --save_to_db is enabled")
    elif not args.out:
        raise SystemExit("--out must be specified when --save_to_db is disabled")

    start = time.time()
    # Stage inputs
    if args.images_dir:
        if os.path.isdir(args.images_dir):
            target_dir = stage_images_from_dir(args.images_dir, args.work_dir)
        elif os.path.isfile(args.images_dir):
            target_dir = stage_images_from_video(args.images_dir, args.work_dir, fps=args.fps)
        else:
            raise SystemExit(f"Input path not found: {args.images_dir}")
    else:
        target_dir = stage_images_from_video(args.video_path, args.work_dir, fps=args.fps)

    # Load model and run
    model = load_model(args.checkpoint)
    predictions = run_model_on_target_dir(target_dir, model)

    # Save predictions for reuse
    np.savez(os.path.join(target_dir, "predictions.npz"), **predictions)

    # Build scene and export
    glb_scene: trimesh.Scene = predictions_to_glb(
        predictions,
        conf_thres=args.conf_thres,
        filter_by_frames=args.frame_filter,
        mask_black_bg=args.mask_black_bg,
        mask_white_bg=args.mask_white_bg,
        show_cam=args.show_cam,
        mask_sky=args.mask_sky,
        target_dir=target_dir,
        prediction_mode=args.prediction_mode,
    )

    if args.save_to_db:
        record = save_glb_with_record(
            glb_scene,
            user_id=args.user_id,
            original_name=args.original_name,
        )
        elapsed = time.time() - start
        print(
            "GLB stored in DB: hashed={hashed_name}, path={path} (elapsed: {elapsed:.2f}s)".format(
                hashed_name=record.hashed_name,
                path=record.file_path,
                elapsed=elapsed,
            )
        )
    else:
        out_path = _resolve_output_path(
            args.out,
            target_dir,
            args.conf_thres,
            args.frame_filter,
            args.mask_black_bg,
            args.mask_white_bg,
            args.show_cam,
            args.mask_sky,
            args.prediction_mode,
        )

        glb_scene.export(file_obj=out_path)
        elapsed = time.time() - start
        print(f"GLB saved to: {out_path} (elapsed: {elapsed:.2f}s)")


if __name__ == "__main__":
    main()
