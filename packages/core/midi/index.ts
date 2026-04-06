/**
 * MIDI parsing and generation utilities
 */

import { MidiNote } from '../project/types';

export interface MidiEvent {
  type: 'noteOn' | 'noteOff' | 'controlChange' | 'programChange';
  channel: number;
  tick: number;
  data: Record<string, number>;
}

export function notesToEvents(notes: MidiNote[]): MidiEvent[] {
  const events: MidiEvent[] = [];
  for (const note of notes) {
    events.push({
      type: 'noteOn',
      channel: note.channel,
      tick: note.startTick,
      data: { pitch: note.pitch, velocity: note.velocity },
    });
    events.push({
      type: 'noteOff',
      channel: note.channel,
      tick: note.startTick + note.durationTicks,
      data: { pitch: note.pitch, velocity: 0 },
    });
  }
  return events.sort((a, b) => a.tick - b.tick);
}

export function quantize(notes: MidiNote[], gridTicks: number): MidiNote[] {
  return notes.map(note => ({
    ...note,
    startTick: Math.round(note.startTick / gridTicks) * gridTicks,
  }));
}

export function humanize(notes: MidiNote[], timingJitter: number, velocityJitter: number): MidiNote[] {
  return notes.map(note => ({
    ...note,
    startTick: note.startTick + Math.round((Math.random() - 0.5) * 2 * timingJitter),
    velocity: Math.max(1, Math.min(127, note.velocity + Math.round((Math.random() - 0.5) * 2 * velocityJitter))),
  }));
}
