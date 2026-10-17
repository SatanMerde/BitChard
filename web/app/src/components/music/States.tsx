import { PrefetchLink } from '@/components/PrefetchLink';
import styles from './States.module.css';

/** In place of a YouTube page when the extension isn't installed (prompt §1): an invitation, not an error. */
export function NeedsExtension({ what = 'YouTube Music' }: { what?: string }) {
  return (
    <div className={styles.state}>
      <p className={styles.title}>Browse {what} with the BitChard extension</p>
      <p className={styles.detail}>
        The extension lets this page reach YouTube Music from your own browser. Addons and lyrics work without it.
      </p>
      <PrefetchLink to="/extension" className={styles.button}>
        Get the Extension
      </PrefetchLink>
    </div>
  );
}

export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className={styles.state}>
      <p className={styles.title}>Couldn’t load this page</p>
      <p className={styles.detail}>{error instanceof Error ? error.message : String(error)}</p>
      <button className={styles.button} onClick={onRetry}>
        Try Again
      </button>
    </div>
  );
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className={styles.state}>
      <p className={styles.title}>{title}</p>
      {detail && <p className={styles.detail}>{detail}</p>}
    </div>
  );
}

/** A later page of a long list failed: what's loaded stays, with a way to try again. */
export function LoadMoreError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className={styles.more}>
      <span>Couldn’t load more.</span>
      <button className={styles.moreButton} onClick={onRetry}>
        Try Again
      </button>
    </div>
  );
}
