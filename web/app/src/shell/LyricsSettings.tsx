import { ArrowDown, ArrowUp, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { getBridge } from '@/extension/bridge';
import { useLyricsSettings } from '@/lyrics/settings';
import { sourceById } from '@/lyrics/sources';
import styles from './LyricsSettings.module.css';

/** Hosts the page can't reach itself: KuGou, Musixmatch, Genius and Apple's catalogue (for PaxSenix). */
const EXTENSION_ORIGINS = [
  'https://mobileservice.kugou.com',
  'https://lyrics.kugou.com',
  'https://apic.musixmatch.com',
  'https://www.musixmatch.com',
  'https://genius.com',
  'https://music.apple.com',
  'https://amp-api.music.apple.com',
  'https://api.paxsenix.org',
  'https://lyrics-api.boidu.dev',
  'https://unison.boidu.dev',
];

/**
 * Lyric providers, as the desktop's settings have them: switch each on or
 * off, put them in order, prefer syllable timing, and the PaxSenix key. Plus
 * the one thing only the web needs: letting the extension reach the hosts a
 * page can't.
 */
export function LyricsSettings({ onClose }: { onClose: () => void }) {
  const { order, disabled, preferSyllables, paxsenixKey, setEnabled, move, setPreferSyllables, setPaxsenixKey, reset } =
    useLyricsSettings();
  const [granted, setGranted] = useState<string[] | null>(null);
  const [key, setKey] = useState(paxsenixKey);

  useEffect(() => {
    const check = () =>
      void getBridge()
        ?.call('lyrics.permissions', { origins: EXTENSION_ORIGINS })
        .then((r) => setGranted(r.granted))
        .catch(() => setGranted(null));
    check();
    window.addEventListener('focus', check);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('focus', check);
      window.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  const missing = granted ? EXTENSION_ORIGINS.filter((o) => !granted.includes(o)) : [];

  return createPortal(
    <div className={styles.scrim} onPointerDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-label="Lyric settings">
        <header className={styles.header}>
          <h2>Lyrics</h2>
          <button className={styles.close} aria-label="Close" onClick={onClose}>
            <X size={16} />
          </button>
        </header>

        <div className={styles.body}>
          <h3 className={styles.section}>Providers</h3>
          <p className={styles.note}>Asked all at once; the first in this order with word-timed lyrics wins.</p>
          <ol className={styles.group}>
            {order.map((id, index) => {
              const source = sourceById(id)!;
              const on = !disabled.includes(id);
              return (
                <li key={id} className={styles.row}>
                  <label className={styles.toggle}>
                    <input type="checkbox" checked={on} onChange={(e) => setEnabled(id, e.currentTarget.checked)} />
                    <span className={styles.text}>
                      <span className={styles.name}>{source.label}</span>
                      <span className={styles.detail}>{source.detail}</span>
                    </span>
                  </label>
                  <span className={styles.moves}>
                    <button aria-label={`Move ${source.label} up`} disabled={index === 0} onClick={() => move(id, -1)}>
                      <ArrowUp size={14} />
                    </button>
                    <button aria-label={`Move ${source.label} down`} disabled={index === order.length - 1} onClick={() => move(id, 1)}>
                      <ArrowDown size={14} />
                    </button>
                  </span>
                </li>
              );
            })}
          </ol>

          <label className={`${styles.group} ${styles.row} ${styles.toggle}`}>
            <input type="checkbox" checked={preferSyllables} onChange={(e) => setPreferSyllables(e.currentTarget.checked)} />
            <span className={styles.text}>
              <span className={styles.name}>Prefer syllable timing</span>
              <span className={styles.detail}>Keep looking past a line-timed answer for a word-timed one.</span>
            </span>
          </label>

          <h3 className={styles.section}>Extension access</h3>
          {granted === null ? (
            <p className={styles.note}>KuGou, Musixmatch, Genius and PaxSenix need the BitChard extension.</p>
          ) : missing.length ? (
            <div className={styles.access}>
              <p className={styles.note}>
                KuGou, Musixmatch, Genius and PaxSenix don’t let web pages ask them directly. Allow the extension to,
                once.
              </p>
              <button className={styles.button} onClick={() => void getBridge()?.call('lyrics.requestPermissions', { origins: missing })}>
                Allow
              </button>
            </div>
          ) : (
            <p className={styles.note}>All providers are reachable.</p>
          )}

          <h3 className={styles.section}>PaxSenix key</h3>
          <input
            className={styles.input}
            type="password"
            placeholder="For PaxSenix: Spotify and Musixmatch"
            value={key}
            onChange={(e) => setKey(e.currentTarget.value)}
            onBlur={() => setPaxsenixKey(key)}
            autoComplete="off"
          />

          <button className={styles.reset} onClick={reset}>
            Restore Defaults
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
