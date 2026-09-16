from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path
from typing import Iterable

from app.errors import ConfigurationError, MediaError


def require_ffmpeg(configured: str = "") -> tuple[Path, Path]:
    ffmpeg = _find_binary("ffmpeg", configured)
    if ffmpeg is None:
        raise ConfigurationError(
            "未找到 FFmpeg。请安装 ffmpeg，或在 config.yaml 的 render.ffmpeg_path 中填写路径。"
        )
    ffprobe = _find_binary("ffprobe", str(ffmpeg.parent))
    if ffprobe is None:
        raise ConfigurationError("找到了 ffmpeg，但同目录/PATH 中没有 ffprobe。")
    return ffmpeg, ffprobe


def _find_binary(name: str, configured: str) -> Path | None:
    executable = f"{name}.exe" if os.name == "nt" else name
    if configured:
        candidate = Path(configured).expanduser()
        if candidate.is_dir():
            candidate = candidate / executable
        elif candidate.name.casefold().startswith("ffmpeg") and name == "ffprobe":
            candidate = candidate.with_name(executable)
        if candidate.is_file():
            return candidate.resolve()
    found = shutil.which(name)
    return Path(found).resolve() if found else None


def run_checked(args: Iterable[str | Path], label: str) -> None:
    values = [str(item) for item in args]
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    completed = subprocess.run(
        values,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        creationflags=flags,
    )
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "未知错误").strip()
        raise MediaError(f"{label}失败：{detail[-4000:]}")


def probe_media(path: Path, ffprobe: Path) -> dict:
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    completed = subprocess.run(
        [
            str(ffprobe),
            "-v",
            "error",
            "-show_streams",
            "-show_format",
            "-of",
            "json",
            str(path),
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        creationflags=flags,
    )
    if completed.returncode != 0:
        raise MediaError(f"无法读取视频信息：{path.name}\n{completed.stderr[-2000:]}")
    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise MediaError(f"ffprobe 返回了无效信息：{path.name}") from exc


def media_duration(path: Path, ffprobe: Path) -> float:
    payload = probe_media(path, ffprobe)
    try:
        duration = float(payload.get("format", {}).get("duration", 0))
    except (TypeError, ValueError):
        duration = 0
    if duration <= 0:
        raise MediaError(f"视频时长无效：{path.name}")
    return duration


def has_stream(payload: dict, stream_type: str) -> bool:
    return any(item.get("codec_type") == stream_type for item in payload.get("streams", []))
