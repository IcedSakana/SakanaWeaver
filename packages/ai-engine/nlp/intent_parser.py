"""
SakanaWeaver AI Engine — NLP Intent Parser

Parses natural language arrangement instructions into structured commands,
with rich style resolution (aliases, blending, sub-genre mapping).
"""

from __future__ import annotations
from dataclasses import dataclass, field


# ── Style Alias Map ───────────────────────────────────────────────
# Maps natural language style names (including Chinese) to canonical style IDs.
# Supports fuzzy/colloquial references.

STYLE_ALIASES: dict[str, str] = {
    # English aliases
    'pop': 'pop', 'pop music': 'pop', 'mainstream': 'pop',
    'jazz': 'jazz', 'swing': 'jazz', 'bebop': 'jazz', 'smooth jazz': 'jazz',
    'rock': 'rock', 'rock and roll': 'rock', 'alternative': 'rock', 'indie rock': 'rock',
    'lofi': 'lofi', 'lo-fi': 'lofi', 'lo fi': 'lofi', 'chillhop': 'lofi', 'study beats': 'lofi',
    'edm': 'edm', 'electronic': 'edm', 'dance': 'edm', 'house': 'edm', 'trance': 'edm', 'techno': 'edm',
    'rnb': 'rnb', 'r&b': 'rnb', 'r and b': 'rnb', 'soul': 'rnb', 'neo soul': 'rnb',
    'classical': 'classical', 'orchestral': 'classical', 'symphony': 'classical',
    'reggaeton': 'reggaeton', 'dembow': 'reggaeton', 'latin': 'reggaeton', 'urbano': 'reggaeton',
    # Chinese aliases
    '流行': 'pop', '流行乐': 'pop', '流行音乐': 'pop',
    '爵士': 'jazz', '爵士乐': 'jazz', '摇摆乐': 'jazz',
    '摇滚': 'rock', '摇滚乐': 'rock', '硬摇滚': 'rock', '独立摇滚': 'rock',
    '电子': 'edm', '电子乐': 'edm', '电子音乐': 'edm', '电音': 'edm',
    '古典': 'classical', '古典乐': 'classical', '交响乐': 'classical', '管弦乐': 'classical',
    '嘻哈': 'lofi', '说唱': 'lofi',
    '节奏蓝调': 'rnb', '灵魂乐': 'rnb',
    '雷鬼': 'reggaeton', '拉丁': 'reggaeton',
}

# Blend patterns: "jazz pop" → blend jazz + pop
BLEND_SEPARATORS = [' x ', ' × ', '融合', 'fusion', ' + ', '-']


@dataclass
class StyleIntent:
    """Resolved style from user input."""
    primary_style: str                     # canonical style ID
    secondary_style: str | None = None     # for blended styles
    blend_ratio: float = 0.5              # 0.0 = all primary, 1.0 = all secondary
    sub_genre: str | None = None           # e.g. "smooth" in "smooth jazz"
    energy_modifier: float | None = None   # user override: "更激烈" → +0.2


@dataclass
class ArrangementIntent:
    action: str                         # generate, modify, delete, extend
    target: str                         # melody, chords, drums, bass, arrangement, full
    section: str | None = None          # verse, chorus, bridge, etc.
    style: StyleIntent | None = None    # resolved style
    key: str | None = None              # C major, A minor, etc.
    bpm: int | None = None
    bars: int | None = None
    constraints: dict = field(default_factory=dict)


def resolve_style(raw_style: str) -> StyleIntent:
    """
    Resolve a raw style string into a StyleIntent.

    Handles:
    - Direct matches: "pop" → pop
    - Aliases: "流行" → pop, "lo-fi" → lofi
    - Blends: "jazz pop" → jazz × pop, "爵士流行" → jazz × pop
    - Sub-genres: "smooth jazz" → jazz (sub_genre="smooth")
    """
    text = raw_style.strip().lower()

    # Check for blend patterns
    for sep in BLEND_SEPARATORS:
        if sep in text:
            parts = text.split(sep, 1)
            a = _lookup_style(parts[0].strip())
            b = _lookup_style(parts[1].strip())
            if a and b:
                return StyleIntent(primary_style=a, secondary_style=b, blend_ratio=0.5)

    # Check for two-word blend ("jazz pop", "爵士流行")
    words = text.split()
    if len(words) == 2:
        a = _lookup_style(words[0])
        b = _lookup_style(words[1])
        if a and b and a != b:
            return StyleIntent(primary_style=a, secondary_style=b, blend_ratio=0.5)
        if a and not b:
            return StyleIntent(primary_style=a, sub_genre=words[1])
        if b and not a:
            return StyleIntent(primary_style=b, sub_genre=words[0])

    # Direct lookup
    matched = _lookup_style(text)
    if matched:
        return StyleIntent(primary_style=matched)

    # Fuzzy: check if any alias is a substring
    for alias, sid in STYLE_ALIASES.items():
        if alias in text or text in alias:
            return StyleIntent(primary_style=sid)

    # Default fallback
    return StyleIntent(primary_style='pop')


def _lookup_style(text: str) -> str | None:
    """Look up a canonical style ID from text."""
    return STYLE_ALIASES.get(text)


class IntentParser:
    """
    Parses user text into ArrangementIntent with style resolution.

    In production, this wraps an LLM call with function-calling / structured output.
    The rule-based style resolution handles common patterns without LLM.
    """

    SYSTEM_PROMPT = """You are a music arrangement assistant. Parse the user's request into a structured command.
Extract:
- action: generate / modify / delete / extend
- target: melody / chords / drums / bass / arrangement / full
- section: intro / verse / pre-chorus / chorus / bridge / outro / solo / breakdown
- style: raw style string (e.g. "jazz pop", "lo-fi", "摇滚")
- key: e.g. "C major", "A minor"
- bpm: integer
- bars: integer
- constraints: any additional constraints as key-value pairs

Return JSON only."""

    async def parse(self, user_input: str) -> ArrangementIntent:
        """
        Parse user input into a structured intent.

        In production, calls LLM for full parsing.
        For style field, always runs through resolve_style() for normalization.
        """
        # TODO: Replace with LLM call
        # raw = await llm.chat(system=self.SYSTEM_PROMPT, user=user_input, response_format="json")
        # intent = ArrangementIntent(**raw)
        # if intent.style_raw:
        #     intent.style = resolve_style(intent.style_raw)
        # return intent
        raise NotImplementedError("LLM integration required — use resolve_style() for style parsing")

    def parse_style_only(self, text: str) -> StyleIntent:
        """Quick style resolution without full LLM parse."""
        return resolve_style(text)

