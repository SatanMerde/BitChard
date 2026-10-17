import { useMutation, useQuery } from '@tanstack/react-query';
import { Check, Copy, Crown, LogOut, UserMinus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { PageHeader } from '@/components/PageHeader';
import { useSignedIn } from '@/data/music/queries';
import { cleanCode, inviteUrl } from '@/party/invite';
import {
  controlsLocked,
  createParty,
  DEFAULT_SERVER,
  joinParty,
  leaveParty,
  messageOf,
  party,
  partyServer,
  partySettings,
  previewParty,
  savePartySettings,
  useParty,
} from '@/party/listenTogether';
import { usePlayer } from '@/state/player';
import { Toggle } from './IntegrationSettings';
import styles from './ExtensionPage.module.css';
import own from './ListenTogetherPage.module.css';

/**
 * Listen Together (ListenTogetherScreen on the phone): start a party or join
 * one by code, then the party itself — its code and invite link, who's in it,
 * and the host's controls. Opened from Settings, and from an invite link
 * (`/invite/:code`), where joining waits for a tap: a browser only lets audio
 * start after the listener has interacted with the page.
 */
export default function ListenTogetherPage() {
  const params = useParams();
  const [search] = useSearchParams();
  const inviteCode = params.code ? cleanCode(params.code) : null;
  const inviteServer = search.get('server');
  const state = useParty();
  const signedIn = useSignedIn();

  return (
    <div className={styles.centered}>
      <PageHeader title="Listen Together" />
      <div className={styles.body}>
        {state.code ? (
          <InParty />
        ) : inviteCode ? (
          <InviteCard code={inviteCode} server={inviteServer} signedIn={signedIn} />
        ) : (
          <StartOrJoin signedIn={signedIn} />
        )}
        {state.error && <p className={own.error}>{state.error}</p>}
      </div>
    </div>
  );
}

function SignInFirst() {
  return (
    <p className={`${styles.footer} ${own.notice}`}>
      Listen Together uses your YouTube Music account for your name and picture in the party. Sign in on music.youtube.com,
      then come back.
    </p>
  );
}

function StartOrJoin({ signedIn }: { signedIn: boolean }) {
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [settings, setSettings] = useState(partySettings);
  const autoplay = usePlayer((s) => s.autoplay);
  const create = useMutation({ mutationFn: () => createParty({ autoplayEnabled: autoplay }) });
  const update = (change: Partial<typeof settings>) => {
    savePartySettings(change);
    setSettings(partySettings());
  };

  return (
    <>
      <p className={own.lede}>
        Listen to the same music at the same moment with up to five devices. Anyone in the party can play, pause and skip.
      </p>
      {!signedIn && <SignInFirst />}
      <section className={styles.group}>
        <div className={styles.row}>
          <span className={styles.rowText}>
            <span>Start a Party</span>
            <span className={styles.rowDetail}>Whatever you’re playing comes with you.</span>
          </span>
          <button className={own.primary} disabled={!signedIn || create.isPending} onClick={() => create.mutate()}>
            {create.isPending ? 'Starting…' : 'Start'}
          </button>
        </div>
        <form
          className={styles.row}
          onSubmit={(e) => {
            e.preventDefault();
            const cleaned = cleanCode(code);
            if (cleaned) navigate(`/invite/${cleaned}`);
          }}
        >
          <span>Join with a Code</span>
          <span className={own.join}>
            <input
              className={`${styles.input} ${own.code}`}
              value={code}
              maxLength={7}
              placeholder="ABC123"
              aria-label="Party code"
              onChange={(e) => setCode(e.currentTarget.value.toUpperCase())}
            />
            <button className={own.primary} type="submit" disabled={!cleanCode(code)}>
              Join
            </button>
          </span>
        </form>
      </section>

      <h2 className={styles.sectionHeader}>You</h2>
      <section className={styles.group}>
        <label className={styles.row}>
          <span>Name in parties</span>
          <input
            className={styles.input}
            placeholder="Your YouTube name"
            value={settings.nickname}
            maxLength={80}
            onChange={(e) => update({ nickname: e.currentTarget.value })}
          />
        </label>
        <label className={styles.row}>
          <span>Party server</span>
          <input
            className={styles.input}
            placeholder={new URL(DEFAULT_SERVER).host}
            value={settings.customServer}
            onChange={(e) => update({ customServer: e.currentTarget.value })}
          />
        </label>
      </section>
      <p className={styles.footer}>Leave the server empty to use BitChard’s. Anyone joining your party uses the server it’s on.</p>
    </>
  );
}

/** An invite: who's in it, then a tap to join (the tap also lets the browser start audio). */
function InviteCard({ code, server, signedIn }: { code: string; server: string | null; signedIn: boolean }) {
  const preview = useQuery({ queryKey: ['party', 'preview', code, server], queryFn: () => previewParty(code, server), retry: false });
  const join = useMutation({ mutationFn: () => joinParty(code, server) });
  const data = preview.data;
  return (
    <div className={own.invite}>
      <p className={own.inviteCode}>{code}</p>
      {preview.isPending && <p className={styles.footer}>Looking up the party…</p>}
      {preview.isError && <p className={own.error}>{messageOf(preview.error)}</p>}
      {data && (
        <>
          <p className={own.lede}>
            {data.hostName ? `${data.hostName}’s party` : 'A Listen Together party'}
            {data.memberCount ? ` · ${data.memberCount} of ${data.maxMembers} listening` : ''}
          </p>
          <div className={own.faces}>
            {data.members.map((m, i) => (
              <Avatar key={i} name={m.displayName} url={m.avatarUrl} />
            ))}
          </div>
        </>
      )}
      {!signedIn && <SignInFirst />}
      <button
        className={own.tapToJoin}
        disabled={!signedIn || join.isPending || data?.isFull || preview.isError}
        onClick={() => join.mutate()}
      >
        {data?.isFull ? 'This party is full' : join.isPending ? 'Joining…' : 'Tap to Join'}
      </button>
    </div>
  );
}

function Avatar({ name, url }: { name: string; url: string | null }) {
  return url ? (
    <img className={own.avatar} src={url} alt="" referrerPolicy="no-referrer" />
  ) : (
    <span className={own.avatar}>{name.slice(0, 1).toUpperCase()}</span>
  );
}

function InParty() {
  const state = useParty();
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);
  const isHost = state.you?.isHost === true;
  const locked = controlsLocked(state);
  const custom = partyServer() && partyServer() !== DEFAULT_SERVER ? partyServer() : null;
  const link = inviteUrl(state.code!, custom);
  useEffect(() => {
    if (!copied) return;
    const t = setTimeout(() => setCopied(false), 1500);
    return () => clearTimeout(t);
  }, [copied]);

  const status =
    state.connection === 'live'
      ? state.clockSynced
        ? `In sync · ${state.roundTripMs} ms round trip`
        : 'Measuring the clock…'
      : state.connection === 'connecting'
        ? 'Reconnecting…'
        : 'Offline';

  return (
    <>
      <div className={own.invite}>
        <p className={own.inviteCode}>{state.code}</p>
        <p className={styles.footer}>{status}</p>
        <button
          className={own.copy}
          onClick={() => void navigator.clipboard.writeText(link).then(() => setCopied(true))}
          title={link}
        >
          {copied ? <Check size={15} /> : <Copy size={15} />}
          {copied ? 'Copied' : 'Copy Invite Link'}
        </button>
      </div>

      <h2 className={styles.sectionHeader}>
        Listening · {state.members.length} of {state.maxMembers}
      </h2>
      <section className={styles.group}>
        {state.members.map((m) => (
          <div key={m.memberId} className={styles.integration}>
            <Avatar name={m.displayName} url={m.avatarUrl} />
            <span className={styles.integrationName}>
              {m.displayName}
              {m.memberId === state.you?.memberId && <span className={own.you}> (you)</span>}
            </span>
            <span className={styles.value}>
              {m.isHost && <Crown size={14} aria-label="Host" />}
              <span className={own.dot} data-on={m.connected || undefined} title={m.connected ? 'Connected' : 'Reconnecting'} />
              {isHost && m.memberId !== state.you?.memberId && (
                <button className={styles.inlineButton} onClick={() => party.kick(m.memberId)} title="Remove from the party">
                  <UserMinus size={13} />
                </button>
              )}
            </span>
          </div>
        ))}
      </section>

      <h2 className={styles.sectionHeader}>Party</h2>
      <section className={styles.group}>
        <Toggle
          label="AutoPlay"
          detail="Keep the party going with similar music when the queue runs out."
          checked={state.playback.autoplayEnabled}
          onChange={(on) => !locked && party.setAutoplay(on)}
        />
        {isHost && (
          <Toggle
            label="Only I control the music"
            detail="Others can listen but not play, pause, skip or change the queue."
            checked={state.hostOnlyControl}
            onChange={party.setHostOnlyControl}
          />
        )}
      </section>
      {locked && <p className={styles.footer}>The host controls the music in this party. You can still pause your own device.</p>}

      {state.activity.length > 0 && (
        <>
          <h2 className={styles.sectionHeader}>Activity</h2>
          <section className={styles.group}>
            {state.activity.slice(0, 8).map((a, i) => (
              <div key={i} className={styles.row}>
                <span className={styles.rowText}>
                  <span>
                    {a.by} · {a.action}
                  </span>
                  {a.detail && <span className={styles.rowDetail}>{a.detail}</span>}
                </span>
                <span className={styles.rowDetail}>{new Date(a.atMs).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</span>
              </div>
            ))}
          </section>
        </>
      )}

      <button className={own.leave} onClick={() => void leaveParty().then(() => navigate('/settings/listen-together'))}>
        <LogOut size={15} />
        Leave Party
      </button>
    </>
  );
}
