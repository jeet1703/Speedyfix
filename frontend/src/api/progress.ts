/** Counts API calls in flight, so the top loading bar can show while any of them runs. */
let inFlight = 0;
const listeners = new Set<(busy: boolean) => void>();

function notify() {
  listeners.forEach((l) => l(inFlight > 0));
}

export const progress = {
  start() {
    inFlight += 1;
    if (inFlight === 1) notify();
  },
  done() {
    inFlight = Math.max(0, inFlight - 1);
    if (inFlight === 0) notify();
  },
  subscribe(listener: (busy: boolean) => void) {
    listeners.add(listener);
    listener(inFlight > 0);
    return () => { listeners.delete(listener); };
  }
};
