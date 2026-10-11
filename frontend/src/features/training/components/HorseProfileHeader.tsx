import { HorseAvatar, MetricCard, Panel, Pill, StatusBadge, Tooltip } from '@/components/ui';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Horse } from '@/features/stable/types';
import { formatDate, formatNumber } from '@/lib/display';
import type { HorseFitnessTrendItem, TrainingRules } from '../types';

function average(values: number[]): number | null {
  return values.length > 0 ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
}

function highest(values: Array<number | null>): number | null {
  const present = values.filter((value): value is number => value != null);
  return present.length > 0 ? Math.max(...present) : null;
}

/**
 * Identity, health gate, racing specialty and the headline numbers of the selected date range.
 * The training decision is shown exactly as the backend reports it; the screen never re-derives it.
 */
export function HorseProfileHeader({
  horse,
  horseId,
  specialty,
  trend,
  rules,
}: {
  horse: Horse | null;
  horseId: number;
  specialty: HorseSpecialty | null;
  trend: HorseFitnessTrendItem[];
  rules: TrainingRules | null;
}) {
  const name = horse?.name ?? `Horse #${horseId}`;
  const averageRating = average(trend.map((t) => t.performanceRating).filter((r): r is number => r != null));
  const maxHeartRate = highest(trend.map((t) => t.maxHeartRate));
  const topSpeed = highest(trend.map((t) => t.topSpeedKmh));
  const heartRateOverLimit = rules != null && maxHeartRate != null && maxHeartRate > rules.maxHeartRateLimit;

  return (
    <Panel padded className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
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

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <MetricCard label="Completed sessions" value={trend.length} icon="check" />
        <MetricCard
          label="Average performance"
          value={averageRating != null ? formatNumber(averageRating, 1) : '—'}
          unit={averageRating != null ? '/10' : undefined}
          icon="trending-up"
          tone="success"
        />
        <MetricCard
          label="Max heart rate"
          value={maxHeartRate != null ? maxHeartRate : '—'}
          unit={maxHeartRate != null ? 'bpm' : undefined}
          icon="heart-pulse"
          tone={heartRateOverLimit ? 'danger' : 'default'}
          hint={rules ? `Limit ${rules.maxHeartRateLimit} bpm` : undefined}
        />
        <MetricCard
          label="Top speed"
          value={topSpeed != null ? formatNumber(topSpeed, 1) : '—'}
          unit={topSpeed != null ? 'km/h' : undefined}
          icon="gauge"
        />
      </div>
    </Panel>
  );
}
