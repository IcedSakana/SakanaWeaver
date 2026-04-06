"""
SakanaWeaver AI Engine — Style-Aware Post-processing Utilities

Quantize, humanize, and align generated MIDI output,
with parameters driven by the style profile.
"""

from __future__ import annotations
import random
import math


# ── Basic transforms ──────────────────────────────────────────────

def quantize_to_grid(notes: list[dict], ticks_per_beat: int, grid_division: int = 16) -> list[dict]:
    """Snap notes to the nearest grid position."""
    grid_size = ticks_per_beat * 4 // grid_division  # assuming 4/4
    return [
        {**n, 'start_tick': round(n['start_tick'] / grid_size) * grid_size}
        for n in notes
    ]


def humanize_timing(notes: list[dict], jitter_ticks: int = 10) -> list[dict]:
    """Add small random timing offsets for natural feel."""
    return [
        {**n, 'start_tick': n['start_tick'] + random.randint(-jitter_ticks, jitter_ticks)}
        for n in notes
    ]


def humanize_velocity(notes: list[dict], jitter: int = 8) -> list[dict]:
    """Add small random velocity variations."""
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
        candidates = sorted(scale_pitches, key=lambda s: abs(s - pc))
        return (pitch // 12) * 12 + candidates[0]

    return [{**n, 'pitch': nearest_in_scale(n['pitch'])} for n in notes]


# ── Style-aware transforms ───────────────────────────────────────

def apply_swing(notes: list[dict], ticks_per_beat: int, swing_amount: float) -> list[dict]:
    """
    Apply swing feel by delaying off-beat notes.

    swing_amount: 0.0 = straight, 1.0 = full triplet swing (67/33 split)
    """
    if swing_amount <= 0.0:
        return notes

    eighth_ticks = ticks_per_beat // 2
    # Swing delays the 2nd eighth note: max delay = 1/3 of eighth duration
    max_delay = int(eighth_ticks * swing_amount * 0.33)

    result = []
    for n in notes:
        pos_in_beat = n['start_tick'] % ticks_per_beat
        # Is this note on an off-beat eighth?
        if abs(pos_in_beat - eighth_ticks) < (eighth_ticks // 4):
            result.append({**n, 'start_tick': n['start_tick'] + max_delay})
        else:
            result.append(n)
    return result


def apply_velocity_accents(
    notes: list[dict],
    ticks_per_beat: int,
    accent_pattern: list[float],
    steps_per_bar: int = 16,
) -> list[dict]:
    """Apply velocity accent pattern from a groove template."""
    if not accent_pattern:
        return notes

    bar_ticks = ticks_per_beat * 4  # assuming 4/4
    step_ticks = bar_ticks / steps_per_bar

    result = []
    for n in notes:
        pos_in_bar = n['start_tick'] % bar_ticks
        step_idx = int(pos_in_bar / step_ticks) % len(accent_pattern)
        accent = accent_pattern[step_idx]
        new_vel = max(1, min(127, int(n['velocity'] * accent)))
        result.append({**n, 'velocity': new_vel})
    return result


def apply_style_postprocess(
    notes: list[dict],
    style: dict,
    ticks_per_beat: int = 480,
    scale_pitches: set[int] | None = None,
) -> list[dict]:
    """
    Full style-aware post-processing pipeline.

    Applies in order:
    1. Scale constraint (if scale_pitches given)
    2. Quantize to grid
    3. Swing
    4. Velocity accents from groove template
    5. Humanize timing & velocity (based on style parameters)
    """
    rhythm = style.get('rhythm', {})
    melody_profile = style.get('melody', {})

    # 1. Scale constraint
    if scale_pitches:
        adherence = melody_profile.get('scaleAdherence', 0.8)
        if adherence > 0.5:
            notes = constrain_to_scale(notes, scale_pitches)

    # 2. Quantize — tighter for electronic styles, looser for jazz/lo-fi
    swing = rhythm.get('swing', 0.0)
    syncopation = rhythm.get('syncopationLevel', 0.3)
    # Higher syncopation → finer grid (allow more off-beat)
    grid_div = 16 if syncopation < 0.5 else 32
    notes = quantize_to_grid(notes, ticks_per_beat, grid_division=grid_div)

    # 3. Swing
    if swing > 0.05:
        notes = apply_swing(notes, ticks_per_beat, swing)

    # 4. Velocity accents from first groove template
    grooves = rhythm.get('grooveTemplates', [])
    if grooves:
        first_groove = grooves[0]
        accents = first_groove.get('velocityAccents', [])
        steps = first_groove.get('stepsPerBar', 16)
        if accents:
            notes = apply_velocity_accents(notes, ticks_per_beat, accents, steps)

    # 5. Humanize — more for organic styles, less for electronic
    # Use swing + syncopation as proxy for "organic-ness"
    organic_factor = (swing + syncopation) / 2.0
    timing_jitter = int(5 + organic_factor * 20)  # 5-25 ticks
    velocity_jitter = int(3 + organic_factor * 12) # 3-15
    notes = humanize_timing(notes, jitter_ticks=timing_jitter)
    notes = humanize_velocity(notes, jitter=velocity_jitter)

    return notes


def clamp_pitch_range(notes: list[dict], pitch_range: tuple[int, int]) -> list[dict]:
    """Transpose notes that fall outside the style's pitch range into range."""
    lo, hi = pitch_range
    result = []
    for n in notes:
        p = n['pitch']
        while p < lo:
            p += 12
        while p > hi:
            p -= 12
        result.append({**n, 'pitch': p})
    return result


def adjust_note_density(
    notes: list[dict],
    target_density: float,
    ticks_per_beat: int,
    total_ticks: int,
) -> list[dict]:
    """
    Add or remove notes to match target density (notes per beat).
    Removal prioritizes weak-beat notes; addition duplicates strong-beat notes.
    """
    total_beats = total_ticks / ticks_per_beat
    current_density = len(notes) / total_beats if total_beats > 0 else 0

    if abs(current_density - target_density) / max(target_density, 0.1) < 0.15:
        return notes  # close enough

    if current_density > target_density:
        # Remove weakest notes
        ratio = target_density / current_density
        scored = []
        for n in notes:
            pos_in_beat = n['start_tick'] % ticks_per_beat
            # downbeat = strong, offbeat = weak
            strength = 1.0 - (pos_in_beat / ticks_per_beat)
            scored.append((strength, n))
        scored.sort(key=lambda x: x[0], reverse=True)
        keep_count = max(1, int(len(notes) * ratio))
        return [n for _, n in scored[:keep_count]]
    else:
        # Density is already lower; just return as-is (generation should handle this)
        return notes

