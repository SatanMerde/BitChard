import type { DiscordPresenceOptions } from '@bitchord/shared/bridge';
import { useSpotifySettings } from '@/data/spotify/canvas';
import {
  useDiscord,
  useDiscordConnect,
  useDiscordDisconnect,
  useDiscordEnabled,
  useDiscordOptions,
  type DiscordStatus,
} from '@/extension/useDiscord';
import {
  openSpotifySignIn,
  useRefreshSpotifyAuth,
  useSetSpotifyConnected,
  useSpotifyAuth,
  type SpotifyAuthStatus,
} from '@/extension/useSpotifyAuth';
import styles from './ExtensionPage.module.css';

/** The Spotify row's words and button (SpotifyLibraryScreen's states). */
export function SpotifyIntegration() {
  const auth = useSpotifyAuth();
  const connect = useSetSpotifyConnected();
  const retry = useRefreshSpotifyAuth();
  if (auth.state !== 'ready') return <span className={styles.value}>{spotifyText(auth)}</span>;
  const { browserSignedIn, connected, signedIn } = auth.auth;
  const action = !browserSignedIn
    ? { label: 'Sign In', run: openSpotifySignIn }
    : !connected
      ? { label: 'Connect', run: () => connect.mutate(true) }
      : !signedIn
        ? { label: 'Retry', run: () => retry.mutate() }
        : { label: 'Disconnect', run: () => connect.mutate(false) };
  return (
    <span className={styles.value}>
      <span title={auth.auth.problem ?? undefined}>{spotifyText(auth)}</span>
      <button className={styles.inlineButton} onClick={action.run} disabled={connect.isPending || retry.isPending}>
        {action.label}
      </button>
    </span>
  );
}

function spotifyText(auth: SpotifyAuthStatus): string {
  if (auth.state === 'checking') return 'Checking…';
  if (auth.state === 'unavailable') return 'Not available in this version yet';
  const { browserSignedIn, connected, signedIn, user } = auth.auth;
  if (!browserSignedIn) return 'Not signed in in this browser';
  if (!connected) return 'Disconnected';
  return signedIn ? `Signed in as ${user?.name ?? 'your account'}` : 'Couldn’t read your account';
}

export function DiscordIntegration() {
  const status = useDiscord();
  const connect = useDiscordConnect();
  const disconnect = useDiscordDisconnect();
  const text = discordText(status);
  if (status.state !== 'ready' || !status.discord.available) return <span className={styles.value}>{text}</span>;
  const connected = status.discord.connected;
  return (
    <span className={styles.value}>
      <span title={status.discord.problem ?? undefined}>{text}</span>
      <button
        className={styles.inlineButton}
        onClick={() => (connected ? disconnect.mutate() : connect.mutate())}
        disabled={connect.isPending || disconnect.isPending}
      >
        {connected ? 'Disconnect' : 'Connect'}
      </button>
    </span>
  );
}

function discordText(status: DiscordStatus): string {
  if (status.state === 'checking') return 'Checking…';
  if (status.state === 'unavailable') return 'Not in this build of the extension';
  const { available, connected, enabled, user, gateway, problem } = status.discord;
  if (!available) return 'Not in this build of the extension';
  if (!connected || !user) return 'Not connected';
  if (!enabled) return `${user.name} · presence off`;
  if (gateway === 'error') return problem ?? 'Couldn’t connect';
  if (gateway === 'ready') return `${user.name} · showing what you play`;
  if (gateway === 'connecting') return `${user.name} · connecting…`;
  return `${user.name} · on`;
}

export function Toggle({ label, detail, checked, onChange }: { label: string; detail?: string; checked: boolean; onChange: (on: boolean) => void }) {
  return (
    <label className={styles.row}>
      <span className={styles.rowText}>
        <span>{label}</span>
        {detail && <span className={styles.rowDetail}>{detail}</span>}
      </span>
      <input type="checkbox" className={styles.switch} checked={checked} onChange={(e) => onChange(e.currentTarget.checked)} />
    </label>
  );
}

