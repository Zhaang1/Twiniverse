"""Utility helpers for persisting GLB files and metadata."""

from __future__ import annotations

import hashlib
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from database.db import SessionLocal
from database.models import GlbFile

LOGGER = logging.getLogger(__name__)
PROJECT_ROOT = Path(__file__).resolve().parent
RELATIVE_OUT_ROOT = Path("out")


def _render_relative_path(hashed_name: str, timestamp: datetime) -> tuple[Path, Path]:
    date_dir = timestamp.strftime("%Y-%m-%d")
    relative_dir = RELATIVE_OUT_ROOT / date_dir
    relative_path = relative_dir / f"{hashed_name}.glb"
    absolute_path = PROJECT_ROOT / relative_path
    return relative_path, absolute_path


def save_glb_with_record(
    glb_scene,
    *,
    user_id: int,
    original_name: str,
    session: Optional[Session] = None,
) -> GlbFile:
    """Export a GLB scene into ``out/`` and create a DB record.

    Returns the stored :class:`GlbFile` ORM instance (with refreshed fields).
    """

    timestamp = datetime.now(timezone.utc)
    timestamp_str = timestamp.isoformat()
    payload = f"{user_id}:{timestamp_str}:{original_name}".encode("utf-8")
    hashed_name = hashlib.sha256(payload).hexdigest()
    relative_path, absolute_path = _render_relative_path(hashed_name, timestamp)
    absolute_path.parent.mkdir(parents=True, exist_ok=True)
    glb_scene.export(file_obj=str(absolute_path))
    file_size = absolute_path.stat().st_size
    record = GlbFile(
        hashed_name=hashed_name,
        original_name=original_name,
        file_path=relative_path.as_posix(),
        user_id=user_id,
        file_size=file_size,
        created_at=timestamp,
        is_public=False,
    )

    created_session = False
    if session is None:
        session = SessionLocal()
        created_session = True

    try:
        session.add(record)
        session.commit()
        session.refresh(record)
        return record
    except SQLAlchemyError as exc:  # pragma: no cover - defensive cleanup
        session.rollback()
        try:
            if absolute_path.exists():
                absolute_path.unlink()
        except OSError as cleanup_err:
            LOGGER.warning("Failed to delete GLB file '%s': %s", absolute_path, cleanup_err)
        raise RuntimeError("Failed to store GLB metadata") from exc
    finally:
        if created_session:
            session.close()

