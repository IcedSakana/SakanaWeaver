/**
 * Style Definition — describes the musical characteristics of a genre/style.
 *
 * This is the central data structure that conditions all AI generation modules.
 * Each style profile captures rhythm, harmony, melody, instrumentation,
 * arrangement, and mixing preferences for a given genre.
 */

import type { SectionType } from '../project/types';

// ─── Rhythm Profile ──────────────────────────────────────────────

export interface RhythmProfile {
  bpmRange: [number, number];               // typical BPM range
  defaultBpm: number;
  timeSignature: [number, number];           // e.g. [4, 4], [3, 4], [6, 8]
  swing: number;                             // 0.0 (straight) to 1.0 (full swing)
  subdivisionBias: 'straight' | 'triplet' | 'shuffle' | 'dotted';
  syncopationLevel: number;                  // 0.0 - 1.0
  grooveTemplates: GrooveTemplate[];         // named drum/rhythm patterns
}

export interface GrooveTemplate {
  name: string;                              // e.g. "basic-beat", "four-on-the-floor"
  pattern: DrumHitMap;                       // step sequencer grid
  stepsPerBar: number;
  velocityAccents: number[];                 // velocity multipliers per step
}

export type DrumInstrument =
  | 'kick' | 'snare' | 'hihat_closed' | 'hihat_open'
  | 'tom_high' | 'tom_mid' | 'tom_low'
  | 'crash' | 'ride' | 'clap' | 'rimshot'
  | 'shaker' | 'tambourine' | 'cowbell';

/** Mapping from drum instrument → boolean[] (one per step). */
export type DrumHitMap = Partial<Record<DrumInstrument, boolean[]>>;

// ─── Harmony Profile ─────────────────────────────────────────────

export interface HarmonyProfile {
  preferredModes: string[];                  // e.g. ['major', 'mixolydian']
  chordComplexity: number;                   // 0.0 (triads only) to 1.0 (extended/altered)
  commonProgressions: ChordProgressionPattern[];
  allowedChordQualities: string[];           // e.g. ['major', 'minor', 'dom7', 'maj7']
  modulationFrequency: number;               // 0.0 (never) to 1.0 (frequent key changes)
  chromaticismLevel: number;                 // 0.0 - 1.0
  tensionCurve: Record<SectionType, number>; // harmonic tension per section (0-1)
}

export interface ChordProgressionPattern {
  name: string;                              // e.g. "I-V-vi-IV"
  degrees: number[];                         // scale degrees: [1, 5, 6, 4]
  qualities: string[];                       // chord quality per degree
  weight: number;                            // selection probability weight
}

// ─── Melody Profile ──────────────────────────────────────────────

export interface MelodyProfile {
  pitchRange: [number, number];              // MIDI pitch range
  preferredIntervals: number[];              // common intervals in semitones
  maxLeap: number;                           // max interval in semitones
  stepToLeapRatio: number;                   // 0.0 (all leaps) to 1.0 (all steps)
  noteDensity: number;                       // notes per beat (average)
  restFrequency: number;                     // 0.0 (no rests) to 1.0 (very sparse)
  ornamentTypes: OrnamentType[];
  phraseLength: [number, number];            // typical phrase length in bars
  repetitionLevel: number;                   // 0.0 (no repetition) to 1.0 (highly repetitive)
  scaleAdherence: number;                    // 0.0 (chromatic) to 1.0 (strictly diatonic)
}

export type OrnamentType = 'trill' | 'mordent' | 'grace_note' | 'slide' | 'bend' | 'vibrato' | 'none';

// ─── Instrumentation Profile ─────────────────────────────────────

export interface InstrumentationProfile {
  defaultInstruments: InstrumentRole[];
  optionalInstruments: InstrumentRole[];
  instrumentDensity: Record<SectionType, number>; // how many instruments active per section
}

