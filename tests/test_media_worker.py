from __future__ import annotations

import json
import random
from pathlib import Path

import pytest
from PIL import Image, ImageColor, ImageDraw

from app.config import load_settings
from app.models import DOUYIN_TAGS, SentenceContent, with_random_douyin_tags
from app.renderer import DailySentenceRenderer, _cover_video, _fit_text, _sentence_case
from scripts import daily_time_agent


class StubHttpResponse:
    def __init__(self, payload: dict[str, object]):
        self.payload = payload

    def __enter__(self) -> "StubHttpResponse":
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def read(self) -> bytes:
        return json.dumps(self.payload, ensure_ascii=False).encode("utf-8")


def test_feishu_entry_forwards_exact_message_and_identity(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DAILY_TIME_CONVERSATION_ID", "oc_conversation")
    monkeypatch.setenv("DAILY_TIME_USER_ID", "ou_User123")
    monkeypatch.setenv("DAILY_TIME_MESSAGE", '制作 "Keep going"，不要改写')
    captured: dict[str, object] = {}

    def fake_urlopen(request, *, timeout: float):
        captured["request"] = request
        captured["timeout"] = timeout
        return StubHttpResponse({"code": "OK", "data": {"answer": "已创建"}})

    monkeypatch.setattr(daily_time_agent, "open_agent_request", fake_urlopen)

    response = daily_time_agent.forward_message()

    request = captured["request"]
    assert response["code"] == "OK"
    assert captured["timeout"] == 120.0
    assert request.full_url == "http://127.0.0.1:8080/api/v1/agent/chat"
    assert request.get_header("X-user-id") == "ou_User123"
    assert json.loads(request.data.decode("utf-8")) == {
        "conversationId": "oc_conversation",
        "message": '制作 "Keep going"，不要改写',
    }


def test_feishu_entry_rejects_untrusted_user_id(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DAILY_TIME_CONVERSATION_ID", "oc_conversation")
    monkeypatch.setenv("DAILY_TIME_USER_ID", "someone@example.com")
    monkeypatch.setenv("DAILY_TIME_MESSAGE", "进度")

    with pytest.raises(daily_time_agent.AgentClientError, match="ou_ 用户 ID"):
        daily_time_agent.build_request()


@pytest.mark.parametrize("seed", range(10))
def test_douyin_description_uses_unique_known_tags(seed: int):
    content = SentenceContent.create("Keep going", "继续加油", "/kiːp ˈɡoʊɪŋ/", "正文")
    published = with_random_douyin_tags(content, random.Random(seed))
    tags = published.video_description.splitlines()[-1].split()
    assert len(tags) in (4, 5)
    assert len(tags) == len(set(tags))
    assert set(tags) <= set(DOUYIN_TAGS)


def test_frame_matches_fixed_portrait_canvas(tmp_path: Path):
    settings = load_settings()
    renderer = DailySentenceRenderer.__new__(DailySentenceRenderer)
    renderer.settings = settings
    destination = tmp_path / "frame.png"
    renderer.build_frame(
        SentenceContent.create(
            "why the long face", "为什么不高兴？", "/waɪ ðə lɔːŋ feɪs/"
        ),
        destination,
    )
    with Image.open(destination) as image:
        assert image.size == (1080, 1920)
        assert image.mode == "RGBA"
        background = ImageColor.getrgb(settings.layout.background_color) + (255,)
        assert image.getpixel((34, 72)) == background
        assert image.getpixel((settings.layout.video_x, settings.layout.video_y)) == (
            0,
            0,
            0,
            0,
        )


def test_top_english_shrinks_before_wrapping():
    settings = load_settings()
    layout = settings.layout
    canvas = Image.new("RGBA", (settings.render.width, settings.render.height))
    draw = ImageDraw.Draw(canvas)

    text = "Can I see you for a second right now?"
    lines, font = _fit_text(
        draw,
        text,
        settings.font_path("english"),
        layout.top_english_font_size,
        layout.top_english_min_font_size,
        layout.top_english_max_width,
        1,
    )

    assert lines == [text]
    assert font.size < layout.top_english_font_size
    assert draw.textlength(lines[0], font=font) <= layout.top_english_max_width


def test_video_subtitle_uses_sentence_case_without_changing_other_letters():
    assert _sentence_case("why the long face?") == "Why the long face?"
    assert _sentence_case("  déjà vu") == "  Déjà vu"


def test_video_cover_fills_the_entire_viewport_without_padding():
    filters = _cover_video(1000, 880)
    assert filters.startswith("scale=1000:880:")
    assert "force_original_aspect_ratio=increase" in filters
    assert filters.endswith("crop=1000:880:(iw-ow)/2:(ih-oh)/2")
    assert "pad=" not in filters
