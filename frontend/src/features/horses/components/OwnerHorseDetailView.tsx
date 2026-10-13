'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import {
  Button,
  EmptyState,
  HorseAvatar,
  Icon,
  Notice,
  PageHeader,
  Panel,
  Pill,
  ScreenLayout,
  Spinner,
  StatusBadge,
  Tabs,
  type TabItem,
} from '@/components/ui';
import { horseService } from '../services/horseService';
import { trainingService } from '@/features/training/services/trainingService';
import { racingService } from '@/features/racing/services/racingService';
import { useTrainingRules } from '@/features/training/hooks/useTrainingRules';
import { HorseSpecialtyBadges } from '../shared/components/HorseSpecialtyBadges';
import { HorsePedigreeCard } from './HorsePedigreeCard';
import { HorseHealthSummary } from './HorseHealthSummary';
import { HorseRacingHistory } from './HorseRacingHistory';
import { FitnessTrendChart } from '@/features/training/components/FitnessTrendChart';
import { HorseMetricsRow } from '@/features/training/components/HorseMetricsRow';
import { WorkoutHistoryTable } from '@/features/training/components/WorkoutHistoryTable';
import type { Horse } from '@/features/stable/types';
import type { HorseHealthMetric, HorsePedigree, HorseSpecialty } from '../types';
import type { HorseAlert, HorseFitnessTrendItem, ReadinessAssessment } from '@/features/training/types';
import type { RaceRegistrationResponse } from '@/features/racing/types';
import { displayError, formatDate } from '@/lib/display';

const tabs: TabItem[] = [
  { id: 'overview', label: 'Overview & Pedigree', icon: 'file-text' },
  { id: 'training', label: 'Training & Fitness', icon: 'activity' },
  { id: 'health', label: 'Health & Care', icon: 'heart-pulse' },
  { id: 'racing', label: 'Racing History', icon: 'flag' },
];

