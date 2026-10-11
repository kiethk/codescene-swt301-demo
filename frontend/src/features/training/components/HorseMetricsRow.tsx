import { MetricCard } from '@/components/ui';
import { formatNumber } from '@/lib/display';
import type { HorseFitnessTrendItem, TrainingRules } from '../types';

function highest(values: Array<number | null>): number | null {
  const present = values.filter((value): value is number => value != null);
  return present.length > 0 ? Math.max(...present) : null;
}

/**
 * Headline numbers of the selected period, from every completed session of the horse in that period (all of its
 * plans together). The period is named by the date range bar right above, so these cards do not repeat it.
 */
export function HorseMetricsRow({ trend, rules }: { trend: HorseFitnessTrendItem[]; rules: TrainingRules | null }) {
  const ratings = trend.map((t) => t.performanceRating).filter((r): r is number => r != null);
  const averageRating = ratings.length > 0 ? ratings.reduce((sum, r) => sum + r, 0) / ratings.length : null;
  const maxHeartRate = highest(trend.map((t) => t.maxHeartRate));
  const topSpeed = highest(trend.map((t) => t.topSpeedKmh));
  const overLimit = rules != null && maxHeartRate != null && maxHeartRate > rules.maxHeartRateLimit;

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <MetricCard label="Completed sessions" value={trend.length} icon="check" hint="Across all plans" />
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
        tone={overLimit ? 'danger' : 'default'}
        hint={rules ? `Limit ${rules.maxHeartRateLimit} bpm` : undefined}
      />
      <MetricCard
        label="Top speed"
        value={topSpeed != null ? formatNumber(topSpeed, 1) : '—'}
        unit={topSpeed != null ? 'km/h' : undefined}
        icon="gauge"
      />
    </div>
  );
}
