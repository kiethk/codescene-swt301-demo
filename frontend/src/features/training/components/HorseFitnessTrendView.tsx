'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  FieldLabel,
  FilterBar,
  FormField,
  Input,
  LinkButton,
  Notice,
  PageHeader,
  Panel,
  ScreenLayout,
  SegmentedControl,
  Spinner,
} from '@/components/ui';
import { horseService } from '@/features/horses/services/horseService';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Horse } from '@/features/stable/types';
import { displayError } from '@/lib/display';
import { useTrainingRules } from '../hooks/useTrainingRules';
import { rangeForPreset, type DatePreset, type DateRange } from '../lib/dateRange';
import { trainingService } from '../services/trainingService';
import type { HorseAlert, HorseFitnessTrendItem, ReadinessAssessment } from '../types';
import { FitnessTrendChart } from './FitnessTrendChart';
import { HorseAlertList } from './HorseAlertList';
import { HorseProfileHeader } from './HorseProfileHeader';
import { WorkoutHistoryTable } from './WorkoutHistoryTable';

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

  const name = horse?.name ?? `Horse #${horseId}`;

  return (
    <ScreenLayout variant="detail">
      <PageHeader
        title={name}
        description="Track fitness trends, injury risks and development progress."
        breadcrumbs={[
          { label: 'Dashboard', href: '/trainer/dashboard' },
          { label: 'Horses', href: '/trainer/horses' },
          { label: name },
        ]}
        actions={
          <>
            <LinkButton href="/trainer/horses" size="sm" icon="arrow-left">Back to horses</LinkButton>
            <Button size="sm" icon="refresh" onClick={load} loading={loading}>Refresh</Button>
          </>
        }
      />

      {error && <Notice tone="error">{error}</Notice>}

      <HorseProfileHeader horse={horse} horseId={horseId} specialty={specialty} trend={trend} rules={rules} />

      <HorseAlertList alerts={alerts} />

      <FilterBar onSubmit={applyCustomRange} aria-label="Date range">
        <div className="space-y-1.5">
          <FieldLabel>Date range</FieldLabel>
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
      </FilterBar>

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
