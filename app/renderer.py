from __future__ import annotations

from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFont

from app.config import AppSettings
from app.errors import MediaError
from app.media import has_stream, media_duration, probe_media, require_ffmpeg, run_checked
from app.models import RenderResult, SentenceContent
from app.storage import JobWorkspace, write_json


ProgressCallback = Callable[[float, str], None]


class DailySentenceRenderer:
    """Burn one persistent 9:16 frame and fixed bilingual copy onto every clip."""

    def __init__(self, settings: AppSettings):
        self.settings = settings
        self.ffmpeg, self.ffprobe = require_ffmpeg(settings.render.ffmpeg_path)

    def render(
        self,
        content: SentenceContent,
        sources: list[Path],
        job: JobWorkspace,
        output: Path,
        progress: ProgressCallback | None = None,
        *,
        music_path: Path | None = None,
        music_volume: float | None = None,
    ) -> RenderResult:
        maximum = self.settings.render.max_uploads
        if not 1 <= len(sources) <= maximum:
            raise MediaError(f"请上传 1～{maximum} 个视频。")
        overlay = job.file("frame.png")
        self.build_frame(content, overlay)
        self._progress(progress, 0.03, "固定竖屏框架已生成")

        rendered: list[Path] = []
        durations: list[float] = []
        for index, source in enumerate(sources, start=1):
            destination = job.file("clips") / f"{index:02d}.mp4"
            duration = self._render_clip(source, overlay, destination)
            rendered.append(destination)
            durations.append(duration)
            self._progress(
                progress,
                0.05 + 0.78 * index / len(sources),
                f"已完成字幕烧录 {index}/{len(sources)}",
            )

        output.parent.mkdir(parents=True, exist_ok=True)
        self._progress(progress, 0.86, "正在按上传顺序拼接视频")
        joined = job.file("joined.mp4") if music_path else output
        self._concat(rendered, joined, job.file("concat.txt"))
        if music_path:
            joined_duration = media_duration(joined, self.ffprobe)
            self._progress(progress, 0.93, "正在加入背景音乐")
            self._mix_background_music(
                joined,
                music_path,
                output,
                joined_duration,
                self.settings.music.volume if music_volume is None else music_volume,
            )
        total = media_duration(output, self.ffprobe)
        write_json(
            job.file("result.json"),
            {
                "job_id": job.job_id,
                "content": content.to_dict(),
                "source_files": [path.name for path in sources],
                "clip_durations": [round(value, 3) for value in durations],
                "duration": round(total, 3),
                "output": str(output.resolve()),
                "render": {
                    "width": self.settings.render.width,
                    "height": self.settings.render.height,
                    "fps": self.settings.render.fps,
                    "video_codec": self.settings.render.video_codec,
                    "audio_codec": self.settings.render.audio_codec,
                    "timeline": "direct_cuts",
                    "fixed_subtitles": True,
                    "explanation_cards": False,
                },
            },
        )
        self._progress(progress, 1.0, "每日一句视频生成完成")
        return RenderResult(job.job_id, job.path, output.resolve(), total, content)

    def build_frame(self, content: SentenceContent, destination: Path) -> Path:
        render = self.settings.render
        layout = self.settings.layout
        image = Image.new("RGBA", (render.width, render.height), _rgba(layout.background_color))
        draw = ImageDraw.Draw(image)

        series_font = ImageFont.truetype(
            str(self.settings.font_path("chinese")), layout.series_font_size
        )
        draw.text(
            (layout.series_x, layout.series_y),
            layout.series_text,
            font=series_font,
            fill=_rgba(layout.series_color),
        )

        self._center_fitted(
            draw,
            content.sentence,
            self.settings.font_path("english"),
            layout.top_english_y,
            layout.top_english_font_size,
            layout.top_english_min_font_size,
            layout.top_english_max_width,
            1,
            _rgba(layout.top_text_color),
            spacing=8,
        )
        self._center_fitted(
            draw,
            content.chinese_translation,
            self.settings.font_path("chinese"),
            layout.top_chinese_y,
            layout.top_chinese_font_size,
            layout.top_chinese_min_font_size,
            layout.top_chinese_max_width,
            2,
            _rgba(layout.top_text_color),
            spacing=8,
        )
        self._center_fitted(
            draw,
            content.phonetic,
            self.settings.font_path("phonetic"),
            layout.phonetic_y,
            layout.phonetic_font_size,
            layout.phonetic_min_font_size,
            layout.phonetic_max_width,
            2,
            _rgba(layout.phonetic_color),
            spacing=6,
        )

        video_box = (
            layout.video_x,
            layout.video_y,
            layout.video_x + layout.video_width,
            layout.video_y + layout.video_height,
        )
        draw.rectangle(video_box, fill=(0, 0, 0, 0))

        # Both subtitle lines sit inside the video's lower black letterbox and
        # remain fixed for the complete duration of every source clip.
        subtitle_sentence = _sentence_case(content.sentence)
        subtitle_lines, subtitle_font = _fit_text(
            draw,
            subtitle_sentence,
            self.settings.font_path("english_subtitle"),
            layout.english_subtitle_font_size,
            layout.english_subtitle_min_font_size,
            layout.english_subtitle_max_width,
            2,
        )
        subtitle_text = "\n".join(subtitle_lines)
        subtitle_box = draw.multiline_textbbox(
            (0, 0), subtitle_text, font=subtitle_font, spacing=6, align="center",
            stroke_width=layout.english_subtitle_stroke_width,
        )
        subtitle_height = subtitle_box[3] - subtitle_box[1]
        subtitle_y = (
            layout.video_y
            + layout.video_height
            - layout.english_subtitle_bottom_margin
            - subtitle_height
        )
        _draw_centered(
            draw,
            subtitle_lines,
            subtitle_y,
            subtitle_font,
            _rgba(layout.subtitle_color),
            6,
            stroke_width=layout.english_subtitle_stroke_width,
            stroke_fill=_rgba(layout.subtitle_outline_color),
        )

        self._center_fitted(
            draw,
            content.chinese_translation,
            self.settings.font_path("chinese"),
            layout.chinese_subtitle_y,
            layout.chinese_subtitle_font_size,
            layout.chinese_subtitle_min_font_size,
            layout.chinese_subtitle_max_width,
            2,
            _rgba(layout.subtitle_color),
            spacing=8,
        )
        if layout.footer_text.strip():
            self._center_fitted(
                draw,
                layout.footer_text.strip(),
                self.settings.font_path("chinese"),
                layout.footer_y,
                layout.footer_font_size,
                max(20, layout.footer_font_size - 12),
                layout.footer_max_width,
                2,
                _rgba(layout.footer_color),
                spacing=6,
            )

        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination, "PNG")
        return destination.resolve()

    def _center_fitted(
        self,
        draw: ImageDraw.ImageDraw,
        text: str,
        font_path: Path,
        y: int,
        maximum: int,
        minimum: int,
        max_width: int,
        max_lines: int,
        color: tuple[int, int, int, int],
        *,
        spacing: int,
    ) -> None:
        lines, font = _fit_text(
            draw, text, font_path, maximum, minimum, max_width, max_lines
        )
        _draw_centered(draw, lines, y, font, color, spacing)

    def _render_clip(self, source: Path, overlay: Path, destination: Path) -> float:
        payload = probe_media(source, self.ffprobe)
        if not has_stream(payload, "video"):
            raise MediaError(f"上传文件没有视频画面：{source.name}")
        duration = media_duration(source, self.ffprobe)
        if duration > self.settings.render.max_clip_seconds:
            raise MediaError(
                f"{source.name} 时长 {duration:.1f}s，超过配置上限 "
                f"{self.settings.render.max_clip_seconds:g}s。"
            )

        render = self.settings.render
        layout = self.settings.layout
        has_audio = has_stream(payload, "audio")
        args: list[str | Path] = [
            self.ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            source,
            "-loop",
            "1",
            "-i",
            overlay,
        ]
        if not has_audio:
            args += [
                "-f",
                "lavfi",
                "-i",
                f"anullsrc=r={render.audio_sample_rate}:cl=stereo",
            ]

        visual = (
            f"[0:v]{_cover_video(layout.video_width, layout.video_height)},"
            "setsar=1,"
            f"fps={render.fps}[movie];"
            f"[movie]pad={render.width}:{render.height}:"
            f"{layout.video_x}:{layout.video_y}:color=black[canvas];"
            "[canvas][1:v]overlay=0:0:shortest=1:format=auto,format=yuv420p[vout]"
        )
        audio_input = "0:a:0" if has_audio else "2:a:0"
        audio_filters = [
            f"[{audio_input}]aresample={render.audio_sample_rate}",
            "aformat=channel_layouts=stereo",
        ]
        if render.normalize_audio and has_audio:
            audio_filters.append("loudnorm=I=-16:LRA=11:TP=-1.5")
        fade = min(max(0.0, render.audio_edge_fade_seconds), duration / 4)
        if fade > 0:
            audio_filters.extend(
                (
                    f"afade=t=in:st=0:d={fade:.4f}",
                    f"afade=t=out:st={max(0.0, duration - fade):.4f}:d={fade:.4f}",
                )
            )
        audio_filters.append(f"atrim=duration={duration:.6f}[aout]")
        graph = visual + ";" + ",".join(audio_filters)

        destination.parent.mkdir(parents=True, exist_ok=True)
        run_checked(
            [
                *args,
                "-filter_complex",
                graph,
                "-map",
                "[vout]",
                "-map",
                "[aout]",
                "-t",
                f"{duration:.6f}",
                "-c:v",
                render.video_codec,
                "-preset",
                render.preset,
                "-crf",
                str(render.crf),
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                render.audio_codec,
                "-b:a",
                render.audio_bitrate,
                "-ar",
                str(render.audio_sample_rate),
                "-ac",
                "2",
                "-movflags",
                "+faststart",
                destination,
            ],
            f"字幕烧录（{source.name}）",
        )
        return duration

    def _concat(self, clips: list[Path], output: Path, list_path: Path) -> None:
        lines = []
        for path in clips:
            escaped = path.resolve().as_posix().replace("'", "'\\''")
            lines.append(f"file '{escaped}'")
        list_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        run_checked(
            [
                self.ffmpeg,
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                list_path,
                "-c",
                "copy",
                "-movflags",
                "+faststart",
                output,
            ],
            "视频拼接",
        )

    def _mix_background_music(
        self,
        source: Path,
        music: Path,
        output: Path,
        duration: float,
        volume: float,
    ) -> None:
        render = self.settings.render
        clean_volume = min(1.0, max(0.0, float(volume)))
        fade = min(self.settings.music.fade_out_seconds, duration / 2)
        original = (
            f"[0:a:0]aresample={render.audio_sample_rate},"
            "aformat=channel_layouts=stereo,"
            f"apad=pad_dur={duration:.6f},atrim=duration={duration:.6f}[original]"
        )
        background = (
            f"[1:a:0]aresample={render.audio_sample_rate},"
            "aformat=channel_layouts=stereo,"
            f"volume={clean_volume:.6f},atrim=duration={duration:.6f}"
        )
        if fade > 0:
            background += (
                f",afade=t=out:st={max(0.0, duration - fade):.6f}:d={fade:.6f}"
            )
        graph = (
            original
            + ";"
            + background
            + "[background];[original][background]"
            + f"amix=inputs=2:duration=longest:dropout_transition=0,"
            + f"atrim=duration={duration:.6f},alimiter=limit=0.95[aout]"
        )
        run_checked(
            [
                self.ffmpeg,
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                source,
                "-stream_loop",
                "-1",
                "-i",
                music,
                "-filter_complex",
                graph,
                "-map",
                "0:v:0",
                "-map",
                "[aout]",
                "-t",
                f"{duration:.6f}",
                "-c:v",
                "copy",
                "-c:a",
                render.audio_codec,
                "-b:a",
                render.audio_bitrate,
                "-ar",
                str(render.audio_sample_rate),
                "-ac",
                "2",
                "-movflags",
                "+faststart",
                output,
            ],
            "背景音乐混音",
        )

    @staticmethod
    def _progress(callback: ProgressCallback | None, value: float, label: str) -> None:
        if callback:
            callback(min(1.0, max(0.0, value)), label)


