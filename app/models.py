from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
import random
import re


MUSIC_TAGS = (
    "love_warm",
    "hope_motivation",
    "happy_daily",
    "sad_reflection",
    "nostalgia_life",
)

MUSIC_TAG_LABELS = {
    "love_warm": "love/warm｜爱情、陪伴、温柔",
    "hope_motivation": "hope/motivation｜梦想、努力、鼓励",
    "happy_daily": "happy/daily｜日常口语、青春",
    "sad_reflection": "sad/reflection｜伤感、人生感悟",
    "nostalgia_life": "nostalgia/life｜成长、回忆、时间",
}

DOUYIN_TAGS = (
    "#英语口语",
    "#每日一句英语",
    "#看剧学英语",
    "#看电影学英语",
    "#英语",
    "#练口语",
    "#生活英语",
    "#美剧",
    "#英语发音",
    "#从零开始学英语",
)


PUBLISH_PUNCTUATION_TRANSLATION = str.maketrans(
    {
        "“": '"',
        "”": '"',
        "＂": '"',
        "‘": "'",
        "’": "'",
        "＇": "'",
        "！": "!",
        "？": "?",
    }
)


def clean_text(value: object) -> str:
    return " ".join(str(value or "").split()).strip()


def clean_multiline_text(value: object) -> str:
    normalized = str(value or "").translate(PUBLISH_PUNCTUATION_TRANSLATION)
    lines = [" ".join(line.split()).strip() for line in normalized.splitlines()]
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    cleaned: list[str] = []
    for line in lines:
        if line or not cleaned or cleaned[-1]:
            cleaned.append(line)
    return "\n".join(cleaned)


@dataclass(frozen=True)
class SentenceContent:
    sentence: str
    chinese_translation: str
    phonetic: str
    video_description: str = ""

    @classmethod
    def create(
        cls,
        sentence: object,
        chinese_translation: object,
        phonetic: object,
        video_description: object = "",
    ) -> "SentenceContent":
        result = cls(
            clean_text(sentence),
            clean_text(chinese_translation),
            clean_text(phonetic),
            clean_multiline_text(video_description),
        )
        result.validate()
        return result

    def validate(self) -> None:
        if not self.sentence:
            raise ValueError("英文句子不能为空。")
        if not self.chinese_translation:
            raise ValueError("中文翻译不能为空。")
        if not self.phonetic:
            raise ValueError("整句 IPA 音标不能为空。")
        if len(self.sentence) > 160:
            raise ValueError("英文句子不能超过 160 个字符。")
        if len(self.chinese_translation) > 80:
            raise ValueError("中文翻译不能超过 80 个字符。")
        if len(self.phonetic) > 240:
            raise ValueError("整句 IPA 音标不能超过 240 个字符。")
        if len(self.video_description) > 4000:
            raise ValueError("抖音视频文案不能超过 4000 个字符。")

    def to_dict(self) -> dict[str, str]:
        return {**asdict(self), "video_title": self.video_title}

    @property
    def video_title(self) -> str:
        return f"每日一句地道英语｜{self.sentence}"


def with_random_douyin_tags(
    content: SentenceContent, rng: random.Random | None = None
) -> SentenceContent:
    """Return publication content ending in 4 or 5 unique random Douyin tags."""

    chooser = rng or random.SystemRandom()
    count = chooser.randint(4, 5)
    tags = chooser.sample(DOUYIN_TAGS, count)
    description = _remove_existing_douyin_tag_line(content.video_description)
    return SentenceContent.create(
        content.sentence,
        content.chinese_translation,
        content.phonetic,
        f'{description}\n\n{" ".join(tags)}',
    )


def _remove_existing_douyin_tag_line(value: str) -> str:
    lines = value.rstrip().splitlines()
    if not lines:
        return ""
    known_tags = "|".join(re.escape(tag) for tag in DOUYIN_TAGS)
    if re.fullmatch(rf"(?:{known_tags})(?:\s+(?:{known_tags}))*", lines[-1].strip()):
        lines.pop()
        while lines and not lines[-1].strip():
            lines.pop()
    return "\n".join(lines)


@dataclass(frozen=True)
class RenderResult:
    job_id: str
    job_dir: Path
    output: Path
    duration: float
    content: SentenceContent
