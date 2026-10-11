import { PageSection } from '@/components/ui';
import { DataTable, type DataTableColumn } from '@/components/ui/DataTable';
import { cn } from '@/lib/cn';
import { formatDate, formatNumber } from '@/lib/display';
import type { HorseFitnessTrendItem, TrainingRules } from '../types';
import { RatingPill } from './RatingPill';

function pair(left: number | null, right: number | null, unit: string): string {
  const show = (value: number | null) => (value != null ? formatNumber(value, 1) : '—');
  return `${show(left)} / ${show(right)} ${unit}`;
}

function buildColumns(rules: TrainingRules | null): DataTableColumn<HorseFitnessTrendItem>[] {
  return [
    {
      id: 'date',
      header: 'Date',
      sortValue: (row) => row.date,
      render: (row) => <span className="whitespace-nowrap font-medium">{formatDate(row.date)}</span>,
    },
    { id: 'subject', header: 'Subject', render: (row) => row.subjectName },
    {
      id: 'distance',
      header: 'Distance',
      align: 'right',
      sortValue: (row) => row.distanceMeters,
      render: (row) => (row.distanceMeters != null ? `${formatNumber(row.distanceMeters)} m` : '—'),
    },
    {
      id: 'speed',
      header: 'Speed (avg / max)',
      align: 'right',
      render: (row) => <span className="whitespace-nowrap">{pair(row.averageSpeedKmh, row.topSpeedKmh, 'km/h')}</span>,
    },
    {
      id: 'heartRate',
      header: 'Heart rate (avg / max / recovery)',
      align: 'right',
      render: (row) => {
        const maxOver = rules != null && row.maxHeartRate != null && row.maxHeartRate > rules.maxHeartRateLimit;
        const recoveryOver = rules != null && row.recoveryHeartRate != null && row.recoveryHeartRate > rules.recoveryHeartRateLimit;
        return (
          <span className="whitespace-nowrap">
            {row.averageHeartRate ?? '—'} /{' '}
            <strong className={cn(maxOver && 'text-[var(--color-danger)]')}>{row.maxHeartRate ?? '—'}</strong> /{' '}
            <strong className={cn(recoveryOver && 'text-[var(--color-warning)]')}>{row.recoveryHeartRate ?? '—'}</strong> bpm
          </span>
        );
      },
    },
    {
      id: 'rating',
      header: 'Performance',
      align: 'center',
      sortValue: (row) => row.performanceRating,
      render: (row) => <RatingPill rating={row.performanceRating} />,
    },
  ];
}

/** Completed sessions of the selected date range, newest first. */
export function WorkoutHistoryTable({
  trend,
  rules,
  loading,
}: {
  trend: HorseFitnessTrendItem[];
  rules: TrainingRules | null;
  loading: boolean;
}) {
  const rows = [...trend].sort((a, b) => b.date.localeCompare(a.date));

  return (
    <PageSection title={`Completed session history (${trend.length})`} description="Data from completed training sessions.">
      <DataTable
        ariaLabel="Completed sessions"
        rows={rows}
        columns={buildColumns(rules)}
        getRowKey={(row) => row.workoutId}
        loading={loading}
        pageSize={10}
        emptyTitle="No completed sessions in the selected date range"
      />
    </PageSection>
  );
}