def _rgba(value: str, alpha: int = 255) -> tuple[int, int, int, int]:
    clean = value.strip().lstrip("#")
    if len(clean) != 6:
        raise ValueError(f"无效颜色：{value}")
    return tuple(int(clean[index : index + 2], 16) for index in (0, 2, 4)) + (alpha,)


def _sentence_case(text: str) -> str:
    """Uppercase the first alphabetic character without altering the rest."""
    for index, character in enumerate(text):
        if character.isalpha():
            return text[:index] + character.upper() + text[index + 1 :]
    return text


def _cover_video(width: int, height: int) -> str:
    """Cover the full viewport without distortion, then center-crop overflow."""
    return (
        f"scale={width}:{height}:"
        "force_original_aspect_ratio=increase:force_divisible_by=2,"
        f"crop={width}:{height}:(iw-ow)/2:(ih-oh)/2"
    )


def _fit_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font_path: Path,
    maximum: int,
    minimum: int,
    max_width: int,
    max_lines: int,
) -> tuple[list[str], ImageFont.FreeTypeFont]:
    clean = " ".join(text.split()).strip()
    for size in range(maximum, minimum - 1, -2):
        font = ImageFont.truetype(str(font_path), size)
        lines = _wrap_text(draw, clean, font, max_width)
        if len(lines) <= max_lines:
            return lines or [clean], font
    font = ImageFont.truetype(str(font_path), minimum)
    lines = _wrap_text(draw, clean, font, max_width)
    # Never silently drop part of the user's exact sentence. Extremely long
    # copy may exceed the configured line target, but remains fully visible for
    # the user to correct or accommodate by lowering the configured minimum.
    return (lines or [clean]), font


