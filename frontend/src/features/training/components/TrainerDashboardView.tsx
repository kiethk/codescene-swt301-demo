'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  FilterChips,
  LinkButton,
  MetricCard,
  Notice,
  PageHeader,
  Panel,
  Pill,
  ProgressBar,
  ScreenLayout,
  SectionTitle,
} from '@/components/ui';
import { DataTable, type DataTableColumn } from '@/components/ui/DataTable';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import { displayError, formatNumber } from '@/lib/display';
import { RATING_LEGEND, ratingDistribution } from '../lib/rating';
import { PLAN_STATUS } from '../lib/status';
import { trainingService } from '../services/trainingService';
import type { TrainerDashboardHorse } from '../types';
import { RatingPill } from './RatingPill';
import { StatusPill } from './StatusPill';

type HorseFilter = 'ALL' | 'ACTIVE' | 'ALERT';

const columns: DataTableColumn<TrainerDashboardHorse>[] = [
  {
    id: 'horse',
    header: 'Horse',
    sortValue: (horse) => horse.horseName,
    render: (horse) => (
      <div className="min-w-0">
        <Link
          href={`/trainer/horses/${horse.horseId}`}
          className="font-semibold hover:text-[var(--color-primary)] focus-visible:rounded-sm focus-visible:outline-2"
        >
          {horse.horseName}
        </Link>
        <div className="text-xs text-[var(--color-text-muted)]">
          {[horse.stallCode, horse.breed].filter(Boolean).join(' · ') || '—'}
        </div>
      </div>
    ),
  },
  {
    id: 'specialty',
    header: 'Specialty',
    render: (horse) => <HorseSpecialtyBadges specialty={horse.specialty} />,
  },
  {
    id: 'course',
    header: 'Training course',
    render: (horse) =>
      horse.planStatus === 'NONE'
        ? <span className="text-[var(--color-text-muted)]">Not enrolled</span>
        : <span className="text-[var(--color-text-secondary)]">{horse.courseName}</span>,
  },
  {
    id: 'status',
    header: 'Status',
    sortValue: (horse) => PLAN_STATUS[horse.planStatus].label,
    render: (horse) => <StatusPill status={PLAN_STATUS[horse.planStatus]} />,
  },
  {
    id: 'progress',
    header: 'Course progress',
    className: 'min-w-40',
    sortValue: (horse) => horse.progressPercent,
    render: (horse) =>
      horse.totalSessions > 0 ? (
        <div className="space-y-1">
          <div className="flex items-center justify-between text-xs text-[var(--color-text-secondary)]">
            <span>{horse.completedSessions}/{horse.totalSessions} sessions</span>
            <span className="font-semibold">{formatNumber(horse.progressPercent)}%</span>
          </div>
          <ProgressBar label={`${horse.horseName} course progress`} value={horse.progressPercent} size="sm" tone="info" />
        </div>
      ) : (
        <span className="text-[var(--color-text-muted)]">—</span>
      ),
  },
  {
    id: 'latest',
    header: 'Latest',
    align: 'center',
    sortValue: (horse) => horse.latestPerformanceRating,
    render: (horse) => <RatingPill rating={horse.latestPerformanceRating} />,
  },
  {
    id: 'average',
    header: '30-day average',
    align: 'center',
    sortValue: (horse) => horse.avgPerformanceRating30d,
    render: (horse) =>
      horse.avgPerformanceRating30d != null
        ? <span className="font-semibold">{formatNumber(horse.avgPerformanceRating30d, 1)}</span>
        : <span className="text-[var(--color-text-muted)]">—</span>,
  },
  {
    id: 'alerts',
    header: 'Alerts',
    align: 'center',
    sortValue: (horse) => horse.alertsCount,
    render: (horse) =>
      horse.alertsCount > 0
        ? <Pill tone="danger" size="sm" icon="alert-triangle">{horse.alertsCount}</Pill>
        : <Pill tone="success" size="sm" icon="check">Clear</Pill>,
  },
  {
    id: 'actions',
    header: <span className="sr-only">Actions</span>,
    align: 'right',
    render: (horse) => (
      <LinkButton href={`/trainer/horses/${horse.horseId}`} size="sm" icon="trending-up">
        View fitness
      </LinkButton>
    ),
  },
];

