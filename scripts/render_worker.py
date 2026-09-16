from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

PROJECT_DIRECTORY = Path(__file__).resolve().parent.parent
if str(PROJECT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIRECTORY))

from app.config import PROJECT_ROOT, load_settings
from app.models import SentenceContent
from app.renderer import DailySentenceRenderer
from app.storage import JobWorkspace, MUSIC_TRACKS


VIDEO_EXTENSIONS = {".mp4", ".mov", ".mkv", ".webm"}


def emit(value: dict[str, object]) -> None:
    sys.stdout.write(json.dumps(value, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def allowed(path: Path, parent: Path) -> bool:
    try:
        path.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description="每日一句 Java 渲染 Worker")
    parser.add_argument("--request-file", required=True)
    args = parser.parse_args()
    request_file = Path(args.request_file).resolve()
    project_root = PROJECT_ROOT.resolve()
    java_jobs = (project_root / "data" / "java-jobs").resolve()
    output_root = (project_root / "data" / "output").resolve()
    if not allowed(request_file, java_jobs) or not request_file.is_file():
        emit({"type": "error", "code": "INVALID_ARGUMENT", "message": "请求文件不在 Java 任务目录", "retryable": False})
        return 64

    try:
        request = json.loads(request_file.read_text(encoding="utf-8"))
        task_id = str(request["taskId"])
        task_dir = request_file.parent.resolve()
        content_value = request["content"]
        content = SentenceContent.create(
            content_value["sentence"],
            content_value["chineseTranslation"],
            content_value["phonetic"],
            content_value.get("videoDescription", ""),
        )
        sources = [Path(value).resolve() for value in request["sources"]]
        output = Path(request["output"]).resolve()
        if not sources or any(not allowed(path, task_dir) or path.suffix.casefold() not in VIDEO_EXTENSIONS or not path.is_file() for path in sources):
            raise ValueError("素材路径无效或越过当前任务目录")
        if not allowed(output, output_root):
            raise ValueError("输出路径越过成片目录")

        settings = load_settings()
        job = JobWorkspace(task_id, task_dir)
        (task_dir / "clips").mkdir(parents=True, exist_ok=True)
        music_tag = str(request.get("musicTag", "HAPPY_DAILY")).lower()
        track = MUSIC_TRACKS.get(music_tag)
        music_path = settings.path("music") / track[1] if settings.music.enabled and track else None
        if music_path is not None and not music_path.is_file():
            raise ValueError(f"音乐标签 {music_tag} 对应文件不存在")

        emit({"type": "progress", "percent": 2, "stage": "RENDER_VIDEO", "message": "开始渲染"})
        result = DailySentenceRenderer(settings).render(
            content,
            sources,
            job,
            output,
            lambda value, message: emit({"type": "progress", "percent": round(value * 100), "stage": "RENDER_VIDEO", "message": message}),
            music_path=music_path,
        )
        emit({"type": "result", "status": "SUCCESS", "data": {"output": str(result.output), "duration": result.duration}})
        return 0
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        emit({"type": "error", "code": "INVALID_ARGUMENT", "message": str(error), "retryable": False})
        return 64
    except Exception as error:  # Worker 边界只返回脱敏错误，不输出环境变量和密钥。
        emit({"type": "error", "code": "FFMPEG_FAILED", "message": str(error), "retryable": True})
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
