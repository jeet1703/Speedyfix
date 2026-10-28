import { useEffect, useState } from 'react';
import { progress } from '../api/progress';

/**
 * A thin bar across the top of the window while any API call runs. It waits 200ms before showing, so quick calls
 * don't flash it, and finishes with a short fill so it doesn't just vanish.
 */
export function TopLoader() {
  const [state, setState] = useState<'idle' | 'busy' | 'done'>('idle');

  useEffect(() => {
    let showTimer: number | undefined;
    let hideTimer: number | undefined;
    let shown = false;
    const unsubscribe = progress.subscribe((busy) => {
      window.clearTimeout(showTimer);
      window.clearTimeout(hideTimer);
      if (busy) {
        showTimer = window.setTimeout(() => { shown = true; setState('busy'); }, 200);
      } else if (shown) {
        shown = false;
        setState('done');
        hideTimer = window.setTimeout(() => setState('idle'), 400);
      }
    });
    return () => { unsubscribe(); window.clearTimeout(showTimer); window.clearTimeout(hideTimer); };
  }, []);

  if (state === 'idle') return null;
  return (
    <div className={'top-loader' + (state === 'done' ? ' is-done' : '')} role="progressbar" aria-label="Loading"
      aria-busy={state === 'busy'}>
      <span className="top-loader__bar" />
    </div>
  );
}