def _wrap_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
    max_width: int,
) -> list[str]:
    if draw.textlength(text, font=font) <= max_width:
        return [text]
    has_spaces = " " in text
    tokens = text.split(" ") if has_spaces else list(text)
    separator = " " if has_spaces else ""
    lines: list[str] = []
    current = ""
    for token in tokens:
        candidate = f"{current}{separator if current else ''}{token}"
        if current and draw.textlength(candidate, font=font) > max_width:
            lines.append(current)
            current = token
        else:
            current = candidate
    if current:
        lines.append(current)
    return lines


def _draw_centered(
    draw: ImageDraw.ImageDraw,
    lines: list[str],
    y: int,
    font: ImageFont.FreeTypeFont,
    fill: tuple[int, int, int, int],
    spacing: int,
    *,
    stroke_width: int = 0,
    stroke_fill: tuple[int, int, int, int] | None = None,
) -> None:
    text = "\n".join(lines)
    box = draw.multiline_textbbox(
        (0, 0),
        text,
        font=font,
        spacing=spacing,
        align="center",
        stroke_width=stroke_width,
    )
    width = box[2] - box[0]
    draw.multiline_text(
        ((draw._image.width - width) / 2, y),
        text,
        font=font,
        fill=fill,
        spacing=spacing,
        align="center",
        stroke_width=stroke_width,
        stroke_fill=stroke_fill,
    )
