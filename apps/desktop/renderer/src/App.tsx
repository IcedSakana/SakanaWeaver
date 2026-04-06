import { PianoRoll } from '@sakana-weaver/ui-components/piano-roll';
import { Timeline } from '@sakana-weaver/ui-components/timeline';
import { Mixer } from '@sakana-weaver/ui-components/mixer';
import { AIPanel } from '@sakana-weaver/ui-components/ai-panel';

export function App() {
  return (
    <div className="app">
      <header className="app-header">SakanaWeaver</header>
      <main className="app-layout">
        <Timeline />
        <PianoRoll />
        <Mixer />
        <AIPanel />
      </main>
    </div>
  );
}
