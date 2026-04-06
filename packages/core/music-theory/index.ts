/**
 * Music Theory utilities — scales, chords, intervals
 */

export const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'] as const;

export const SCALE_INTERVALS: Record<string, number[]> = {
  major:        [0, 2, 4, 5, 7, 9, 11],
  minor:        [0, 2, 3, 5, 7, 8, 10],
  dorian:       [0, 2, 3, 5, 7, 9, 10],
  mixolydian:   [0, 2, 4, 5, 7, 9, 10],
  pentatonic:   [0, 2, 4, 7, 9],
  blues:        [0, 3, 5, 6, 7, 10],
};

export const CHORD_FORMULAS: Record<string, number[]> = {
  major:  [0, 4, 7],
  minor:  [0, 3, 7],
  dim:    [0, 3, 6],
  aug:    [0, 4, 8],
  dom7:   [0, 4, 7, 10],
  maj7:   [0, 4, 7, 11],
  min7:   [0, 3, 7, 10],
};

export function getScale(root: string, mode: string): string[] {
  const rootIdx = NOTE_NAMES.indexOf(root as typeof NOTE_NAMES[number]);
  if (rootIdx === -1) throw new Error(`Unknown root note: ${root}`);
  const intervals = SCALE_INTERVALS[mode];
  if (!intervals) throw new Error(`Unknown scale mode: ${mode}`);
  return intervals.map(i => NOTE_NAMES[(rootIdx + i) % 12]);
}

export function getChord(root: string, quality: string): string[] {
  const rootIdx = NOTE_NAMES.indexOf(root as typeof NOTE_NAMES[number]);
  if (rootIdx === -1) throw new Error(`Unknown root note: ${root}`);
  const formula = CHORD_FORMULAS[quality];
  if (!formula) throw new Error(`Unknown chord quality: ${quality}`);
  return formula.map(i => NOTE_NAMES[(rootIdx + i) % 12]);
}

export function midiToNoteName(midi: number): string {
  const octave = Math.floor(midi / 12) - 1;
  return `${NOTE_NAMES[midi % 12]}${octave}`;
}

export function noteNameToMidi(name: string): number {
  const match = name.match(/^([A-G]#?)(-?\d+)$/);
  if (!match) throw new Error(`Invalid note name: ${name}`);
  const noteIdx = NOTE_NAMES.indexOf(match[1] as typeof NOTE_NAMES[number]);
  if (noteIdx === -1) throw new Error(`Invalid note: ${match[1]}`);
  return (parseInt(match[2]) + 1) * 12 + noteIdx;
}
