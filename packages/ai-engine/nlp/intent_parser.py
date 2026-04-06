"""
SakanaWeaver AI Engine — NLP Intent Parser

Parses natural language arrangement instructions into structured commands.
"""

from dataclasses import dataclass, field


@dataclass
class ArrangementIntent:
    action: str                         # generate, modify, delete, extend
    target: str                         # melody, chords, drums, bass, arrangement
    section: str | None = None          # verse, chorus, bridge, etc.
    style: str | None = None            # pop, jazz, rock, lo-fi, etc.
    key: str | None = None              # C major, A minor, etc.
    bpm: int | None = None
    bars: int | None = None
    constraints: dict = field(default_factory=dict)


class IntentParser:
    """
    Parses user text into ArrangementIntent.

    In production, this wraps an LLM call with function-calling / structured output.
    """

    SYSTEM_PROMPT = """You are a music arrangement assistant. Parse the user's request into a structured command.
Extract: action (generate/modify/delete/extend), target (melody/chords/drums/bass/arrangement),
section, style, key, bpm, bars, and any additional constraints.
Return JSON only."""

    async def parse(self, user_input: str) -> ArrangementIntent:
        # Placeholder: in production, call LLM API here
        # response = await llm.chat(system=self.SYSTEM_PROMPT, user=user_input, response_format="json")
        # return ArrangementIntent(**response)
        raise NotImplementedError("LLM integration required")