export function TrainerDashboardView() {
  const [horses, setHorses] = useState<TrainerDashboardHorse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<HorseFilter>('ALL');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setHorses(await trainingService.getDashboard());
    } catch (cause) {
      setError(displayError(cause, 'Unable to load training dashboard data.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const inTraining = useMemo(() => horses.filter((h) => h.planStatus === 'ACTIVE'), [horses]);
  const withAlerts = useMemo(() => horses.filter((h) => h.alertsCount > 0), [horses]);

  const averageRating = useMemo(() => {
    const ratings = horses.map((h) => h.avgPerformanceRating30d).filter((r): r is number => r != null);
    return ratings.length > 0 ? ratings.reduce((sum, r) => sum + r, 0) / ratings.length : null;
  }, [horses]);

  const distribution = useMemo(
    () => ratingDistribution(horses.map((h) => h.avgPerformanceRating30d ?? h.latestPerformanceRating)),
    [horses],
  );

  const visibleHorses = filter === 'ACTIVE' ? inTraining : filter === 'ALERT' ? withAlerts : horses;

  return (
    <ScreenLayout variant="dashboard">
      <PageHeader
        title="Fitness and training overview"
        description="Track training progress across your stable and identify overtraining risks early."
        actions={
          <>
            <LinkButton href="/trainer/plans" size="sm" icon="clipboard">Manage plans</LinkButton>
            <Button size="sm" icon="refresh" onClick={load} loading={loading}>Refresh</Button>
          </>
        }
      />

      {error && <Notice tone="error">{error}</Notice>}

      <div className="grid grid-cols-1 gap-4 min-[480px]:grid-cols-2 lg:grid-cols-4">
        <MetricCard label="Total horses" value={horses.length} icon="horse" hint="Assigned stable" />
        <MetricCard label="In training" value={inTraining.length} icon="activity" tone="info" unit="active plans" hint="With scheduled sessions" />
        <MetricCard
          label="Risk alerts"
          value={withAlerts.length}
          icon="alert-triangle"
          tone={withAlerts.length > 0 ? 'danger' : 'success'}
          unit="horses"
          hint="Horses to monitor"
        />
        <MetricCard
          label="Average performance (30 days)"
          value={averageRating != null ? `${formatNumber(averageRating, 1)}/10` : '—'}
          icon="trending-up"
          tone="success"
          hint="Across all assigned horses"
        />
      </div>

      <Panel padded className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <SectionTitle>Team performance distribution</SectionTitle>
          <div className="flex flex-wrap items-center gap-2">
            <Pill tone="success" size="sm">{RATING_LEGEND.excellent}: {distribution.excellent}</Pill>
            <Pill tone="info" size="sm">{RATING_LEGEND.good}: {distribution.good}</Pill>
            <Pill tone="warning" size="sm">{RATING_LEGEND.needsImprovement}: {distribution.needsImprovement}</Pill>
            <Pill tone="neutral" size="sm">No data: {distribution.noData}</Pill>
          </div>
        </div>
        {horses.length > 0 && (
          <ProgressBar
            label="Team performance distribution"
            max={horses.length}
            segments={[
              { value: distribution.excellent, tone: 'success', label: `Excellent: ${distribution.excellent}` },
              { value: distribution.good, tone: 'info', label: `On target: ${distribution.good}` },
              { value: distribution.needsImprovement, tone: 'warning', label: `Needs improvement: ${distribution.needsImprovement}` },
              { value: distribution.noData, tone: 'neutral', label: `No data: ${distribution.noData}` },
            ]}
          />
        )}
      </Panel>

      <div className="space-y-3">
        <FilterChips
          label="Horse filter"
          value={filter}
          onChange={setFilter}
          options={[
            { value: 'ALL', label: 'All', count: horses.length },
            { value: 'ACTIVE', label: 'In training', count: inTraining.length },
            { value: 'ALERT', label: 'With alerts', count: withAlerts.length },
          ]}
        />
        <DataTable
          ariaLabel="Assigned horses"
          rows={visibleHorses}
          columns={columns}
          getRowKey={(horse) => horse.horseId}
          loading={loading}
          getSearchText={(horse) => `${horse.horseName} ${horse.courseName} ${horse.stallCode ?? ''}`}
          searchPlaceholder="Search horses…"
          emptyTitle={horses.length === 0 ? 'No horses assigned yet' : 'No horses match the selected filters'}
          emptyDescription={horses.length === 0 ? 'Horses appear here once they are assigned to a stall in your area.' : undefined}
        />
      </div>
    </ScreenLayout>
  );
}
