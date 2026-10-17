import type { SVGProps } from 'react';

// Filled transport glyphs, drawn for BitChard. Lucide's outline versions read
// as too light next to the rest of the player chrome, and SF Symbols are
// licensed for Apple platforms only (prompt §2.2). 24×24 grid, currentColor.

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Svg({ size = 24, children, ...rest }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" {...rest}>
      {children}
    </svg>
  );
}

export function PlayIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M8.1 4.6c-.9-.5-1.9.1-1.9 1.1v12.6c0 1 1 1.6 1.9 1.1l10.6-6.3c.8-.5.8-1.7 0-2.2L8.1 4.6Z" />
    </Svg>
  );
}

export function PauseIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="6" y="4.5" width="4" height="15" rx="1.2" />
      <rect x="14" y="4.5" width="4" height="15" rx="1.2" />
    </Svg>
  );
}

/** Two joined triangles pointing right: next track. */
export function ForwardIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M2.6 7.1c0-.8.9-1.3 1.6-.9l7.6 4.9c.6.4.6 1.4 0 1.8l-7.6 4.9c-.7.4-1.6-.1-1.6-.9V7.1Z" />
      <path d="M12.1 7.1c0-.8.9-1.3 1.6-.9l7.6 4.9c.6.4.6 1.4 0 1.8l-7.6 4.9c-.7.4-1.6-.1-1.6-.9V7.1Z" />
    </Svg>
  );
}

/** Mirror of [ForwardIcon]: previous track. */
export function BackwardIcon(props: IconProps) {
  return (
    <Svg {...props} style={{ transform: 'scaleX(-1)', ...props.style }}>
      <path d="M2.6 7.1c0-.8.9-1.3 1.6-.9l7.6 4.9c.6.4.6 1.4 0 1.8l-7.6 4.9c-.7.4-1.6-.1-1.6-.9V7.1Z" />
      <path d="M12.1 7.1c0-.8.9-1.3 1.6-.9l7.6 4.9c.6.4.6 1.4 0 1.8l-7.6 4.9c-.7.4-1.6-.1-1.6-.9V7.1Z" />
    </Svg>
  );
}
