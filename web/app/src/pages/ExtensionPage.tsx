import { ChevronRight } from 'lucide-react';
import { useState, type ComponentType } from 'react';
import { FaChrome, FaFirefoxBrowser } from 'react-icons/fa';
import { SiDiscord, SiSpotify, SiYoutubemusic } from 'react-icons/si';
import type { Capability } from '@bitchord/shared/bridge';
import { PageHeader } from '@/components/PageHeader';
import { config } from '@/config';
import { useExtension, type ExtensionStatus } from '@/extension/useExtension';
import { useExtensionUpdate } from '@/extension/useExtensionUpdate';
import { useYtAuth, type YtAuthStatus } from '@/extension/useYtAuth';
import styles from './ExtensionPage.module.css';
import { PrefetchLink } from '@/components/PrefetchLink';
import { DiscordIntegration, SpotifyIntegration } from './IntegrationSettings';

type Browser = 'chrome' | 'firefox';

interface Download {
  browser: Browser;
  title: string;
  detail: string;
  href: string;
  Icon: ComponentType<{ size?: number; className?: string }>;
  steps: string[];
  updateSteps: string[];
}

const DOWNLOADS: Download[] = [
  {
    browser: 'chrome',
    title: 'Download for Chrome',
    detail: 'Chrome, Edge, Brave and other Chromium browsers',
    href: config.extensionChromeUrl,
    Icon: FaChrome,
    steps: [
      'Extract the zip somewhere you’ll keep it. It holds one folder, bitchord-extension-chrome.',
      'Open chrome://extensions and turn on Developer mode.',
      'Choose Load unpacked and select that folder.',
      'Come back to this page: the status above turns to Installed.',
    ],
    updateSteps: [
      'Extract the new zip over the old one, replacing the bitchord-extension-chrome folder.',
      'In chrome://extensions, press the reload arrow on BitChard’s card.',
      'Come back to this page: the version above updates on its own.',
    ],
  },
  {
    browser: 'firefox',
    title: 'Download for Firefox',
    detail: 'Firefox 128 or newer',
    href: config.extensionFirefoxUrl,
    Icon: FaFirefoxBrowser,
    steps: [
      'Extract the zip. It holds one folder, bitchord-extension-firefox.',
      'Open about:debugging#/runtime/this-firefox and choose Load Temporary Add-on.',
      'Select manifest.json inside that folder.',
      'Firefox removes temporary add-ons when it quits; a signed version is coming.',
    ],
    updateSteps: [
      'Extract the new zip over the old one, replacing the bitchord-extension-firefox folder.',
      'In about:debugging, press Reload on BitChard’s entry.',
      'Come back to this page: the version above updates on its own.',
    ],
  },
];

/** The browser this page is open in, to put its download first. */
const currentBrowser: Browser = /firefox/i.test(navigator.userAgent) ? 'firefox' : 'chrome';

interface Integration {
  name: string;
  Icon: ComponentType<{ size?: number; className?: string }>;
  color: string;
  capability: Capability;
  /** What the row says when the extension serves this integration. */
  ready: string;
}

const INTEGRATIONS: Integration[] = [
  { name: 'YouTube Music', Icon: SiYoutubemusic, color: '#ff0033', capability: 'yt', ready: 'Playback ready · not signed in' },
  { name: 'Spotify', Icon: SiSpotify, color: '#1ed760', capability: 'spotify', ready: 'Not connected' },
  { name: 'Discord', Icon: SiDiscord, color: '#5865f2', capability: 'discord', ready: 'Off' },
];

/** YouTube Music's row: who requests act as, once the extension reports it. */
function youtubeStatus(auth: YtAuthStatus): string | null {
  if (auth.state !== 'ready') return null;
  const { browserSignedIn, connected, active } = auth.auth;
  if (!browserSignedIn) return 'Playback ready · not signed in';
  if (!connected) return 'Disconnected';
  return active ? `Signed in as ${active.name}` : 'Signed in · no account could be read';
}

/** One integration's state, honest about what this extension version can do. */
function integrationStatus(status: ExtensionStatus, integration: Integration, auth: YtAuthStatus): string {
  if (status.state === 'checking') return 'Checking…';
  if (status.state === 'missing') return 'Needs the extension';
  if (!status.extension.info.capabilities.includes(integration.capability)) return 'Not available in this version yet';
  return (integration.capability === 'yt' && youtubeStatus(auth)) || integration.ready;
}

