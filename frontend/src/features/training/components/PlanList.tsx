'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  EmptyState,
  FilterChips,
  LinkButton,
  ListSkeleton,
  PageHeader,
  Panel,
  ProgressBar,
  ScreenLayout,
} from '@/components/ui';
import { displayError, formatDate } from '@/lib/display';
import { planProgress } from '../lib/planProgress';
import { PLAN_STATUS } from '../lib/status';
import { trainingService } from '../services/trainingService';
import type { PlanStatus, PlanSummaryResponse } from '../types';
import { StatusPill } from './StatusPill';

type StatusFilter = 'ALL' | PlanStatus;

const FILTERS: StatusFilter[] = ['ALL', 'UPCOMING', 'ACTIVE', 'COMPLETED', 'CANCELLED'];

function PlanRow({ plan }: { plan: PlanSummaryResponse }) {
  const progress = planProgress(plan.completedSessions, plan.totalSessions, plan.cancelledSessions);

  return (
    <Panel padded>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="min-w-0 space-y-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <strong className="text-base text-[var(--color-text-primary)]">{plan.horseName}</strong>
            <StatusPill status={PLAN_STATUS[plan.status]} />
          </div>
          <p className="text-xs text-[var(--color-text-secondary)]">{plan.courseName}</p>
          <p className="text-xs text-[var(--color-text-muted)]">
            {formatDate(plan.startDate)} → {formatDate(plan.endDate)}
          </p>
        </div>

        <div className="flex items-center gap-4">
          <div className="w-44 space-y-1">
            <div className="flex justify-between text-xs text-[var(--color-text-secondary)]">
              <span>{progress.completed}/{progress.total} sessions</span>
              <span>{progress.percent}%</span>
            </div>
            <ProgressBar label={`${plan.horseName} plan progress`} value={progress.percent} size="sm" />
            {progress.cancelled > 0 && (
              <p className="text-xs text-[var(--color-text-muted)]">{progress.cancelled} sessions cancelled</p>
            )}
          </div>
          <LinkButton href={`/trainer/plans/${plan.planId}`} size="sm">Details</LinkButton>
        </div>
      </div>
    </Panel>
  );
}

export function PlanList() {
  const [plans, setPlans] = useState<PlanSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('ALL');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPlans(await trainingService.getPlans());
    } catch (cause) {
      setError(displayError(cause, 'Unable to load training plans.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const visible = useMemo(
    () => (filter === 'ALL' ? plans : plans.filter((plan) => plan.status === filter)),
    [plans, filter],
  );

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Training plans"
        description="Track each horse’s training progress."
        actions={<LinkButton href="/trainer/plans/new" variant="primary" size="sm" icon="plus">Enroll horses</LinkButton>}
      />

      <FilterChips
        label="Plan status"
        value={filter}
        onChange={setFilter}
        options={FILTERS.map((status) => ({
          value: status,
          label: status === 'ALL' ? 'All' : PLAN_STATUS[status].label,
          count: status === 'ALL' ? plans.length : plans.filter((plan) => plan.status === status).length,
        }))}
      />

      {loading ? (
        <Panel padded><ListSkeleton rows={6} /></Panel>
      ) : error ? (
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Unable to load training plans"
            description={error}
            action={<Button size="sm" icon="refresh" onClick={load}>Try again</Button>}
          />
        </Panel>
      ) : visible.length === 0 ? (
        <Panel padded>
          <EmptyState
            icon="clipboard"
            title={plans.length === 0 ? 'No training plans yet' : 'No plans with this status'}
            description={plans.length === 0 ? 'Enroll a horse in a training course to get started.' : undefined}
          />
        </Panel>
      ) : (
        <div className="space-y-2">
          {visible.map((plan) => <PlanRow key={plan.planId} plan={plan} />)}
        </div>
      )}
    </ScreenLayout>
  );
}
