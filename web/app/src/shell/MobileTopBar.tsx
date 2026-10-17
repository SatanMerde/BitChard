import { Menu } from 'lucide-react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { LogoMark } from '@/components/icons/LogoMark';
import { useUi } from '@/state/ui';
import styles from './MobileTopBar.module.css';

/** Below 484px the sidebar folds into this bar and a drawer (report §11). */
export function MobileTopBar() {
  const setDrawerOpen = useUi((s) => s.setDrawerOpen);
  return (
    <header className={styles.bar}>
      <button className={styles.menu} aria-label="Open menu" onClick={() => setDrawerOpen(true)}>
        <Menu size={22} strokeWidth={2} />
      </button>
      <PrefetchLink to="/home" className={styles.logo} aria-label="BitChard, Home">
        <LogoMark size={20} className={styles.mark} />
        <span>BitChard</span>
      </PrefetchLink>
      <span className={styles.spacer} />
    </header>
  );
}
