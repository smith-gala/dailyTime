class DailyTimeError(RuntimeError):
    """Base error shown to the UI."""


class ConfigurationError(DailyTimeError):
    """Invalid or missing local configuration."""


class ModelError(DailyTimeError):
    """The translation/phonetic model request failed."""


class MediaError(DailyTimeError):
    """A video could not be inspected or rendered."""
