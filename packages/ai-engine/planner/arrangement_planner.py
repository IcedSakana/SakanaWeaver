"""
SakanaWeaver AI Engine — Style-Aware Arrangement Planner

Given a style profile and user intent, this module plans the full arrangement:
1. Determines song structure (section order & lengths)
2. Assigns instrumentation per section
3. Plans dynamic / energy arc
4. Selects chord progressions per section
5. Produces a complete arrangement blueprint for downstream generators
"""

from __future__ import annotations
import random
from dataclasses import dataclass, field


@dataclass
class SectionPlan:
    """Plan for a single section within the arrangement."""
    section_type: str                       # e.g. 'verse', 'chorus'
    start_bar: int
    length_bars: int
    key_root: str
    key_mode: str
    chord_progression: list[str]            # e.g. ['Cmaj7', 'Am7', 'Dm7', 'G7']
    instruments: list[str]                  # active instruments
    energy: float                           # 0.0 - 1.0
    groove_template: str | None             # drum groove name
    melody_density: float                   # notes per beat
    velocity_range: tuple[int, int]         # (min, max) MIDI velocity


@dataclass
class ArrangementBlueprint:
    """Complete arrangement plan for a song."""
    style_id: str
    bpm: int
    time_signature: tuple[int, int]
    key_root: str
    key_mode: str
    total_bars: int
    sections: list[SectionPlan]
    metadata: dict = field(default_factory=dict)


class ArrangementPlanner:
    """
    Plans a complete arrangement based on a style profile.

    The planner uses the style's arrangement profile to determine structure,
    instrumentation profile for instrument selection, harmony profile for
    chord progressions, and rhythm profile for groove selection.
    """

    def plan(
        self,
        style: dict,
        key_root: str = 'C',
        key_mode: str = 'major',
        bpm: int | None = None,
        num_bars: int | None = None,
        sections_override: list[str] | None = None,
    ) -> ArrangementBlueprint:
        """
        Generate a complete arrangement blueprint.

        Args:
            style: StyleProfile as a dict (from TypeScript presets, serialized via JSON)
            key_root: Root note
            key_mode: Scale mode
            bpm: Override BPM (default: use style's default)
            num_bars: Target total bars (auto-calculated if None)
            sections_override: Override section order (default: use style's typical structure)
        """
        rhythm = style.get('rhythm', {})
        harmony = style.get('harmony', {})
        melody = style.get('melody', {})
        instrumentation = style.get('instrumentation', {})
        arrangement = style.get('arrangement', {})

        # BPM
        if bpm is None:
            bpm = rhythm.get('defaultBpm', 120)

        # Time signature
        ts = rhythm.get('timeSignature', [4, 4])
        time_signature = (ts[0], ts[1])

        # Section structure
        section_types = sections_override or arrangement.get('typicalStructure', ['intro', 'verse', 'chorus', 'outro'])
        section_lengths = arrangement.get('sectionLengths', {})
        energy_curve = arrangement.get('energyCurve', {})
        instrument_density = instrumentation.get('instrumentDensity', {})

        # Build instrument pool
        default_instruments = [i['instrument'] for i in instrumentation.get('defaultInstruments', [])]
        optional_instruments = [i['instrument'] for i in instrumentation.get('optionalInstruments', [])]
        all_instruments = default_instruments + optional_instruments

        # Chord progressions ranked by weight
        progressions = sorted(
            harmony.get('commonProgressions', []),
            key=lambda p: p.get('weight', 0),
            reverse=True,
        )

        # Groove templates
        groove_templates = rhythm.get('grooveTemplates', [])
        default_groove = groove_templates[0]['name'] if groove_templates else None

        # Plan each section
        sections: list[SectionPlan] = []
        current_bar = 0

        for sec_type in section_types:
            # Section length
            length_range = section_lengths.get(sec_type, [4, 8])
            length_bars = random.randint(length_range[0], length_range[1])
            # Ensure even number of bars for symmetry
            if length_bars % 2 != 0:
                length_bars += 1

            # Energy level
            energy = energy_curve.get(sec_type, 0.5)

            # Instrument selection based on density
            target_count = instrument_density.get(sec_type, 3)
            target_count = min(target_count, len(all_instruments))
            # Always include drums and bass in non-intro/outro sections
            active = self._select_instruments(
                default_instruments, optional_instruments,
                target_count, sec_type,
            )

            # Chord progression selection (higher energy → try first progression)
            chord_prog = self._select_progression(progressions, sec_type, energy, key_root)

            # Melody density from style, scaled by energy
            base_density = melody.get('noteDensity', 2.0)
            mel_density = base_density * (0.5 + 0.5 * energy)

            # Velocity range scaled by energy
            vel_min = int(40 + energy * 40)
            vel_max = int(80 + energy * 47)

            sections.append(SectionPlan(
                section_type=sec_type,
                start_bar=current_bar,
                length_bars=length_bars,
                key_root=key_root,
                key_mode=key_mode,
                chord_progression=chord_prog,
                instruments=active,
                energy=energy,
                groove_template=default_groove if sec_type not in ('intro', 'outro') else None,
                melody_density=mel_density,
                velocity_range=(vel_min, vel_max),
            ))
            current_bar += length_bars

        return ArrangementBlueprint(
            style_id=style.get('id', 'unknown'),
            bpm=bpm,
            time_signature=time_signature,
            key_root=key_root,
            key_mode=key_mode,
            total_bars=current_bar,
            sections=sections,
        )

    def _select_instruments(
        self,
        default: list[str],
        optional: list[str],
        target_count: int,
        section_type: str,
    ) -> list[str]:
        """Select instruments for a section, prioritizing defaults."""
        if section_type == 'breakdown':
            # Sparse: only 1-2 instruments
            return default[:min(2, len(default))]

        selected = list(default[:target_count])
        remaining = target_count - len(selected)
        if remaining > 0 and optional:
            selected.extend(optional[:remaining])
        return selected

    def _select_progression(
        self,
        progressions: list[dict],
        section_type: str,
        energy: float,
        key_root: str,
    ) -> list[str]:
        """Pick a chord progression appropriate for the section."""
        if not progressions:
            return [f'{key_root}maj']

        # Weighted random selection
        weights = [p.get('weight', 1.0) for p in progressions]
        total = sum(weights)
        r = random.random() * total
        cumulative = 0.0
        chosen = progressions[0]
        for prog, w in zip(progressions, weights):
            cumulative += w
            if r <= cumulative:
                chosen = prog
                break

        # Format chord names from degrees (simplified)
        degrees = chosen.get('degrees', [1])
        qualities = chosen.get('qualities', ['major'] * len(degrees))

        # Map scale degree → note name (simplified: major scale from key_root)
        major_scale_offsets = {1: 0, 2: 2, 3: 4, 4: 5, 5: 7, 6: 9, 7: 11}
        note_names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
        root_idx = note_names.index(key_root) if key_root in note_names else 0

        quality_suffixes = {
            'major': '', 'minor': 'm', 'dom7': '7', 'maj7': 'maj7',
            'min7': 'm7', 'dim': 'dim', 'aug': 'aug',
        }

        chords = []
        for deg, qual in zip(degrees, qualities):
            offset = major_scale_offsets.get(deg, 0)
            note = note_names[(root_idx + offset) % 12]
            suffix = quality_suffixes.get(qual, '')
            chords.append(f'{note}{suffix}')

        return chords
