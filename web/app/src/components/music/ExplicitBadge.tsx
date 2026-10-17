import styles from './ExplicitBadge.module.css';

/** The boxed "E" (report §2: 10×16, 4px after the title). Drawn for BitChard. */
export function ExplicitBadge() {
  return (
    <span className={styles.badge} role="img" aria-label="Explicit">
      E
    </span>
  );
}