export interface InstrumentRole {
  role: 'lead' | 'harmony' | 'bass' | 'drums' | 'pad' | 'arpeggio' | 'fx' | 'percussion';
  instrument: string;                        // e.g. "acoustic_piano", "electric_guitar", "synth_pad"
  midiProgram?: number;                      // General MIDI program number
  octaveRange: [number, number];             // e.g. [3, 5]
  velocityRange: [number, number];           // e.g. [60, 110]
}

// ─── Arrangement Profile ─────────────────────────────────────────

export interface ArrangementProfile {
  typicalStructure: SectionType[];           // e.g. ['intro', 'verse', 'chorus', 'verse', 'chorus', 'bridge', 'chorus', 'outro']
  sectionLengths: Record<SectionType, [number, number]>; // length range in bars
  buildupStrategy: 'additive' | 'subtractive' | 'contrast' | 'layered';
  dynamicRange: number;                      // 0.0 (flat) to 1.0 (wide dynamic range)
  energyCurve: Record<SectionType, number>;  // energy level per section (0-1)
}

// ─── Mixing Profile ──────────────────────────────────────────────

export interface MixingProfile {
  reverbLevel: number;                       // 0.0 - 1.0
  stereoWidth: number;                       // 0.0 (mono) to 1.0 (wide)
  compressionAmount: number;                 // 0.0 - 1.0
  brightnessEQ: number;                      // -1.0 (dark) to 1.0 (bright)
  lowEndEmphasis: number;                    // 0.0 - 1.0
}

// ─── Top-Level Style Profile ─────────────────────────────────────

export interface StyleProfile {
  id: string;
  name: string;
  description: string;
  tags: string[];                            // e.g. ['electronic', 'chill', 'ambient']
  parentStyle?: string;                      // inherit from another style
  rhythm: RhythmProfile;
  harmony: HarmonyProfile;
  melody: MelodyProfile;
  instrumentation: InstrumentationProfile;
  arrangement: ArrangementProfile;
  mixing: MixingProfile;
}

/**
 * Merges a child style with its parent, child values take precedence.
 */
export function mergeStyles(parent: StyleProfile, child: Partial<StyleProfile>): StyleProfile {
  return {
    ...parent,
    ...child,
    rhythm: { ...parent.rhythm, ...child.rhythm },
    harmony: { ...parent.harmony, ...child.harmony },
    melody: { ...parent.melody, ...child.melody },
    instrumentation: { ...parent.instrumentation, ...child.instrumentation },
    arrangement: { ...parent.arrangement, ...child.arrangement },
    mixing: { ...parent.mixing, ...child.mixing },
  } as StyleProfile;
}

/**
 * Interpolate between two styles (for blending, e.g. "jazz-pop fusion").
 * @param alpha 0.0 = fully style A, 1.0 = fully style B
 */