/** Spotify Canvas behind the lyrics; only offered once signed in to Spotify. */
export function CanvasToggle() {
  const auth = useSpotifyAuth();
  const canvas = useSpotifySettings((s) => s.canvas);
  const setCanvas = useSpotifySettings((s) => s.setCanvas);
  if (auth.state !== 'ready' || !auth.auth.signedIn) return null;
  return (
    <Toggle
      label="Spotify Canvas"
      detail="Spotify’s looping clip behind the lyrics. Not on metered connections or with reduced motion."
      checked={canvas}
      onChange={setCanvas}
    />
  );
}

const ACTIVITY_TYPES: { value: DiscordPresenceOptions['activityType']; label: string }[] = [
  { value: 'listening', label: 'Listening to' },
  { value: 'playing', label: 'Playing' },
  { value: 'watching', label: 'Watching' },
  { value: 'competing', label: 'Competing in' },
];

const STATUSES: { value: DiscordPresenceOptions['status']; label: string }[] = [
  { value: 'online', label: 'Online' },
  { value: 'idle', label: 'Idle' },
  { value: 'dnd', label: 'Do Not Disturb' },
  { value: 'invisible', label: 'Invisible' },
];

/** Discord presence (prompt §7.3): opt-in, with the one-line warning, then how the card reads. */
export function DiscordSettings() {
  const status = useDiscord();
  const setEnabled = useDiscordEnabled();
  const options = useDiscordOptions((s) => s.options);
  const set = useDiscordOptions((s) => s.set);
  if (status.state !== 'ready' || !status.discord.available || !status.discord.connected) return null;
  const on = status.discord.enabled;
  return (
    <>
      <section className={styles.group}>
        <Toggle label="Show what you’re playing" checked={on} onChange={(v) => setEnabled.mutate(v)} />
        {on && (
          <>
            <label className={styles.row}>
              <span>Activity</span>
              <select
                className={styles.select}
                value={options.activityType}
                onChange={(e) => set({ activityType: e.currentTarget.value as DiscordPresenceOptions['activityType'] })}
              >
                {ACTIVITY_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.row}>
              <span>Name</span>
              <input
                className={styles.input}
                placeholder="BitChard"
                value={options.activityName}
                onChange={(e) => set({ activityName: e.currentTarget.value })}
              />
            </label>
            <label className={styles.row}>
              <span>Status</span>
              <select
                className={styles.select}
                value={options.status}
                onChange={(e) => set({ status: e.currentTarget.value as DiscordPresenceOptions['status'] })}
              >
                {STATUSES.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </label>
            <Toggle
              label="Lead with the song"
              detail="Member lists show the song title instead of the artist."
              checked={options.useDetails}
              onChange={(v) => set({ useDetails: v })}
            />
            <Toggle label="Listen button" checked={options.button1Visible} onChange={(v) => set({ button1Visible: v })} />
            {options.button1Visible && (
              <label className={styles.row}>
                <span>Listen button text</span>
                <input
                  className={styles.input}
                  placeholder="Listen on YouTube Music"
                  value={options.button1Text}
                  onChange={(e) => set({ button1Text: e.currentTarget.value })}
                />
              </label>
            )}
            <Toggle label="BitChard button" checked={options.button2Visible} onChange={(v) => set({ button2Visible: v })} />
            {options.button2Visible && (
              <label className={styles.row}>
                <span>BitChard button text</span>
                <input
                  className={styles.input}
                  placeholder="Visit BitChard"
                  value={options.button2Text}
                  onChange={(e) => set({ button2Text: e.currentTarget.value })}
                />
              </label>
            )}
          </>
        )}
      </section>
      <p className={styles.footer}>
        This uses your Discord account’s own token, which Discord’s terms don’t allow. Button text can use {'{song_name}'},{' '}
        {'{artist_name}'} and {'{album_name}'}.
      </p>
    </>
  );
}
