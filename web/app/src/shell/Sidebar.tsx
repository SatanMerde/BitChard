import { ArrowUpRight, ListMusic, Settings } from 'lucide-react';
import { MdOutlineExtension } from 'react-icons/md';
import { PrefetchLink, PrefetchNavLink } from '@/components/PrefetchLink';
import { LogoMark } from '@/components/icons/LogoMark';
import {
  AlbumsIcon,
  AllPlaylistsIcon,
  ArtistsIcon,
  HomeIcon,
  MadeForYouIcon,
  NewIcon,
  RadioIcon,
  RecentlyAddedIcon,
  SearchIcon,
  SongsIcon,
  type NavIconProps,
} from '@/components/icons/NavIcons';
import { hrefFor } from '@/components/music/links';
import { config } from '@/config';
import { LIBRARY_SECTIONS, type LibrarySection } from '@/data/music/library';
import { useLibraryPlaylists, useSignedIn } from '@/data/music/queries';
import { useSpotifyPlaylists } from '@/data/spotify/queries';
import { useExtensionUpdate } from '@/extension/useExtensionUpdate';
import { useSpotifySignedIn } from '@/extension/useSpotifyAuth';
import { useUi } from '@/state/ui';
import { AccountButton } from './AccountButton';
import styles from './Sidebar.module.css';

interface NavItem {
  to: string;
  label: string;
  Icon: (props: NavIconProps) => React.ReactNode;
}

const PRIMARY: NavItem[] = [
  { to: '/search', label: 'Search', Icon: SearchIcon },
  { to: '/home', label: 'Home', Icon: HomeIcon },
  { to: '/new', label: 'New', Icon: NewIcon },
  { to: '/radio', label: 'Radio', Icon: RadioIcon },
];

const LIBRARY_ICONS: Record<Exclude<LibrarySection, 'playlists'>, NavItem['Icon']> = {
  recent: RecentlyAddedIcon,
  artists: ArtistsIcon,
  albums: AlbumsIcon,
  songs: SongsIcon,
  'made-for-you': MadeForYouIcon,
};

const LIBRARY: NavItem[] = (Object.keys(LIBRARY_ICONS) as (keyof typeof LIBRARY_ICONS)[]).map((section) => ({
  to: `/library/${section}`,
  label: LIBRARY_SECTIONS[section].title,
  Icon: LIBRARY_ICONS[section],
}));

function NavList({ items, onNavigate }: { items: NavItem[]; onNavigate: () => void }) {
  return (
    <ul className={styles.list}>
      {items.map(({ to, label, Icon }) => (
        <li key={to}>
          <PrefetchNavLink to={to} className={styles.item} onClick={onNavigate}>
            <Icon size={24} className={styles.icon} />
            <span className={styles.label}>{label}</span>
          </PrefetchNavLink>
        </li>
      ))}
    </ul>
  );
}

/** Library and Playlists (report §1), once YouTube requests are made as the user. */
function LibraryNav({ onNavigate }: { onNavigate: () => void }) {
  const playlists = useLibraryPlaylists();
  return (
    <>
      <h2 className={styles.sectionHeader}>Library</h2>
      <NavList items={LIBRARY} onNavigate={onNavigate} />
      <h2 className={styles.sectionHeader}>Playlists</h2>
      <ul className={styles.list}>
        <li>
          <PrefetchNavLink to="/library/playlists" className={styles.item} onClick={onNavigate}>
            <AllPlaylistsIcon size={24} className={styles.icon} />
            <span className={styles.label}>{LIBRARY_SECTIONS.playlists.title}</span>
          </PrefetchNavLink>
        </li>
        {playlists.data?.map((playlist) => (
          <li key={playlist.id}>
            <PrefetchNavLink to={hrefFor(playlist) ?? '/library/playlists'} className={styles.item} onClick={onNavigate}>
              <span className={styles.icon}>
                <ListMusic size={17} strokeWidth={1.8} />
              </span>
              <span className={styles.label}>{playlist.title}</span>
            </PrefetchNavLink>
          </li>
        ))}
      </ul>
    </>
  );
}

/** Spotify's playlists, once the extension can read them as the user (prompt §7.1). */
function SpotifyNav({ onNavigate }: { onNavigate: () => void }) {
  const playlists = useSpotifyPlaylists();
  return (
    <>
      <h2 className={styles.sectionHeader}>Spotify</h2>
      <ul className={styles.list}>
        <li>
          <PrefetchNavLink to="/spotify" end className={styles.item} onClick={onNavigate}>
            <AllPlaylistsIcon size={24} className={styles.icon} />
            <span className={styles.label}>All Spotify Playlists</span>
          </PrefetchNavLink>
        </li>
        {playlists.data?.map((playlist) => (
          <li key={playlist.id}>
            <PrefetchNavLink to={`/spotify/playlist/${encodeURIComponent(playlist.id)}`} className={styles.item} onClick={onNavigate}>
              <span className={styles.icon}>
                <ListMusic size={17} strokeWidth={1.8} />
              </span>
              <span className={styles.label}>{playlist.name}</span>
            </PrefetchNavLink>
          </li>
        ))}
      </ul>
    </>
  );
}

/** The left glass sidebar (report §1). */
export function Sidebar() {
  const drawerOpen = useUi((s) => s.drawerOpen);
  const setDrawerOpen = useUi((s) => s.setDrawerOpen);
  const update = useExtensionUpdate();
  const signedIn = useSignedIn();
  const spotifySignedIn = useSpotifySignedIn();
  const close = () => setDrawerOpen(false);

  return (
    <>
      <nav className={styles.nav} data-open={drawerOpen} aria-label="Main">
        <div className={styles.header}>
          <PrefetchLink to="/home" className={styles.logo} aria-label="BitChard, Home" onClick={close}>
            <LogoMark size={22} className={styles.mark} />
            <span className={styles.wordmark}>BitChard</span>
          </PrefetchLink>
        </div>
        <div className={styles.scroll}>
          <NavList items={PRIMARY} onNavigate={close} />
          {signedIn && <LibraryNav onNavigate={close} />}
          {spotifySignedIn && <SpotifyNav onNavigate={close} />}
        </div>
        <div className={styles.footer}>
          <PrefetchNavLink to="/extension" className={styles.item} onClick={close}>
            {/* Chrome's own toolbar glyph for extensions (Material "extension"). */}
            <span className={styles.icon}>
              <MdOutlineExtension size={18} />
            </span>
            <span className={styles.label}>BitChard Extension</span>
            {update.state === 'available' && (
              <span className={styles.badge} title={`Version ${update.latest.version} is available`}>
                Update
              </span>
            )}
          </PrefetchNavLink>
          <AccountButton />
          <div className={styles.footerRow}>
            <a className={styles.footerLink} href={config.androidAppUrl} target="_blank" rel="noreferrer">
              <span>Get the Android app</span>
              <ArrowUpRight size={14} strokeWidth={2} />
            </a>
            <PrefetchNavLink to="/settings" className={styles.settings} aria-label="Settings" title="Settings" onClick={close}>
              <Settings size={17} strokeWidth={1.9} />
            </PrefetchNavLink>
          </div>
        </div>
      </nav>
      {drawerOpen && <div className={styles.scrim} onClick={close} aria-hidden="true" />}
    </>
  );
}
