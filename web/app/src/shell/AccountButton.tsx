import { ArrowUpRight, ChevronsUpDown } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { useRefreshYtAuth, useSelectChannel, useSetConnected, useYtAuth } from '@/extension/useYtAuth';
import styles from './AccountButton.module.css';

const SIGN_IN_URL = 'https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/';

function Avatar({ src, name, size }: { src: string | null; name: string; size: number }) {
  return src ? (
    <img className={styles.avatar} src={src} alt="" width={size} height={size} referrerPolicy="no-referrer" />
  ) : (
    <span className={styles.avatar} style={{ width: size, height: size }} aria-hidden="true">
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}

/**
 * The sidebar's account control (report §1: avatar and name at the bottom,
 * opening the account menu; a red "Sign In" pill when signed out).
 *
 * There is no sign-in form (prompt §5.3): BitChard uses the YouTube session
 * this browser already has, through the extension. "Sign In" opens Google's
 * own sign-in; the extension notices the new session and this updates itself.
 */
export function AccountButton() {
  const status = useYtAuth();
  const select = useSelectChannel();
  const connect = useSetConnected();
  const refresh = useRefreshYtAuth();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);

  if (status.state !== 'ready') return null;
  const { auth } = status;

  if (!auth.browserSignedIn) {
    return (
      <a className={styles.pill} href={SIGN_IN_URL} target="_blank" rel="noreferrer">
        Sign In
      </a>
    );
  }
  if (!auth.connected) {
    return (
      <button className={styles.pill} onClick={() => connect.mutate(true)} disabled={connect.isPending}>
        Connect YouTube Music
      </button>
    );
  }
  if (!auth.active) {
    // Signed in to the browser, but the account couldn't be read (the reason is on the Extension page).
    return (
      <button
        className={styles.pill}
        title={auth.problem ?? undefined}
        onClick={() => refresh.mutate()}
        disabled={refresh.isPending}
      >
        {refresh.isPending ? 'Reading account…' : 'Retry YouTube Sign-In'}
      </button>
    );
  }
  const active = auth.active;
  const multiple = auth.accounts.flatMap((a) => a.channels).length > 1;

  return (
    <>
      <button
        className={styles.account}
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        onClick={(event) => setAnchor(anchor ? null : event.currentTarget)}
      >
        <Avatar src={active.avatarUrl} name={active.name} size={24} />
        <span className={styles.name}>{active.name}</span>
        {multiple && <ChevronsUpDown size={14} strokeWidth={2} className={styles.chevron} />}
      </button>
      {anchor && (
        <Menu anchor={anchor} placement="above-start" onClose={close} label="Account">
          {auth.accounts.map((account) => (
            <div key={account.authUser} role="group" aria-label={account.email ?? account.name}>
              <MenuHeader>{account.email ?? account.name}</MenuHeader>
              {account.channels.map((channel) => (
                <MenuItem
                  key={channel.id}
                  checked={channel.id === active.id}
                  icon={<Avatar src={channel.avatarUrl} name={channel.name} size={20} />}
                  trailing={channel.brand ? 'Brand' : undefined}
                  onSelect={() => {
                    close();
                    if (channel.id !== active.id) select.mutate(channel.id);
                  }}
                >
                  {channel.name}
                </MenuItem>
              ))}
            </div>
          ))}
          <MenuSeparator />
          <MenuItem
            onSelect={() => {
              close();
              window.open(SIGN_IN_URL.replace('ServiceLogin', 'AddSession'), '_blank', 'noreferrer');
            }}
            trailing={<ArrowUpRight size={13} />}
          >
            Add Account
          </MenuItem>
          <MenuItem
            destructive
            onSelect={() => {
              close();
              connect.mutate(false);
            }}
          >
            Disconnect from BitChard
          </MenuItem>
        </Menu>
      )}
    </>
  );
}
