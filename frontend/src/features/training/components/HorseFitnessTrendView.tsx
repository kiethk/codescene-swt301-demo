'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  FieldLabel,
  FilterBar,
  FormField,
  Input,
  Notice,
  Panel,
  ScreenLayout,
  SegmentedControl,
  Spinner,
} from '@/components/ui';
import { horseService } from '@/features/horses/services/horseService';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Horse } from '@/features/stable/types';
import { displayError, formatDate } from '@/lib/display';
import { useTrainingRules } from '../hooks/useTrainingRules';
import { rangeForPreset, rangeLabel, type DatePreset, type DateRange } from '../lib/dateRange';
import { trainingService } from '../services/trainingService';
import type { HorseAlert, HorseFitnessTrendItem, ReadinessAssessment } from '../types';
import { FitnessTrendChart } from './FitnessTrendChart';
import { HorseAlertList } from './HorseAlertList';
import { HorseMetricsRow } from './HorseMetricsRow';
import { HorseProfileHeader } from './HorseProfileHeader';
import { WorkoutHistoryTable } from './WorkoutHistoryTable';

/**
 * Everything about one horse: profile, the period to look at, headline numbers, alerts, charts and the session
 * history. The period bar sits above the numbers so the reader knows what period they describe before reading them.
 */
export function HorseFitnessTrendView({ horseId }: { horseId: number }) {
  const rules = useTrainingRules();

  const [horse, setHorse] = useState<Horse | null>(null);
  const [specialty, setSpecialty] = useState<HorseSpecialty | null>(null);
  const [alerts, setAlerts] = useState<HorseAlert[]>([]);
  const [trend, setTrend] = useState<HorseFitnessTrendItem[]>([]);
  const [readiness, setReadiness] = useState<ReadinessAssessment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [preset, setPreset] = useState<DatePreset>('30D');
  const [customInputs, setCustomInputs] = useState<DateRange>({});
  const [appliedCustom, setAppliedCustom] = useState<DateRange>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { from, to } = rangeForPreset(preset, new Date(), appliedCustom);
      // The trend is required; everything else is a bonus and must not hide the screen when it fails.
      const [trendData, horseData, alertData, readinessData, specialtyData] = await Promise.all([
        trainingService.getFitnessTrend(horseId, from, to),
        horseService.getById(horseId).catch(() => null),
        trainingService.getHorseAlerts(horseId).catch(() => []),
        trainingService.getReadinessHistory(horseId, false).catch(() => []),
        horseService.getSpecialty(horseId).catch(() => null),
      ]);
      setTrend(trendData);
      setHorse(horseData);
      setAlerts(alertData);
      setReadiness(readinessData);
      setSpecialty(specialtyData);
    } catch (cause) {
      setError(displayError(cause, 'Unable to load horse fitness data.'));
    } finally {
      setLoading(false);
    }
  }, [horseId, preset, appliedCustom]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  function applyCustomRange(event: React.FormEvent) {
    event.preventDefault();
    setAppliedCustom(customInputs);
    setPreset('CUSTOM');
  }

  return (
    <ScreenLayout variant="detail">
      {error && <Notice tone="error">{error}</Notice>}

      <HorseProfileHeader horse={horse} horseId={horseId} specialty={specialty} />

      <FilterBar onSubmit={applyCustomRange} aria-label="Date range">
        <div className="space-y-1.5">
          <FieldLabel>Period</FieldLabel>
          <SegmentedControl
            label="Date range"
            value={preset}
            onChange={setPreset}
            options={[
              { value: '7D', label: '7 days' },
              { value: '30D', label: '30 days' },
              { value: '90D', label: '90 days' },
              { value: 'ALL', label: 'All time' },
              { value: 'CUSTOM', label: 'Custom' },
            ]}
          />
        </div>
        <FormField label="From">
          <Input type="date" value={customInputs.from ?? ''} onChange={(e) => setCustomInputs((r) => ({ ...r, from: e.target.value }))} />
        </FormField>
        <FormField label="To">
          <Input type="date" value={customInputs.to ?? ''} onChange={(e) => setCustomInputs((r) => ({ ...r, to: e.target.value }))} />
        </FormField>
        <Button type="submit" icon="filter">Apply</Button>
        <Button icon="refresh" onClick={load} loading={loading}>Refresh</Button>
        <p className="ml-auto self-center text-xs text-[var(--color-text-secondary)]">
          Showing: <strong className="text-[var(--color-text-primary)]">{rangeLabel(preset, appliedCustom, formatDate)}</strong>
        </p>
      </FilterBar>

      <HorseMetricsRow trend={trend} rules={rules} />

      <HorseAlertList alerts={alerts} />

      {loading && trend.length === 0 ? (
        <Panel padded>
          <div className="flex h-64 items-center justify-center"><Spinner size="lg" /></div>
        </Panel>
      ) : (
        <FitnessTrendChart trend={trend} readiness={readiness} rules={rules} />
      )}

      <WorkoutHistoryTable trend={trend} rules={rules} loading={loading} />
    </ScreenLayout>
  );
}
