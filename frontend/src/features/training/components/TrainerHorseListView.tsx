'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  EmptyState,
  FieldLabel,
  FilterChips,
  HorseAvatar,
  LinkButton,
  ListSkeleton,
  Notice,
  PageHeader,
  Panel,
  Pill,
  ProgressBar,
  ScreenLayout,
  SearchInput,
} from '@/components/ui';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import { displayError, formatNumber } from '@/lib/display';
import { trainingService } from '../services/trainingService';
import type { TrainerDashboardHorse } from '../types';
import { RatingPill } from './RatingPill';

type HorseFilter = 'ALL' | 'ACTIVE' | 'ALERT';

function HorseCard({ horse }: { horse: TrainerDashboardHorse }) {
  return (
    <Panel padded className="flex flex-col justify-between gap-4">
      <div className="space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex min-w-0 items-center gap-3">
            <HorseAvatar name={horse.horseName} size={40} />
            <div className="min-w-0">
              <h3 className="truncate text-base font-semibold text-[var(--color-text-primary)]">{horse.horseName}</h3>
              <p className="truncate text-xs text-[var(--color-text-secondary)]">
                {horse.planStatus === 'NONE' ? 'No training course assigned' : horse.courseName}
              </p>
            </div>
          </div>
          {horse.alertsCount > 0
            ? <Pill tone="danger" size="sm" icon="alert-triangle">{horse.alertsCount} alerts</Pill>
            : <Pill tone="success" size="sm" icon="check">Clear</Pill>}
        </div>

        <HorseSpecialtyBadges specialty={horse.specialty} />

        {horse.totalSessions > 0 ? (
          <div className="space-y-1">
            <div className="flex items-center justify-between text-xs text-[var(--color-text-secondary)]">
              <span>Course progress</span>
              <span className="font-semibold text-[var(--color-text-primary)]">
                {horse.completedSessions}/{horse.totalSessions} sessions ({formatNumber(horse.progressPercent)}%)
              </span>
            </div>
            <ProgressBar label={`${horse.horseName} course progress`} value={horse.progressPercent} size="sm" tone="info" />
          </div>
        ) : (
          <p className="text-xs text-[var(--color-text-muted)]">No sessions scheduled</p>
        )}

        <div className="grid grid-cols-2 gap-2 border-t border-[var(--color-border)] pt-3">
          <div className="space-y-1">
            <FieldLabel>Latest performance</FieldLabel>
            <RatingPill rating={horse.latestPerformanceRating} />
          </div>
          <div className="space-y-1">
            <FieldLabel>30-day average</FieldLabel>
            <p className="text-sm font-semibold text-[var(--color-text-primary)]">
              {horse.avgPerformanceRating30d != null ? `${formatNumber(horse.avgPerformanceRating30d, 1)}/10` : '—'}
            </p>
          </div>
        </div>
      </div>

      <LinkButton href={`/trainer/horses/${horse.horseId}`} size="sm" icon="trending-up" className="w-full">
        View fitness and alerts
      </LinkButton>
    </Panel>
  );
}

export function TrainerHorseListView() {
  const [horses, setHorses] = useState<TrainerDashboardHorse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<HorseFilter>('ALL');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setHorses(await trainingService.getDashboard());
    } catch (cause) {
      setError(displayError(cause, 'Unable to load the horse list.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const inTraining = useMemo(() => horses.filter((h) => h.planStatus === 'ACTIVE').length, [horses]);
  const withAlerts = useMemo(() => horses.filter((h) => h.alertsCount > 0).length, [horses]);

  const visibleHorses = useMemo(() => {
    const query = search.trim().toLowerCase();
    return horses.filter((horse) => {
      if (query && !horse.horseName.toLowerCase().includes(query)) return false;
      if (filter === 'ACTIVE') return horse.planStatus === 'ACTIVE';
      if (filter === 'ALERT') return horse.alertsCount > 0;
      return true;
    });
  }, [horses, search, filter]);

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Assigned horses"
        description="Review each horse’s profile, racing specialty, fitness trend and health indicators."
        actions={
          <>
            <LinkButton href="/trainer/dashboard" size="sm" icon="grid">View dashboard</LinkButton>
            <Button size="sm" icon="refresh" onClick={load} loading={loading}>Refresh</Button>
          </>
        }
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <FilterChips
          label="Horse filter"
          value={filter}
          onChange={setFilter}
          options={[
            { value: 'ALL', label: 'All', count: horses.length },
            { value: 'ACTIVE', label: 'In training', count: inTraining },
            { value: 'ALERT', label: 'Alerts', count: withAlerts },
          ]}
        />
        <SearchInput className="w-full sm:w-64" value={search} onChange={setSearch} placeholder="Search horses…" />
      </div>

      {error && <Notice tone="error">{error}</Notice>}

      {loading && horses.length === 0 ? (
        <Panel padded><ListSkeleton rows={5} /></Panel>
      ) : visibleHorses.length === 0 ? (
        <Panel padded>
          <EmptyState
            icon="horse"
            title={horses.length === 0 ? 'No horses assigned yet' : 'No horses match the selected filters'}
            description={horses.length === 0 ? 'Horses appear here once they are assigned to a stall in your area.' : undefined}
          />
        </Panel>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {visibleHorses.map((horse) => <HorseCard key={horse.horseId} horse={horse} />)}
        </div>
      )}
    </ScreenLayout>
  );
}
