from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml
from app.errors import ConfigurationError


PROJECT_ROOT = Path(__file__).resolve().parent.parent


def _mapping(value: Any, name: str) -> dict[str, Any]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise ConfigurationError(f"配置段 {name} 必须是 YAML 对象。")
    return value


@dataclass(frozen=True)
class PathSettings:
    data: str = "data"
    jobs: str = "data/jobs"
    output: str = "data/output"
    music: str = "data/music"


@dataclass(frozen=True)
class RenderSettings:
    width: int = 1080
    height: int = 1920
    fps: int = 30
    video_codec: str = "libx264"
    audio_codec: str = "aac"
    audio_bitrate: str = "192k"
    audio_sample_rate: int = 48000
    crf: int = 20
    preset: str = "veryfast"
    normalize_audio: bool = True
    audio_edge_fade_seconds: float = 0.02
    ffmpeg_path: str = ""
    max_uploads: int = 5
    max_clip_seconds: float = 120.0


@dataclass(frozen=True)
class MusicSettings:
    enabled: bool = True
    volume: float = 0.12
    fade_out_seconds: float = 0.4


@dataclass(frozen=True)
class FontSettings:
    english: str = "C:/Windows/Fonts/arialbd.ttf"
    english_subtitle: str = "C:/Windows/Fonts/timesbd.ttf"
    chinese: str = "C:/Windows/Fonts/simhei.ttf"
    phonetic: str = "C:/Windows/Fonts/arial.ttf"


@dataclass(frozen=True)
class LayoutSettings:
    background_color: str = "#03152B"
    series_color: str = "#6EA8E5"
    series_text: str = "每日一句"
    series_x: int = 66
    series_y: int = 104
    series_font_size: int = 34
    top_english_y: int = 270
    top_english_font_size: int = 76
    top_english_min_font_size: int = 42
    top_english_max_width: int = 1000
    top_chinese_y: int = 368
    top_chinese_font_size: int = 58
    top_chinese_min_font_size: int = 38
    top_chinese_max_width: int = 950
    phonetic_y: int = 458
    phonetic_font_size: int = 45
    phonetic_min_font_size: int = 30
    phonetic_max_width: int = 950
    video_x: int = 40
    video_y: int = 535
    video_width: int = 1000
    video_height: int = 880
    english_subtitle_font_size: int = 58
    english_subtitle_min_font_size: int = 34
    english_subtitle_max_width: int = 900
    english_subtitle_bottom_margin: int = 95
    english_subtitle_stroke_width: int = 3
    chinese_subtitle_y: int = 1337
    chinese_subtitle_font_size: int = 48
    chinese_subtitle_min_font_size: int = 34
    chinese_subtitle_max_width: int = 940
    footer_text: str = "评论区默写一遍，比看三遍都管用哦！"
    footer_y: int = 1485
    footer_font_size: int = 34
    footer_max_width: int = 920
    top_text_color: str = "#FFD400"
    phonetic_color: str = "#F7F8FC"
    subtitle_color: str = "#FFFFFF"
    subtitle_outline_color: str = "#050505"
    footer_color: str = "#F7F8FC"


@dataclass(frozen=True)
class AppSettings:
    root: Path = PROJECT_ROOT
    app_title: str = "每日一句"
    paths: PathSettings = field(default_factory=PathSettings)
    render: RenderSettings = field(default_factory=RenderSettings)
    music: MusicSettings = field(default_factory=MusicSettings)
    fonts: FontSettings = field(default_factory=FontSettings)
    layout: LayoutSettings = field(default_factory=LayoutSettings)

    def path(self, name: str) -> Path:
        value = Path(str(getattr(self.paths, name))).expanduser()
        return value.resolve() if value.is_absolute() else (self.root / value).resolve()

    def ensure_directories(self) -> None:
        for name in ("data", "jobs", "output", "music"):
            self.path(name).mkdir(parents=True, exist_ok=True)

    def font_path(self, name: str) -> Path:
        value = Path(str(getattr(self.fonts, name))).expanduser()
        return value.resolve() if value.is_absolute() else (self.root / value).resolve()

def load_settings(config_path: str | Path | None = None) -> AppSettings:
    path = Path(config_path).resolve() if config_path else PROJECT_ROOT / "config.yaml"
    if not path.is_file():
        raise ConfigurationError(f"配置文件不存在：{path}")
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except (OSError, yaml.YAMLError) as exc:
        raise ConfigurationError(f"无法读取配置文件：{path}") from exc
    if not isinstance(data, dict):
        raise ConfigurationError("config.yaml 顶层必须是 YAML 对象。")

    settings = AppSettings(
        root=PROJECT_ROOT,
        app_title=str(data.get("app_title", "每日一句")).strip() or "每日一句",
        paths=PathSettings(**_mapping(data.get("paths"), "paths")),
        render=RenderSettings(**_mapping(data.get("render"), "render")),
        music=MusicSettings(**_mapping(data.get("music"), "music")),
        fonts=FontSettings(**_mapping(data.get("fonts"), "fonts")),
        layout=LayoutSettings(**_mapping(data.get("layout"), "layout")),
    )
    _validate(settings)
    settings.ensure_directories()
    return settings


def _validate(settings: AppSettings) -> None:
    render = settings.render
    layout = settings.layout
    if (render.width, render.height) != (1080, 1920):
        raise ConfigurationError("当前固定框架要求 render.width/height 为 1080×1920。")
    if render.fps <= 0 or render.max_uploads not in range(1, 11):
        raise ConfigurationError("fps 必须大于 0，max_uploads 必须为 1～10。")
    if render.max_clip_seconds <= 0:
        raise ConfigurationError("max_clip_seconds 必须大于 0。")
    if not 0 <= settings.music.volume <= 1:
        raise ConfigurationError("music.volume 必须在 0～1 之间。")
    if settings.music.fade_out_seconds < 0:
        raise ConfigurationError("music.fade_out_seconds 不能小于 0。")
    if layout.video_width <= 0 or layout.video_height <= 0:
        raise ConfigurationError("视频区域宽高必须大于 0。")
    if layout.video_x < 0 or layout.video_y < 0:
        raise ConfigurationError("视频区域坐标不能为负数。")
    if layout.video_x + layout.video_width > render.width:
        raise ConfigurationError("视频区域超出了画布宽度。")
    if layout.video_y + layout.video_height > render.height:
        raise ConfigurationError("视频区域超出了画布高度。")
    if not 0 <= layout.chinese_subtitle_y < render.height:
        raise ConfigurationError("中文字幕位置超出了画布。")
    for name in ("english", "english_subtitle", "chinese", "phonetic"):
        if not settings.font_path(name).is_file():
            raise ConfigurationError(f"字体文件不存在：{settings.font_path(name)}")