export function interpolateStyles(a: StyleProfile, b: StyleProfile, alpha: number): StyleProfile {
  const lerp = (x: number, y: number) => x + (y - x) * alpha;
  return {
    id: `${a.id}_x_${b.id}`,
    name: `${a.name} × ${b.name}`,
    description: `Blend of ${a.name} and ${b.name} (${Math.round(alpha * 100)}%)`,
    tags: [...new Set([...a.tags, ...b.tags])],
    rhythm: {
      bpmRange: [lerp(a.rhythm.bpmRange[0], b.rhythm.bpmRange[0]), lerp(a.rhythm.bpmRange[1], b.rhythm.bpmRange[1])],
      defaultBpm: lerp(a.rhythm.defaultBpm, b.rhythm.defaultBpm),
      timeSignature: alpha < 0.5 ? a.rhythm.timeSignature : b.rhythm.timeSignature,
      swing: lerp(a.rhythm.swing, b.rhythm.swing),
      subdivisionBias: alpha < 0.5 ? a.rhythm.subdivisionBias : b.rhythm.subdivisionBias,
      syncopationLevel: lerp(a.rhythm.syncopationLevel, b.rhythm.syncopationLevel),
      grooveTemplates: alpha < 0.5 ? a.rhythm.grooveTemplates : b.rhythm.grooveTemplates,
    },
    harmony: {
      preferredModes: alpha < 0.5 ? a.harmony.preferredModes : b.harmony.preferredModes,
      chordComplexity: lerp(a.harmony.chordComplexity, b.harmony.chordComplexity),
      commonProgressions: [...a.harmony.commonProgressions, ...b.harmony.commonProgressions],
      allowedChordQualities: [...new Set([...a.harmony.allowedChordQualities, ...b.harmony.allowedChordQualities])],
      modulationFrequency: lerp(a.harmony.modulationFrequency, b.harmony.modulationFrequency),
      chromaticismLevel: lerp(a.harmony.chromaticismLevel, b.harmony.chromaticismLevel),
      tensionCurve: blendRecords(a.harmony.tensionCurve, b.harmony.tensionCurve, alpha),
    },
    melody: {
      pitchRange: [lerp(a.melody.pitchRange[0], b.melody.pitchRange[0]), lerp(a.melody.pitchRange[1], b.melody.pitchRange[1])],
      preferredIntervals: alpha < 0.5 ? a.melody.preferredIntervals : b.melody.preferredIntervals,
      maxLeap: lerp(a.melody.maxLeap, b.melody.maxLeap),
      stepToLeapRatio: lerp(a.melody.stepToLeapRatio, b.melody.stepToLeapRatio),
      noteDensity: lerp(a.melody.noteDensity, b.melody.noteDensity),
      restFrequency: lerp(a.melody.restFrequency, b.melody.restFrequency),
      ornamentTypes: [...new Set([...a.melody.ornamentTypes, ...b.melody.ornamentTypes])],
      phraseLength: [lerp(a.melody.phraseLength[0], b.melody.phraseLength[0]), lerp(a.melody.phraseLength[1], b.melody.phraseLength[1])],
      repetitionLevel: lerp(a.melody.repetitionLevel, b.melody.repetitionLevel),
      scaleAdherence: lerp(a.melody.scaleAdherence, b.melody.scaleAdherence),
    },
    instrumentation: alpha < 0.5 ? a.instrumentation : b.instrumentation,
    arrangement: {
      typicalStructure: alpha < 0.5 ? a.arrangement.typicalStructure : b.arrangement.typicalStructure,
      sectionLengths: alpha < 0.5 ? a.arrangement.sectionLengths : b.arrangement.sectionLengths,
      buildupStrategy: alpha < 0.5 ? a.arrangement.buildupStrategy : b.arrangement.buildupStrategy,
      dynamicRange: lerp(a.arrangement.dynamicRange, b.arrangement.dynamicRange),
      energyCurve: blendRecords(a.arrangement.energyCurve, b.arrangement.energyCurve, alpha),
    },
    mixing: {
      reverbLevel: lerp(a.mixing.reverbLevel, b.mixing.reverbLevel),
      stereoWidth: lerp(a.mixing.stereoWidth, b.mixing.stereoWidth),
      compressionAmount: lerp(a.mixing.compressionAmount, b.mixing.compressionAmount),
      brightnessEQ: lerp(a.mixing.brightnessEQ, b.mixing.brightnessEQ),
      lowEndEmphasis: lerp(a.mixing.lowEndEmphasis, b.mixing.lowEndEmphasis),
    },
  };
}

function blendRecords(a: Record<string, number>, b: Record<string, number>, alpha: number): Record<string, number> {
  const keys = [...new Set([...Object.keys(a), ...Object.keys(b)])];
  const result: Record<string, number> = {};
  for (const k of keys) {
    result[k] = (a[k] ?? 0) + ((b[k] ?? 0) - (a[k] ?? 0)) * alpha;
  }
  return result;
}
