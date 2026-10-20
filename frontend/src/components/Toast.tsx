import { useStore } from '../state/StoreContext';

export function Toast() {
  const { toast } = useStore();
  return (
    <div className={'toast' + (toast ? ' is-visible' : '')} role="status" aria-live="polite">
      {toast?.message}
    </div>
  );
}
