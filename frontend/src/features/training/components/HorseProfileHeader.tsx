import { HorseAvatar, LinkButton, Panel, Pill, StatusBadge, Tooltip } from '@/components/ui';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Horse } from '@/features/stable/types';
import { formatDate } from '@/lib/display';

/**
 * Identity, health gate and racing specialty of the selected horse, with a shortcut to its training plans.
 * The training decision is shown exactly as the backend reports it; the screen never re-derives it.
 */
export function HorseProfileHeader({
  horse,
  horseId,
  specialty,
}: {
  horse: Horse | null;
  horseId: number;
  specialty: HorseSpecialty | null;
}) {
  const name = horse?.name ?? `Horse #${horseId}`;

  return (
    <Panel padded className="flex flex-wrap items-center justify-between gap-4">
      <div className="flex min-w-0 items-center gap-3">
        <HorseAvatar name={name} size={48} rounded="md" />
        <div className="min-w-0 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">{name}</h2>
            {horse && <StatusBadge status={horse.currentStatus} />}
            {horse?.trainingDecision === 'BLOCKED' && (
              <Tooltip content={horse.trainingDecisionReason || 'Training is blocked'}>
                <span tabIndex={0} className="inline-flex rounded-full outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus)]">
                  <Pill tone="danger" icon="lock" size="sm">Training blocked</Pill>
                </span>
              </Tooltip>
            )}
          </div>
          <p className="text-xs text-[var(--color-text-secondary)]">
            {[
              horse?.registrationNumber && `Reg. ${horse.registrationNumber}`,
              horse?.breed,
              horse?.dateOfBirth && `Born ${formatDate(horse.dateOfBirth)}`,
            ].filter(Boolean).join(' · ') || 'Track fitness trends, injury risks and development progress.'}
          </p>
          <HorseSpecialtyBadges specialty={specialty} size="md" />
        </div>
      </div>

      <LinkButton href={`/trainer/plans?horseId=${horseId}`} icon="layers">View training plans</LinkButton>
    </Panel>
  );
}
