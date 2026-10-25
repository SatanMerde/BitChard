import { PrefetchLink } from '@/components/PrefetchLink';
import type { CategoryGroup } from '@/data/music/types';
import { browseHref } from './links';

/**
 * Browse Categories bricks (report §6): 16:9 tiles, radius 10, the title at
 * the bottom left. Coloured from YouTube's own stripe colour per mood/genre.
 */
export function CategoryTiles({ groups }: { groups: CategoryGroup[] }) {
  return (
    <>
      {groups.map((group) => (
        <section key={group.title} className="pt-3" aria-label={group.title}>
          <h2 className="mx-(--body-gutter) type-title-2-emphasized">{group.title}</h2>
          {/* 4 columns at ≥1000px, gap 20px (report §6); 3, then 2, below. */}
          <ul className="mx-(--body-gutter) mt-3 mb-8 grid grid-cols-4 gap-5 max-lg:grid-cols-3 max-[607px]:grid-cols-2 max-[607px]:gap-2.5">
            {group.categories.map((category) => (
              <li key={`${category.title}-${category.link.params}`}>
                <PrefetchLink
                  to={browseHref(category.link)}
                  className="relative flex aspect-video items-end overflow-hidden rounded-artwork-lg bg-[radial-gradient(120%_90%_at_100%_0%,color-mix(in_srgb,var(--tile-color)_70%,#fff)_0%,transparent_70%),linear-gradient(160deg,var(--tile-color),color-mix(in_srgb,var(--tile-color)_55%,#000))] px-3.5 py-3 text-white transition-[scale] duration-200 ease-panel hover:scale-[1.015]"
                  style={{ '--tile-color': category.color } as React.CSSProperties}
                >
                  <span className="type-title-2-emphasized [text-shadow:0_1px_8px_#0000004d]">{category.title}</span>
                </PrefetchLink>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}
