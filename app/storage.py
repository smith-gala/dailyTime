from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from app.config import AppSettings


MUSIC_TRACKS = {
    "love_warm": ("唯一", "唯一.mp3"),
    "hope_motivation": ("夜空中最亮的星", "夜空中最亮的星.mp3"),
    "happy_daily": ("有何不可", "有何不可.mp3"),
    "sad_reflection": ("lll", "lll.mp3"),
    "nostalgia_life": ("起风了", "起风了.mp3"),
}


def write_json(path: Path, data: object) -> None:
    """Atomically persist a renderer artifact inside its task workspace."""

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    temporary.replace(path)


@dataclass(frozen=True)
class JobWorkspace:
    """Filesystem workspace used only by the Python/FFmpeg media worker."""

    job_id: str
    path: Path

    @classmethod
    def create(cls, settings: AppSettings) -> "JobWorkspace":
        job_id = f"{datetime.now():%Y%m%d_%H%M%S}_{uuid4().hex[:6]}"
        path = settings.path("jobs") / job_id
        for name in ("uploads", "clips"):
            (path / name).mkdir(parents=True, exist_ok=True)
        return cls(job_id, path.resolve())

    def file(self, name: str) -> Path:
        return self.path / name
