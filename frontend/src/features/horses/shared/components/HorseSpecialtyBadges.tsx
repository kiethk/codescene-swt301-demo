import { Pill, Tooltip } from '@/components/ui';
import { describeSpecialty } from '../../lib/specialty';
import type { HorseSpecialty } from '../../types';

const triggerClassName =
  'inline-flex rounded-full outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus)]';

/**
 * Racing specialty badges of a horse, for every role's horse screen. The backend decides which badges a horse
 * earns; this only draws them. Without enough data it says so instead of guessing.
 */
export function HorseSpecialtyBadges({
  specialty,
  size = 'sm',
}: {
  specialty: HorseSpecialty | null | undefined;
  size?: 'sm' | 'md';
}) {
  if (!specialty) return null;

  const view = describeSpecialty(specialty);

  if (view.badges.length === 0) {
    return (
      <Tooltip
        content={
          <ul className="space-y-0.5">
            {view.progress.map((line) => <li key={line}>{line}</li>)}
          </ul>
        }
      >
        <span tabIndex={0} className={triggerClassName}>
          <Pill tone="neutral" size={size}>No specialty yet</Pill>
        </span>
      </Tooltip>
    );
  }

  return (
    <ul className="flex flex-wrap items-center gap-1.5" aria-label="Racing specialty">
      {view.badges.map((badge) => (
        <li key={badge.id}>
          <Tooltip content={badge.detail}>
            <span tabIndex={0} className={triggerClassName}>
              <Pill tone={badge.tone} icon={badge.icon} size={size}>{badge.label}</Pill>
            </span>
          </Tooltip>
        </li>
      ))}
    </ul>
  );
}