export function OwnerHorseDetailView({ horseId }: { horseId: number }) {
  const rules = useTrainingRules();
  const [activeTab, setActiveTab] = useState('overview');

  const [horse, setHorse] = useState<Horse | null>(null);
  const [specialty, setSpecialty] = useState<HorseSpecialty | null>(null);
  const [pedigree, setPedigree] = useState<HorsePedigree | null>(null);

  // Training data
  const [trend, setTrend] = useState<HorseFitnessTrendItem[]>([]);
  const [readiness, setReadiness] = useState<ReadinessAssessment[]>([]);
  const [alerts, setAlerts] = useState<HorseAlert[]>([]);

  // Health data
  const [healthMetrics, setHealthMetrics] = useState<HorseHealthMetric[]>([]);
  const [preventiveCare, setPreventiveCare] = useState<any[]>([]);

  // Racing data
  const [raceRegistrations, setRaceRegistrations] = useState<RaceRegistrationResponse[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [
        horseData,
        specialtyData,
        pedigreeData,
        trendData,
        readinessData,
        alertData,
        metricsData,
        preventiveData,
        racingData,
      ] = await Promise.all([
        horseService.getById(horseId),
        horseService.getSpecialty(horseId).catch(() => null),
        horseService.getPedigree(horseId).catch(() => null),
        trainingService.getFitnessTrend(horseId).catch(() => []),
        trainingService.getReadinessHistory(horseId, false).catch(() => []),
        trainingService.getHorseAlerts(horseId).catch(() => []),
        horseService.getHealthMetrics(horseId).catch(() => []),
        horseService.getPreventiveCare(horseId).catch(() => []),
        racingService.listMine(horseId).catch(() => []),
      ]);

      setHorse(horseData);
      setSpecialty(specialtyData);
      setPedigree(pedigreeData);
      setTrend(trendData);
      setReadiness(readinessData);
      setAlerts(alertData);
      setHealthMetrics(metricsData);
      setPreventiveCare(preventiveData);
      setRaceRegistrations(racingData);
    } catch (cause) {
      setError(displayError(cause, 'Unable to load horse details.'));
    } finally {
      setLoading(false);
    }
  }, [horseId]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  if (loading) {
    return (
      <ScreenLayout variant="detail">
        <Panel padded className="flex h-64 items-center justify-center">
          <Spinner />
        </Panel>
      </ScreenLayout>
    );
  }

  if (error || !horse) {
    return (
      <ScreenLayout variant="detail">
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Unable to open horse details"
            description={error || 'Horse not found.'}
            action={
              <Link href="/owner/horses">
                <Button size="sm" icon="arrow-left">Back to My Horses</Button>
              </Link>
            }
          />
        </Panel>
      </ScreenLayout>
    );
  }

  return (
    <ScreenLayout variant="detail">
      {/* Top Breadcrumb Navigation */}
      <div className="flex items-center gap-2 text-[12px] text-[var(--color-text-muted)]">
        <Link
          href="/owner/horses"
          className="hover:text-[var(--color-primary)] flex items-center gap-1 transition-colors"
        >
          <Icon name="arrow-left" size={12} />
          My Horses
        </Link>
        <span>/</span>
        <span className="text-[var(--color-text-primary)] font-medium">{horse.name}</span>
      </div>

      {/* Horse Header Card */}
      <Panel padded className="space-y-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4">
            <HorseAvatar name={horse.name} size={64} rounded="md" />
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-xl font-bold tracking-tight text-[var(--color-text-primary)]">
                  {horse.name}
                </h1>
                <StatusBadge status={horse.currentStatus} size="sm" />
              </div>
              <p className="text-[13px] text-[var(--color-text-muted)] mt-0.5">
                {horse.breed || 'Breed not specified'} · Born: {formatDate(horse.dateOfBirth)}
              </p>
            </div>
          </div>

          <div className="flex flex-col items-start sm:items-end gap-1.5">
            <span className="text-[11px] font-semibold text-[var(--color-text-muted)] uppercase tracking-wider">
              Stall Assignment
            </span>
            <span className="text-[13px] font-medium text-[var(--color-text-primary)]">
              {horse.currentStallId ? `Stall #${horse.currentStallId}` : 'Not assigned to a stall'}
            </span>
          </div>
        </div>

        {/* Racing Specialty Section */}
        <div className="pt-3 border-t border-[var(--color-border-subtle)] flex flex-wrap items-center gap-2">
          <span className="text-[12px] font-medium text-[var(--color-text-muted)] flex items-center gap-1">
            <Icon name="star" size={14} className="text-[var(--color-primary)]" />
            Racing Specialty:
          </span>
          <HorseSpecialtyBadges specialty={specialty} size="md" />
        </div>

        {/* Veterinary Training Restriction Notice */}
        {horse.trainingDecision === 'BLOCKED' && (
          <Notice tone="error" title="Training is Currently Suspended">
            {horse.trainingDecisionReason || 'This horse has been placed on medical hold by veterinary staff.'}
          </Notice>
        )}
      </Panel>

      {/* Tab Navigation Strip */}
      <Tabs tabs={tabs} active={activeTab} onChange={setActiveTab} />

      {/* Tab 1: Overview & Pedigree */}
      {activeTab === 'overview' && (
        <div className="space-y-4">
          <Panel padded>
            <h3 className="text-[14px] font-semibold text-[var(--color-text-primary)] mb-3 flex items-center gap-2">
              <Icon name="info" size={16} className="text-[var(--color-primary)]" />
              General Physical & Identity Information
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 text-[13px]">
              <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
                <span className="text-[var(--color-text-muted)]">Horse ID:</span>
                <span className="font-semibold text-[var(--color-text-primary)]">#{horse.id}</span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
                <span className="text-[var(--color-text-muted)]">Current Status:</span>
                <StatusBadge status={horse.currentStatus} size="sm" />
              </div>
              <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
                <span className="text-[var(--color-text-muted)]">Training Clearance:</span>
                <Pill tone={horse.trainingDecision === 'ALLOWED' ? 'success' : 'danger'} size="sm">
                  {horse.trainingDecision || 'ALLOWED'}
                </Pill>
              </div>
              <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
                <span className="text-[var(--color-text-muted)]">Registration No:</span>
                <span className="font-medium text-[var(--color-text-primary)]">
                  {horse.registrationNumber || pedigree?.registrationNumber || 'None'}
                </span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
                <span className="text-[var(--color-text-muted)]">Registry:</span>
                <span className="font-medium text-[var(--color-text-primary)]">
                  {pedigree?.registryName || 'Not recorded'}
                </span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
                <span className="text-[var(--color-text-muted)]">Stable Location:</span>
                <span className="font-medium text-[var(--color-text-primary)]">
                  {horse.currentStallId ? `Stall #${horse.currentStallId}` : 'Unassigned'}
                </span>
              </div>
            </div>
          </Panel>

          {/* Pedigree Ancestry Card */}
          <HorsePedigreeCard pedigree={pedigree} />
        </div>
      )}

      {/* Tab 2: Training & Fitness */}
      {activeTab === 'training' && (
        <div className="space-y-6">
          {/* Key Metric Numbers */}
          <HorseMetricsRow trend={trend} rules={rules} />

          {/* Charts (Heart rate, speed, performance rating) */}
          <FitnessTrendChart trend={trend} readiness={readiness} rules={rules} />

          {/* Completed Workouts Table */}
          <WorkoutHistoryTable trend={trend} rules={rules} loading={false} />
        </div>
      )}

      {/* Tab 3: Health & Care */}
      {activeTab === 'health' && (
        <HorseHealthSummary
          alerts={alerts}
          metrics={healthMetrics}
          preventiveCare={preventiveCare}
        />
      )}

      {/* Tab 4: Racing History */}
      {activeTab === 'racing' && (
        <HorseRacingHistory registrations={raceRegistrations} />
      )}
    </ScreenLayout>
  );
}