export default function ExtensionPage() {
  const status = useExtension();
  const update = useExtensionUpdate();
  const auth = useYtAuth();
  const [chosen, setChosen] = useState<Browser | null>(null);
  const [updating, setUpdating] = useState(false);
  const downloads = [...DOWNLOADS].sort((a, b) => Number(b.browser === currentBrowser) - Number(a.browser === currentBrowser));
  const installedBrowser: Browser | null =
    status.state === 'present' ? (status.extension.info.browser === 'firefox' ? 'firefox' : 'chrome') : null;
  const forUpdate = DOWNLOADS.find((d) => d.browser === (installedBrowser ?? currentBrowser))!;
  const steps = DOWNLOADS.find((d) => d.browser === chosen)?.steps;

  return (
    <div className={styles.centered}>
      <PageHeader title="BitChard Extension" />
      <div className={styles.body}>
        <section className={styles.group} aria-live="polite">
          <div className={styles.row}>
            <span>Status</span>
            <span className={styles.value}>
              {status.state === 'checking' && 'Checking…'}
              {status.state === 'missing' && 'Not installed'}
              {status.state === 'present' && (
                <>
                  <span className={styles.dot} aria-hidden="true" />
                  Installed · v{status.extension.info.version}
                </>
              )}
            </span>
          </div>
          {update.state !== 'unknown' && (
            <div className={styles.row}>
              <span>Latest</span>
              <span className={styles.value}>
                v{update.latest.version}
                {update.state === 'current' && ' · Up to date'}
              </span>
            </div>
          )}
          {status.state === 'present' && (
            <div className={styles.row}>
              <span>Connection</span>
              <span className={styles.value}>
                {status.extension.bridge.transport === 'external' ? 'Direct' : 'Page relay'} ·{' '}
                {status.extension.info.browser === 'firefox' ? 'Firefox' : 'Chromium'}
              </span>
            </div>
          )}
        </section>
        {update.state === 'available' && (
          <>
            <a
              className={`${styles.group} ${styles.download} ${styles.update}`}
              href={forUpdate.href}
              download
              onClick={() => setUpdating(true)}
            >
              <span className={styles.updateDot} aria-hidden="true" />
              <span className={styles.downloadText}>
                <span className={styles.downloadTitle}>Update to v{update.latest.version}</span>
                <span className={styles.downloadDetail}>
                  You have v{update.installed}. Download the new {forUpdate.browser === 'firefox' ? 'Firefox' : 'Chrome'}{' '}
                  build, then reload the extension.
                </span>
              </span>
              <ChevronRight size={16} strokeWidth={2.4} className={styles.chevron} />
            </a>
            {updating && (
              <ol className={styles.steps}>
                {forUpdate.updateSteps.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
            )}
          </>
        )}
        <p className={styles.footer}>
          The extension does the networking and sign-in work a web page isn’t allowed to do. Addons and most lyric
          providers work without it.
        </p>

        <h2 className={styles.sectionHeader}>Get the extension</h2>
        <section className={styles.group}>
          {downloads.map(({ browser, title, detail, href, Icon }) => (
            <a
              key={browser}
              className={styles.download}
              href={href}
              download
              onClick={() => setChosen(browser)}
            >
              <Icon size={28} className={styles.browserIcon} />
              <span className={styles.downloadText}>
                <span className={styles.downloadTitle}>{title}</span>
                <span className={styles.downloadDetail}>
                  {detail}
                  {browser === currentBrowser && ' · This browser'}
                </span>
              </span>
              <ChevronRight size={16} strokeWidth={2.4} className={styles.chevron} />
            </a>
          ))}
        </section>
        {steps ? (
          <ol className={styles.steps}>
            {steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
        ) : (
          <p className={styles.footer}>Pick your browser to download the extension and see how to install it.</p>
        )}

        <h2 className={styles.sectionHeader}>Integrations</h2>
        <section className={styles.group}>
          {INTEGRATIONS.map((integration) => (
            <div key={integration.name} className={styles.integration}>
              <span className={styles.integrationIcon} style={{ backgroundColor: integration.color }}>
                <integration.Icon size={15} />
              </span>
              <span className={styles.integrationName}>{integration.name}</span>
              {status.state === 'present' && integration.capability === 'spotify' ? (
                <SpotifyIntegration />
              ) : status.state === 'present' && integration.capability === 'discord' ? (
                <DiscordIntegration />
              ) : (
                <span className={styles.value}>{integrationStatus(status, integration, auth)}</span>
              )}
            </div>
          ))}
        </section>
        {auth.state === 'ready' && auth.auth.problem && (
          <p className={styles.footer}>YouTube Music: {auth.auth.problem}.</p>
        )}
        <p className={styles.footer}>
          Sign-ins stay inside the extension: this page only ever sees results, never your cookies or tokens.
        </p>
        <p className={styles.footer}>
          Spotify Canvas, Discord presence and Listen Together are in{' '}
          <PrefetchLink to="/settings" className={styles.inlineLink}>
            Settings
          </PrefetchLink>
          .
        </p>
      </div>
    </div>
  );
}
