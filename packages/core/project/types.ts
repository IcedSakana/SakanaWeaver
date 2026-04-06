/**
 * Core domain model — Project structure
 */

export interface Project {
  id: string;
  name: string;
  bpm: number;
  timeSignature: [number, number]; // e.g. [4, 4]
  key: MusicalKey;
  tracks: Track[];
  sections: ArrangementSection[];
  createdAt: Date;
  updatedAt: Date;
}

export interface MusicalKey {
  root: string;       // C, C#, D, ...
  mode: 'major' | 'minor' | 'dorian' | 'mixolydian' | string;
}

export interface Track {
  id: string;
  name: string;
  type: 'midi' | 'audio';
  instrument: string;
  clips: Clip[];
  effects: EffectSlot[];
  volume: number;     // 0.0 - 1.0
  pan: number;        // -1.0 (L) to 1.0 (R)
  mute: boolean;
  solo: boolean;
}

export interface Clip {
  id: string;
  type: 'midi' | 'audio';
  startTick: number;
  durationTicks: number;
  dataRef: string;    // reference to MIDI data or audio file
}

export interface MidiNote {
  pitch: number;      // 0-127
  velocity: number;   // 0-127
  startTick: number;
  durationTicks: number;
  channel: number;
}

export interface EffectSlot {
  id: string;
  pluginId: string;
  enabled: boolean;
  parameters: Record<string, number>;
}

export type SectionType = 'intro' | 'verse' | 'pre-chorus' | 'chorus' | 'bridge' | 'outro' | 'solo' | 'breakdown';

export interface ArrangementSection {
  id: string;
  type: SectionType;
  label: string;
  startTick: number;
  durationTicks: number;
}
