"""
SakanaWeaver AI Engine — Post-processing utilities

Quantize, humanize, and align generated MIDI output.
"""


def quantize_to_grid(notes: list[dict], ticks_per_beat: int, grid_division: int = 16) -> list[dict]:
    """Snap notes to the nearest grid position."""
    grid_size = ticks_per_beat * 4 // grid_division  # assuming 4/4
    return [
        {**n, 'start_tick': round(n['start_tick'] / grid_size) * grid_size}
        for n in notes
    ]


def humanize_timing(notes: list[dict], jitter_ticks: int = 10) -> list[dict]:
    """Add small random timing offsets for natural feel."""
    import random
    return [
        {**n, 'start_tick': n['start_tick'] + random.randint(-jitter_ticks, jitter_ticks)}
        for n in notes
    ]


def humanize_velocity(notes: list[dict], jitter: int = 8) -> list[dict]:
    """Add small random velocity variations."""
    import random
    return [
        {**n, 'velocity': max(1, min(127, n['velocity'] + random.randint(-jitter, jitter)))}
        for n in notes
    ]


def constrain_to_scale(notes: list[dict], scale_pitches: set[int]) -> list[dict]:
    """Snap pitches to the nearest note in the given scale."""
    def nearest_in_scale(pitch: int) -> int:
        pc = pitch % 12
        if pc in scale_pitches:
            return pitch
        # Find nearest scale pitch class
        candidates = sorted(scale_pitches, key=lambda s: abs(s - pc))
        return (pitch // 12) * 12 + candidates[0]

    return [{**n, 'pitch': nearest_in_scale(n['pitch'])} for n in notes]
