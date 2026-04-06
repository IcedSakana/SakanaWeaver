/**
 * Built-in style presets for common music genres.
 *
 * Each preset is a complete StyleProfile defining rhythm, harmony, melody,
 * instrumentation, arrangement, and mixing characteristics.
 */

import type { StyleProfile } from './types';

// ═══════════════════════════════════════════════════════════════════
//  POP
// ═══════════════════════════════════════════════════════════════════

export const POP: StyleProfile = {
  id: 'pop',
  name: 'Pop',
  description: 'Modern pop music — catchy melodies, simple chord progressions, four-on-the-floor beats',
  tags: ['pop', 'mainstream', 'catchy'],
  rhythm: {
    bpmRange: [100, 130],
    defaultBpm: 120,
    timeSignature: [4, 4],
    swing: 0.0,
    subdivisionBias: 'straight',
    syncopationLevel: 0.3,
    grooveTemplates: [
      {
        name: 'pop-basic',
        stepsPerBar: 16,
        pattern: {
          kick:         [true,false,false,false, true,false,false,false, true,false,false,false, true,false,false,false],
          snare:        [false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false],
          hihat_closed: [true,false,true,false, true,false,true,false, true,false,true,false, true,false,true,false],
        },
        velocityAccents: [1.0,0.6,0.8,0.6, 1.0,0.6,0.8,0.6, 1.0,0.6,0.8,0.6, 1.0,0.6,0.8,0.6],
      },
    ],
  },
  harmony: {
    preferredModes: ['major', 'minor'],
    chordComplexity: 0.2,
    commonProgressions: [
      { name: 'I-V-vi-IV', degrees: [1,5,6,4], qualities: ['major','major','minor','major'], weight: 1.0 },
      { name: 'vi-IV-I-V', degrees: [6,4,1,5], qualities: ['minor','major','major','major'], weight: 0.8 },
      { name: 'I-IV-V-IV', degrees: [1,4,5,4], qualities: ['major','major','major','major'], weight: 0.5 },
    ],
    allowedChordQualities: ['major', 'minor', 'dom7'],
    modulationFrequency: 0.1,
    chromaticismLevel: 0.1,
    tensionCurve: { intro: 0.2, verse: 0.3, 'pre-chorus': 0.6, chorus: 0.8, bridge: 0.5, outro: 0.2, solo: 0.6, breakdown: 0.3 },
  },
  melody: {
    pitchRange: [60, 84],   // C4 - C6
    preferredIntervals: [1, 2, 3, 5, 7],
    maxLeap: 12,
    stepToLeapRatio: 0.7,
    noteDensity: 2.0,
    restFrequency: 0.2,
    ornamentTypes: ['none'],
    phraseLength: [2, 4],
    repetitionLevel: 0.6,
    scaleAdherence: 0.9,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'vocal_synth',     octaveRange: [4,5], velocityRange: [80,120] },
      { role: 'harmony', instrument: 'acoustic_piano',  octaveRange: [3,5], velocityRange: [60,100] },
      { role: 'bass',    instrument: 'electric_bass',   octaveRange: [1,3], velocityRange: [70,110] },
      { role: 'drums',   instrument: 'drum_kit',        octaveRange: [0,0], velocityRange: [80,120] },
      { role: 'pad',     instrument: 'synth_pad',       octaveRange: [3,5], velocityRange: [40,80] },
    ],
    optionalInstruments: [
      { role: 'arpeggio', instrument: 'synth_pluck',    octaveRange: [4,6], velocityRange: [50,90] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 4, chorus: 5, bridge: 3, outro: 2, solo: 4, breakdown: 2 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'pre-chorus', 'chorus', 'verse', 'pre-chorus', 'chorus', 'bridge', 'chorus', 'outro'],
    sectionLengths: { intro: [4,8], verse: [8,16], 'pre-chorus': [4,8], chorus: [8,16], bridge: [4,8], outro: [4,8], solo: [8,16], breakdown: [4,8] },
    buildupStrategy: 'additive',
    dynamicRange: 0.5,
    energyCurve: { intro: 0.3, verse: 0.5, 'pre-chorus': 0.7, chorus: 1.0, bridge: 0.5, outro: 0.3, solo: 0.8, breakdown: 0.3 },
  },
  mixing: {
    reverbLevel: 0.3,
    stereoWidth: 0.6,
    compressionAmount: 0.6,
    brightnessEQ: 0.3,
    lowEndEmphasis: 0.4,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  JAZZ
// ═══════════════════════════════════════════════════════════════════

export const JAZZ: StyleProfile = {
  id: 'jazz',
  name: 'Jazz',
  description: 'Classic jazz — swing feel, extended chords, improvisation-friendly melodies',
  tags: ['jazz', 'swing', 'sophisticated'],
  rhythm: {
    bpmRange: [100, 180],
    defaultBpm: 140,
    timeSignature: [4, 4],
    swing: 0.65,
    subdivisionBias: 'triplet',
    syncopationLevel: 0.7,
    grooveTemplates: [
      {
        name: 'jazz-swing',
        stepsPerBar: 12, // triplet grid
        pattern: {
          ride:         [true,false,true, true,false,true, true,false,true, true,false,true],
          kick:         [true,false,false, false,false,true, false,false,false, true,false,false],
          snare:        [false,false,false, false,false,false, false,false,false, false,false,false], // ghost notes via velocity
          hihat_closed: [false,false,true, false,false,true, false,false,true, false,false,true],
        },
        velocityAccents: [1.0,0.3,0.6, 0.8,0.3,0.5, 1.0,0.3,0.6, 0.8,0.3,0.5],
      },
    ],
  },
  harmony: {
    preferredModes: ['dorian', 'mixolydian', 'major', 'minor'],
    chordComplexity: 0.8,
    commonProgressions: [
      { name: 'ii-V-I',        degrees: [2,5,1],   qualities: ['min7','dom7','maj7'], weight: 1.0 },
      { name: 'I-vi-ii-V',     degrees: [1,6,2,5], qualities: ['maj7','min7','min7','dom7'], weight: 0.8 },
      { name: 'iii-vi-ii-V',   degrees: [3,6,2,5], qualities: ['min7','min7','min7','dom7'], weight: 0.6 },
      { name: 'I-IV-iii-vi',   degrees: [1,4,3,6], qualities: ['maj7','maj7','min7','min7'], weight: 0.4 },
    ],
    allowedChordQualities: ['major', 'minor', 'dom7', 'maj7', 'min7', 'dim', 'aug'],
    modulationFrequency: 0.4,
    chromaticismLevel: 0.5,
    tensionCurve: { intro: 0.3, verse: 0.5, 'pre-chorus': 0.6, chorus: 0.7, bridge: 0.8, outro: 0.3, solo: 0.9, breakdown: 0.4 },
  },
  melody: {
    pitchRange: [55, 84],   // G3 - C6
    preferredIntervals: [1, 2, 3, 4, 5, 7, 10],
    maxLeap: 14,
    stepToLeapRatio: 0.5,
    noteDensity: 3.0,
    restFrequency: 0.25,
    ornamentTypes: ['grace_note', 'slide', 'bend'],
    phraseLength: [2, 8],
    repetitionLevel: 0.2,
    scaleAdherence: 0.6,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'tenor_sax',       midiProgram: 66,  octaveRange: [3,5], velocityRange: [60,110] },
      { role: 'harmony', instrument: 'acoustic_piano',  midiProgram: 0,   octaveRange: [2,5], velocityRange: [50,100] },
      { role: 'bass',    instrument: 'upright_bass',    midiProgram: 32,  octaveRange: [1,3], velocityRange: [60,100] },
      { role: 'drums',   instrument: 'jazz_kit',                          octaveRange: [0,0], velocityRange: [40,100] },
    ],
    optionalInstruments: [
      { role: 'lead',    instrument: 'trumpet',         midiProgram: 56,  octaveRange: [3,5], velocityRange: [60,110] },
      { role: 'harmony', instrument: 'electric_guitar',  midiProgram: 26,  octaveRange: [2,5], velocityRange: [40,90] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 3, chorus: 4, bridge: 3, outro: 2, solo: 2, breakdown: 2 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'chorus', 'solo', 'verse', 'chorus', 'outro'],
    sectionLengths: { intro: [4,8], verse: [8,16], 'pre-chorus': [4,4], chorus: [8,16], bridge: [8,16], outro: [4,8], solo: [16,32], breakdown: [4,8] },
    buildupStrategy: 'layered',
    dynamicRange: 0.8,
    energyCurve: { intro: 0.3, verse: 0.5, 'pre-chorus': 0.6, chorus: 0.7, bridge: 0.6, outro: 0.3, solo: 0.9, breakdown: 0.4 },
  },
  mixing: {
    reverbLevel: 0.4,
    stereoWidth: 0.5,
    compressionAmount: 0.2,
    brightnessEQ: 0.0,
    lowEndEmphasis: 0.5,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  ROCK
// ═══════════════════════════════════════════════════════════════════

export const ROCK: StyleProfile = {
  id: 'rock',
  name: 'Rock',
  description: 'Rock — driving rhythms, power chords, electric guitars, strong dynamics',
  tags: ['rock', 'guitar', 'energetic'],
  rhythm: {
    bpmRange: [110, 160],
    defaultBpm: 130,
    timeSignature: [4, 4],
    swing: 0.0,
    subdivisionBias: 'straight',
    syncopationLevel: 0.3,
    grooveTemplates: [
      {
        name: 'rock-basic',
        stepsPerBar: 16,
        pattern: {
          kick:         [true,false,false,false, false,false,false,false, true,false,true,false, false,false,false,false],
          snare:        [false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false],
          hihat_closed: [true,false,true,false, true,false,true,false, true,false,true,false, true,false,true,false],
          crash:        [true,false,false,false, false,false,false,false, false,false,false,false, false,false,false,false],
        },
        velocityAccents: [1.0,0.5,0.7,0.5, 1.0,0.5,0.7,0.5, 0.9,0.5,0.7,0.5, 1.0,0.5,0.7,0.5],
      },
    ],
  },
  harmony: {
    preferredModes: ['major', 'minor', 'mixolydian'],
    chordComplexity: 0.2,
    commonProgressions: [
      { name: 'I-IV-V',    degrees: [1,4,5],   qualities: ['major','major','major'], weight: 1.0 },
      { name: 'I-bVII-IV', degrees: [1,7,4],   qualities: ['major','major','major'], weight: 0.7 },
      { name: 'i-bVI-bVII', degrees: [1,6,7],  qualities: ['minor','major','major'], weight: 0.6 },
      { name: 'I-V-vi-IV', degrees: [1,5,6,4], qualities: ['major','major','minor','major'], weight: 0.5 },
    ],
    allowedChordQualities: ['major', 'minor', 'dom7'],
    modulationFrequency: 0.1,
    chromaticismLevel: 0.15,
    tensionCurve: { intro: 0.4, verse: 0.5, 'pre-chorus': 0.7, chorus: 1.0, bridge: 0.5, outro: 0.4, solo: 0.9, breakdown: 0.3 },
  },
  melody: {
    pitchRange: [55, 79],   // G3 - G5
    preferredIntervals: [1, 2, 3, 5, 7],
    maxLeap: 12,
    stepToLeapRatio: 0.6,
    noteDensity: 2.5,
    restFrequency: 0.15,
    ornamentTypes: ['bend', 'slide'],
    phraseLength: [2, 4],
    repetitionLevel: 0.5,
    scaleAdherence: 0.8,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'electric_guitar_lead', midiProgram: 29, octaveRange: [3,5], velocityRange: [80,127] },
      { role: 'harmony', instrument: 'electric_guitar_rhythm', midiProgram: 27, octaveRange: [2,4], velocityRange: [70,120] },
      { role: 'bass',    instrument: 'electric_bass',   midiProgram: 33, octaveRange: [1,3], velocityRange: [80,120] },
      { role: 'drums',   instrument: 'rock_kit',                         octaveRange: [0,0], velocityRange: [90,127] },
    ],
    optionalInstruments: [
      { role: 'pad',     instrument: 'organ',           midiProgram: 16, octaveRange: [3,5], velocityRange: [50,90] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 3, chorus: 4, bridge: 3, outro: 3, solo: 3, breakdown: 1 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'verse', 'chorus', 'verse', 'chorus', 'solo', 'chorus', 'outro'],
    sectionLengths: { intro: [4,8], verse: [8,16], 'pre-chorus': [4,8], chorus: [8,16], bridge: [8,8], outro: [4,8], solo: [8,16], breakdown: [4,4] },
    buildupStrategy: 'contrast',
    dynamicRange: 0.7,
    energyCurve: { intro: 0.5, verse: 0.6, 'pre-chorus': 0.7, chorus: 1.0, bridge: 0.5, outro: 0.6, solo: 0.9, breakdown: 0.2 },
  },
  mixing: {
    reverbLevel: 0.25,
    stereoWidth: 0.7,
    compressionAmount: 0.5,
    brightnessEQ: 0.2,
    lowEndEmphasis: 0.5,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  LO-FI HIP HOP
// ═══════════════════════════════════════════════════════════════════

export const LOFI: StyleProfile = {
  id: 'lofi',
  name: 'Lo-Fi Hip Hop',
  description: 'Lo-fi hip hop — chill, jazzy chords, dusty drums, mellow vibes',
  tags: ['lofi', 'hip-hop', 'chill', 'beats'],
  rhythm: {
    bpmRange: [70, 95],
    defaultBpm: 85,
    timeSignature: [4, 4],
    swing: 0.3,
    subdivisionBias: 'shuffle',
    syncopationLevel: 0.4,
    grooveTemplates: [
      {
        name: 'lofi-boom-bap',
        stepsPerBar: 16,
        pattern: {
          kick:         [true,false,false,false, false,false,false,true, false,false,true,false, false,false,false,false],
          snare:        [false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false],
          hihat_closed: [true,true,true,true, true,true,true,true, true,true,true,true, true,true,true,true],
        },
        velocityAccents: [1.0,0.4,0.7,0.4, 0.9,0.4,0.6,0.5, 0.8,0.4,0.7,0.4, 0.9,0.4,0.6,0.4],
      },
    ],
  },
  harmony: {
    preferredModes: ['dorian', 'minor', 'mixolydian'],
    chordComplexity: 0.6,
    commonProgressions: [
      { name: 'ii7-V7-Imaj7',   degrees: [2,5,1],   qualities: ['min7','dom7','maj7'], weight: 1.0 },
      { name: 'Imaj7-vi7-ii7-V7', degrees: [1,6,2,5], qualities: ['maj7','min7','min7','dom7'], weight: 0.8 },
      { name: 'i7-IV7',         degrees: [1,4],     qualities: ['min7','dom7'], weight: 0.5 },
    ],
    allowedChordQualities: ['maj7', 'min7', 'dom7', 'minor', 'dim'],
    modulationFrequency: 0.1,
    chromaticismLevel: 0.3,
    tensionCurve: { intro: 0.3, verse: 0.4, 'pre-chorus': 0.5, chorus: 0.5, bridge: 0.4, outro: 0.3, solo: 0.5, breakdown: 0.3 },
  },
  melody: {
    pitchRange: [60, 79],   // C4 - G5
    preferredIntervals: [1, 2, 3, 5, 7],
    maxLeap: 10,
    stepToLeapRatio: 0.7,
    noteDensity: 1.5,
    restFrequency: 0.35,
    ornamentTypes: ['vibrato', 'slide'],
    phraseLength: [2, 4],
    repetitionLevel: 0.5,
    scaleAdherence: 0.7,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'electric_piano',  midiProgram: 4,  octaveRange: [4,5], velocityRange: [40,80] },
      { role: 'harmony', instrument: 'acoustic_guitar', midiProgram: 24, octaveRange: [3,5], velocityRange: [30,70] },
      { role: 'bass',    instrument: 'synth_bass',      midiProgram: 38, octaveRange: [1,3], velocityRange: [60,90] },
      { role: 'drums',   instrument: 'lofi_kit',                         octaveRange: [0,0], velocityRange: [50,90] },
    ],
    optionalInstruments: [
      { role: 'pad',     instrument: 'vinyl_texture',                    octaveRange: [0,0], velocityRange: [20,40] },
      { role: 'fx',      instrument: 'rain_ambience',                    octaveRange: [0,0], velocityRange: [20,40] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 3, chorus: 4, bridge: 3, outro: 2, solo: 3, breakdown: 2 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'chorus', 'verse', 'chorus', 'outro'],
    sectionLengths: { intro: [4,8], verse: [8,16], 'pre-chorus': [4,4], chorus: [8,8], bridge: [4,8], outro: [4,8], solo: [8,8], breakdown: [4,8] },
    buildupStrategy: 'layered',
    dynamicRange: 0.3,
    energyCurve: { intro: 0.3, verse: 0.4, 'pre-chorus': 0.5, chorus: 0.5, bridge: 0.4, outro: 0.3, solo: 0.5, breakdown: 0.3 },
  },
  mixing: {
    reverbLevel: 0.5,
    stereoWidth: 0.4,
    compressionAmount: 0.4,
    brightnessEQ: -0.3,
    lowEndEmphasis: 0.6,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  EDM (Electronic Dance Music)
// ═══════════════════════════════════════════════════════════════════

export const EDM: StyleProfile = {
  id: 'edm',
  name: 'EDM',
  description: 'Electronic dance music — four-on-the-floor, synth-heavy, high energy builds and drops',
  tags: ['electronic', 'dance', 'edm', 'club'],
  rhythm: {
    bpmRange: [120, 150],
    defaultBpm: 128,
    timeSignature: [4, 4],
    swing: 0.0,
    subdivisionBias: 'straight',
    syncopationLevel: 0.2,
    grooveTemplates: [
      {
        name: 'four-on-the-floor',
        stepsPerBar: 16,
        pattern: {
          kick:         [true,false,false,false, true,false,false,false, true,false,false,false, true,false,false,false],
          clap:         [false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false],
          hihat_closed: [false,false,true,false, false,false,true,false, false,false,true,false, false,false,true,false],
          hihat_open:   [false,false,false,false, false,false,false,true, false,false,false,false, false,false,false,true],
        },
        velocityAccents: [1.0,0.4,0.7,0.4, 1.0,0.4,0.7,0.5, 1.0,0.4,0.7,0.4, 1.0,0.4,0.7,0.5],
      },
    ],
  },
  harmony: {
    preferredModes: ['minor', 'major'],
    chordComplexity: 0.3,
    commonProgressions: [
      { name: 'i-VI-III-VII', degrees: [1,6,3,7], qualities: ['minor','major','major','major'], weight: 1.0 },
      { name: 'i-iv-VI-V',   degrees: [1,4,6,5], qualities: ['minor','minor','major','major'], weight: 0.7 },
      { name: 'i-VII-VI-VII', degrees: [1,7,6,7], qualities: ['minor','major','major','major'], weight: 0.5 },
    ],
    allowedChordQualities: ['major', 'minor', 'dom7'],
    modulationFrequency: 0.1,
    chromaticismLevel: 0.1,
    tensionCurve: { intro: 0.2, verse: 0.4, 'pre-chorus': 0.8, chorus: 1.0, bridge: 0.3, outro: 0.3, solo: 0.7, breakdown: 0.1 },
  },
  melody: {
    pitchRange: [55, 84],
    preferredIntervals: [1, 2, 3, 5, 7],
    maxLeap: 12,
    stepToLeapRatio: 0.6,
    noteDensity: 2.5,
    restFrequency: 0.2,
    ornamentTypes: ['none'],
    phraseLength: [4, 8],
    repetitionLevel: 0.7,
    scaleAdherence: 0.9,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'supersaw',        octaveRange: [4,6], velocityRange: [80,127] },
      { role: 'bass',    instrument: 'sub_bass',        octaveRange: [1,2], velocityRange: [100,127] },
      { role: 'drums',   instrument: 'edm_kit',         octaveRange: [0,0], velocityRange: [90,127] },
      { role: 'pad',     instrument: 'atmosphere_pad',  octaveRange: [3,5], velocityRange: [50,90] },
      { role: 'arpeggio', instrument: 'pluck_synth',    octaveRange: [4,6], velocityRange: [60,100] },
    ],
    optionalInstruments: [
      { role: 'fx',      instrument: 'riser_fx',        octaveRange: [0,0], velocityRange: [60,127] },
      { role: 'fx',      instrument: 'impact_fx',       octaveRange: [0,0], velocityRange: [100,127] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 4, chorus: 5, bridge: 2, outro: 3, solo: 4, breakdown: 1 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'pre-chorus', 'chorus', 'breakdown', 'pre-chorus', 'chorus', 'outro'],
    sectionLengths: { intro: [8,16], verse: [8,16], 'pre-chorus': [8,16], chorus: [8,16], bridge: [8,8], outro: [8,16], solo: [8,8], breakdown: [8,16] },
    buildupStrategy: 'additive',
    dynamicRange: 0.9,
    energyCurve: { intro: 0.2, verse: 0.5, 'pre-chorus': 0.8, chorus: 1.0, bridge: 0.3, outro: 0.3, solo: 0.8, breakdown: 0.1 },
  },
  mixing: {
    reverbLevel: 0.4,
    stereoWidth: 0.8,
    compressionAmount: 0.8,
    brightnessEQ: 0.4,
    lowEndEmphasis: 0.8,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  R&B
// ═══════════════════════════════════════════════════════════════════

export const RNB: StyleProfile = {
  id: 'rnb',
  name: 'R&B',
  description: 'R&B / Soul — smooth grooves, extended chords, soulful melodies',
  tags: ['rnb', 'soul', 'smooth', 'groove'],
  rhythm: {
    bpmRange: [65, 110],
    defaultBpm: 90,
    timeSignature: [4, 4],
    swing: 0.2,
    subdivisionBias: 'straight',
    syncopationLevel: 0.5,
    grooveTemplates: [
      {
        name: 'rnb-groove',
        stepsPerBar: 16,
        pattern: {
          kick:         [true,false,false,false, false,false,true,false, false,false,false,false, false,true,false,false],
          snare:        [false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false],
          hihat_closed: [true,true,true,true, true,true,true,true, true,true,true,true, true,true,true,true],
          rimshot:      [false,false,false,true, false,false,false,false, false,false,true,false, false,false,false,false],
        },
        velocityAccents: [1.0,0.5,0.6,0.7, 1.0,0.5,0.8,0.4, 0.9,0.5,0.6,0.5, 1.0,0.7,0.5,0.4],
      },
    ],
  },
  harmony: {
    preferredModes: ['dorian', 'minor', 'mixolydian'],
    chordComplexity: 0.7,
    commonProgressions: [
      { name: 'Imaj9-IV9',      degrees: [1,4],     qualities: ['maj7','dom7'], weight: 1.0 },
      { name: 'ii7-V7-Imaj7',   degrees: [2,5,1],   qualities: ['min7','dom7','maj7'], weight: 0.8 },
      { name: 'vi7-ii7-V7-I',   degrees: [6,2,5,1], qualities: ['min7','min7','dom7','maj7'], weight: 0.6 },
    ],
    allowedChordQualities: ['maj7', 'min7', 'dom7', 'minor', 'major', 'dim', 'aug'],
    modulationFrequency: 0.2,
    chromaticismLevel: 0.4,
    tensionCurve: { intro: 0.3, verse: 0.4, 'pre-chorus': 0.6, chorus: 0.7, bridge: 0.6, outro: 0.3, solo: 0.7, breakdown: 0.3 },
  },
  melody: {
    pitchRange: [55, 84],
    preferredIntervals: [1, 2, 3, 4, 5, 7],
    maxLeap: 12,
    stepToLeapRatio: 0.6,
    noteDensity: 2.5,
    restFrequency: 0.2,
    ornamentTypes: ['vibrato', 'slide', 'grace_note'],
    phraseLength: [2, 4],
    repetitionLevel: 0.4,
    scaleAdherence: 0.7,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'vocal_synth',     octaveRange: [3,5], velocityRange: [60,110] },
      { role: 'harmony', instrument: 'electric_piano',  midiProgram: 4,  octaveRange: [3,5], velocityRange: [40,80] },
      { role: 'bass',    instrument: 'fingered_bass',   midiProgram: 33, octaveRange: [1,3], velocityRange: [60,100] },
      { role: 'drums',   instrument: 'rnb_kit',                          octaveRange: [0,0], velocityRange: [50,100] },
      { role: 'pad',     instrument: 'warm_pad',        midiProgram: 89, octaveRange: [3,5], velocityRange: [30,60] },
    ],
    optionalInstruments: [
      { role: 'harmony', instrument: 'acoustic_guitar', midiProgram: 24, octaveRange: [3,5], velocityRange: [30,70] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 4, chorus: 5, bridge: 3, outro: 2, solo: 3, breakdown: 2 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'pre-chorus', 'chorus', 'verse', 'pre-chorus', 'chorus', 'bridge', 'chorus', 'outro'],
    sectionLengths: { intro: [4,8], verse: [8,16], 'pre-chorus': [4,8], chorus: [8,16], bridge: [8,8], outro: [4,8], solo: [8,16], breakdown: [4,8] },
    buildupStrategy: 'layered',
    dynamicRange: 0.5,
    energyCurve: { intro: 0.3, verse: 0.5, 'pre-chorus': 0.6, chorus: 0.8, bridge: 0.5, outro: 0.3, solo: 0.7, breakdown: 0.3 },
  },
  mixing: {
    reverbLevel: 0.4,
    stereoWidth: 0.5,
    compressionAmount: 0.4,
    brightnessEQ: 0.1,
    lowEndEmphasis: 0.6,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  CLASSICAL
// ═══════════════════════════════════════════════════════════════════

export const CLASSICAL: StyleProfile = {
  id: 'classical',
  name: 'Classical',
  description: 'Classical / orchestral — rich harmonies, counterpoint, wide dynamic range',
  tags: ['classical', 'orchestral', 'acoustic'],
  rhythm: {
    bpmRange: [60, 140],
    defaultBpm: 100,
    timeSignature: [4, 4],
    swing: 0.0,
    subdivisionBias: 'straight',
    syncopationLevel: 0.1,
    grooveTemplates: [],
  },
  harmony: {
    preferredModes: ['major', 'minor'],
    chordComplexity: 0.5,
    commonProgressions: [
      { name: 'I-IV-V-I',     degrees: [1,4,5,1], qualities: ['major','major','major','major'], weight: 1.0 },
      { name: 'i-iv-V-i',     degrees: [1,4,5,1], qualities: ['minor','minor','major','minor'], weight: 0.8 },
      { name: 'I-vi-IV-V',    degrees: [1,6,4,5], qualities: ['major','minor','major','major'], weight: 0.6 },
      { name: 'I-ii-V-I',     degrees: [1,2,5,1], qualities: ['major','minor','major','major'], weight: 0.5 },
    ],
    allowedChordQualities: ['major', 'minor', 'dim', 'aug', 'dom7', 'maj7'],
    modulationFrequency: 0.3,
    chromaticismLevel: 0.3,
    tensionCurve: { intro: 0.2, verse: 0.4, 'pre-chorus': 0.6, chorus: 0.8, bridge: 0.7, outro: 0.3, solo: 0.8, breakdown: 0.2 },
  },
  melody: {
    pitchRange: [48, 84],   // C3 - C6
    preferredIntervals: [1, 2, 3, 4, 5, 7, 12],
    maxLeap: 16,
    stepToLeapRatio: 0.65,
    noteDensity: 2.0,
    restFrequency: 0.2,
    ornamentTypes: ['trill', 'mordent', 'grace_note'],
    phraseLength: [4, 8],
    repetitionLevel: 0.4,
    scaleAdherence: 0.85,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'violin',          midiProgram: 40, octaveRange: [3,6], velocityRange: [40,120] },
      { role: 'harmony', instrument: 'viola',           midiProgram: 41, octaveRange: [3,5], velocityRange: [40,100] },
      { role: 'harmony', instrument: 'cello',           midiProgram: 42, octaveRange: [2,4], velocityRange: [40,110] },
      { role: 'bass',    instrument: 'contrabass',      midiProgram: 43, octaveRange: [1,3], velocityRange: [40,100] },
    ],
    optionalInstruments: [
      { role: 'lead',    instrument: 'flute',           midiProgram: 73, octaveRange: [4,6], velocityRange: [40,100] },
      { role: 'harmony', instrument: 'french_horn',     midiProgram: 60, octaveRange: [2,4], velocityRange: [40,100] },
      { role: 'pad',     instrument: 'string_ensemble', midiProgram: 48, octaveRange: [3,5], velocityRange: [30,80] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 4, chorus: 6, bridge: 4, outro: 3, solo: 2, breakdown: 2 },
  },
  arrangement: {
    typicalStructure: ['intro', 'verse', 'verse', 'chorus', 'bridge', 'verse', 'chorus', 'outro'],
    sectionLengths: { intro: [8,16], verse: [16,32], 'pre-chorus': [4,8], chorus: [16,32], bridge: [8,16], outro: [8,16], solo: [16,32], breakdown: [8,8] },
    buildupStrategy: 'layered',
    dynamicRange: 1.0,
    energyCurve: { intro: 0.2, verse: 0.4, 'pre-chorus': 0.6, chorus: 0.9, bridge: 0.6, outro: 0.3, solo: 0.8, breakdown: 0.2 },
  },
  mixing: {
    reverbLevel: 0.6,
    stereoWidth: 0.7,
    compressionAmount: 0.1,
    brightnessEQ: 0.0,
    lowEndEmphasis: 0.3,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  REGGAETON
// ═══════════════════════════════════════════════════════════════════

export const REGGAETON: StyleProfile = {
  id: 'reggaeton',
  name: 'Reggaeton',
  description: 'Reggaeton — dembow rhythm, latin percussion, trap-influenced production',
  tags: ['reggaeton', 'latin', 'dembow', 'urban'],
  rhythm: {
    bpmRange: [85, 105],
    defaultBpm: 95,
    timeSignature: [4, 4],
    swing: 0.0,
    subdivisionBias: 'straight',
    syncopationLevel: 0.6,
    grooveTemplates: [
      {
        name: 'dembow',
        stepsPerBar: 16,
        pattern: {
          kick:         [true,false,false,false, false,false,true,false, true,false,false,false, false,false,true,false],
          snare:        [false,false,false,true, false,false,false,false, false,false,false,true, false,false,false,false],
          hihat_closed: [true,false,true,false, true,false,true,false, true,false,true,false, true,false,true,false],
          rimshot:      [false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false],
        },
        velocityAccents: [1.0,0.5,0.7,0.8, 1.0,0.5,0.8,0.5, 1.0,0.5,0.7,0.8, 1.0,0.5,0.8,0.5],
      },
    ],
  },
  harmony: {
    preferredModes: ['minor', 'major'],
    chordComplexity: 0.2,
    commonProgressions: [
      { name: 'i-VII-VI-V',   degrees: [1,7,6,5], qualities: ['minor','major','major','major'], weight: 1.0 },
      { name: 'i-iv-VII-III',  degrees: [1,4,7,3], qualities: ['minor','minor','major','major'], weight: 0.7 },
    ],
    allowedChordQualities: ['major', 'minor', 'dom7'],
    modulationFrequency: 0.05,
    chromaticismLevel: 0.1,
    tensionCurve: { intro: 0.3, verse: 0.5, 'pre-chorus': 0.7, chorus: 0.9, bridge: 0.4, outro: 0.3, solo: 0.6, breakdown: 0.2 },
  },
  melody: {
    pitchRange: [55, 79],
    preferredIntervals: [1, 2, 3, 5],
    maxLeap: 7,
    stepToLeapRatio: 0.8,
    noteDensity: 3.0,
    restFrequency: 0.15,
    ornamentTypes: ['slide'],
    phraseLength: [2, 4],
    repetitionLevel: 0.7,
    scaleAdherence: 0.85,
  },
  instrumentation: {
    defaultInstruments: [
      { role: 'lead',    instrument: 'vocal_synth',     octaveRange: [3,5], velocityRange: [70,120] },
      { role: 'bass',    instrument: 'trap_808',        octaveRange: [0,2], velocityRange: [90,127] },
      { role: 'drums',   instrument: 'reggaeton_kit',   octaveRange: [0,0], velocityRange: [80,127] },
      { role: 'harmony', instrument: 'pluck_synth',     octaveRange: [3,5], velocityRange: [50,90] },
    ],
    optionalInstruments: [
      { role: 'percussion', instrument: 'latin_perc',   octaveRange: [0,0], velocityRange: [60,100] },
    ],
    instrumentDensity: { intro: 2, verse: 3, 'pre-chorus': 3, chorus: 4, bridge: 2, outro: 2, solo: 3, breakdown: 1 },
  },
  arrangement: {
    typicalStructure: ['intro', 'chorus', 'verse', 'chorus', 'verse', 'chorus', 'outro'],
    sectionLengths: { intro: [4,8], verse: [8,16], 'pre-chorus': [4,4], chorus: [8,16], bridge: [4,8], outro: [4,8], solo: [8,8], breakdown: [4,8] },
    buildupStrategy: 'contrast',
    dynamicRange: 0.4,
    energyCurve: { intro: 0.4, verse: 0.6, 'pre-chorus': 0.7, chorus: 1.0, bridge: 0.4, outro: 0.4, solo: 0.7, breakdown: 0.2 },
  },
  mixing: {
    reverbLevel: 0.3,
    stereoWidth: 0.5,
    compressionAmount: 0.7,
    brightnessEQ: 0.2,
    lowEndEmphasis: 0.8,
  },
};

// ═══════════════════════════════════════════════════════════════════
//  Registry
// ═══════════════════════════════════════════════════════════════════

export const STYLE_PRESETS: Record<string, StyleProfile> = {
  pop: POP,
  jazz: JAZZ,
  rock: ROCK,
  lofi: LOFI,
  edm: EDM,
  rnb: RNB,
  classical: CLASSICAL,
  reggaeton: REGGAETON,
};

export function getStyleById(id: string): StyleProfile | undefined {
  return STYLE_PRESETS[id];
}

export function getAllStyleIds(): string[] {
  return Object.keys(STYLE_PRESETS);
}

export function searchStyles(query: string): StyleProfile[] {
  const q = query.toLowerCase();
  return Object.values(STYLE_PRESETS).filter(s =>
    s.name.toLowerCase().includes(q) ||
    s.tags.some(t => t.includes(q)) ||
    s.description.toLowerCase().includes(q)
  );
}
