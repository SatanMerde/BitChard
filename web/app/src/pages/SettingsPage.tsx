import { ChevronRight, Users } from 'lucide-react';
import { lazy, Suspense, useState, type ComponentType, type ReactNode } from 'react';
import { MdOutlineExtension } from 'react-icons/md';
import { SiDiscord, SiSpotify, SiYoutubemusic } from 'react-icons/si';
import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import { useDiscord } from '@/extension/useDiscord';
import { useExtension } from '@/extension/useExtension';
import { useYtAuth } from '@/extension/useYtAuth';
import { useParty } from '@/party/useParty';
import { CROSSFADE_CHOICES, usePlayer } from '@/state/player';
import { CanvasToggle, DiscordIntegration, SpotifyIntegration, Toggle } from './IntegrationSettings';
import styles from './ExtensionPage.module.css';

const LyricsSettings = lazy(() => import('@/shell/LyricsSettings').then((m) => ({ default: m.LyricsSettings })));

/** An integration's coloured glyph, as on the Extension page. */
function Glyph({ Icon, color, ink }: { Icon: ComponentType<{ size?: number }>; color: string; ink?: string }) {
  return (
    <span className={styles.integrationIcon} style={{ backgroundColor: color, ...(ink && { color: ink }) }}>
      <Icon size={15} />
    </span>
  );
}

/** A row that opens a page of its own. */
function LinkRow({ to, icon, title, value }: { to: string; icon?: ReactNode; title: string; value?: string | null }) {
  return (
    <PrefetchLink to={to} className={`${styles.integration} ${styles.linkRow}`}>
      {icon}
      <span className={styles.integrationName}>{title}</span>
      <span className={styles.value}>
        {value}
        <ChevronRight size={16} />
      </span>
    </PrefetchLink>
  );
}

function youtubeText(auth: ReturnType<typeof useYtAuth>): string {
  if (auth.state === 'checking') return 'Checking…';
  if (auth.state === 'unavailable') return 'Needs the extension';
  const { browserSignedIn, connected, active } = auth.auth;
  if (!browserSignedIn) return 'Not signed in';
  if (!connected) return 'Disconnected';
  return active ? active.name : 'Signed in';
}

/**
 * Settings (SettingsSheet on the phone): the account and its integrations,
 * Listen Together, playback and lyrics, in the same grouped rows.
 */
export default function SettingsPage() {
  const extension = useExtension();
  const yt = useYtAuth();
  const discord = useDiscord();
  const party = useParty();
  const autoplay = usePlayer((s) => s.autoplay);
  const crossfadeSec = usePlayer((s) => s.crossfadeSec);
  const { setAutoplay, setCrossfade } = usePlayer.getState();
  const [lyrics, setLyrics] = useState(false);
  const present = extension.state === 'present';
  const discordText =
    discord.state === 'ready' && discord.discord.connected
      ? discord.discord.enabled
        ? 'On'
        : 'Off'
      : discord.state === 'ready' && discord.discord.available
        ? 'Not connected'
        : null;

  return (
    <div className={styles.centered}>
      <PageHeader title="Settings" />
      <div className={styles.body}>
        <h2 className={styles.sectionHeader}>Account & Integrations</h2>
        <section className={styles.group}>
          <div className={styles.integration}>
            <Glyph Icon={SiYoutubemusic} color="#ffffff" ink="#ff0033" />
            <span className={styles.integrationName}>YouTube Music</span>
            <span className={styles.value}>{youtubeText(yt)}</span>
          </div>
          <div className={styles.integration}>
            <Glyph Icon={SiSpotify} color="#1ed760" />
            <span className={styles.integrationName}>Spotify</span>
            {present ? <SpotifyIntegration /> : <span className={styles.value}>Needs the extension</span>}
          </div>
          {present && discord.state === 'ready' && discord.discord.available && (
            <LinkRow to="/settings/discord" icon={<Glyph Icon={SiDiscord} color="#5865f2" />} title="Discord" value={discordText} />
          )}
          <LinkRow
            to="/extension"
            icon={<Glyph Icon={MdOutlineExtension} color="#636366" />}
            title="BitChard Extension"
            value={present ? `v${extension.extension.info.version}` : 'Not installed'}
          />
        </section>
        <section className={`${styles.group} ${styles.spaced}`}>
          <CanvasToggle />
        </section>

        <h2 className={styles.sectionHeader}>Listen Together</h2>
        <section className={styles.group}>
          <LinkRow
            to="/settings/listen-together"
            icon={<Glyph Icon={Users} color="#fa2d48" />}
            title="Listen Together"
            value={party.code ? `In party ${party.code}` : 'Listen in sync with friends'}
          />
        </section>

        <h2 className={styles.sectionHeader}>Playback</h2>
        <section className={styles.group}>
          <Toggle
            label="AutoPlay"
            detail="When the queue ends, keep playing similar music."
            checked={autoplay}
            onChange={setAutoplay}
          />
          <label className={styles.row}>
            <span>Crossfade</span>
            <select className={styles.select} value={crossfadeSec} onChange={(e) => setCrossfade(Number(e.currentTarget.value))}>
              {CROSSFADE_CHOICES.map((seconds) => (
                <option key={seconds} value={seconds}>
                  {seconds === 0 ? 'Off' : `${seconds} seconds`}
                </option>
              ))}
            </select>
          </label>
        </section>

        <h2 className={styles.sectionHeader}>Lyrics</h2>
        <section className={styles.group}>
          <button className={`${styles.integration} ${styles.linkRow}`} onClick={() => setLyrics(true)}>
            <span className={styles.integrationName}>Lyric Sources</span>
            <span className={styles.value}>
              Order, sources, syllable timing
              <ChevronRight size={16} />
            </span>
          </button>
        </section>
      </div>
      {lyrics && (
        <Suspense fallback={null}>
          <LyricsSettings onClose={() => setLyrics(false)} />
        </Suspense>
      )}
    </div>
  );
}
