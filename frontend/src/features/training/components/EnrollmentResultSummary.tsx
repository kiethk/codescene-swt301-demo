'use client';

import { useMemo } from 'react';
import { Button, Icon, LinkButton, Notice, PageHeader, PageSection, Panel, Pill, ScreenLayout } from '@/components/ui';
import type { Horse } from '@/features/stable/types';
import { formatDate, formatTime } from '@/lib/display';
import { PLAN_STATUS } from '../lib/status';
import type { HorseTrainingPlanDetailResponse, PlanWorkoutItemResponse } from '../types';
import { StatusPill } from './StatusPill';

/**
 * Summary shown after a group enrollment. Enrolling 7 horses creates 7 plans, and the group can be split over
 * several lots (a lot is full, or two horses share a groom). That is correct behaviour, but unless it is spelled
 * out the trainer will think group matching is broken, so this screen shows where the first session landed.
 */
export function EnrollmentResultSummary({
  plans,
  horses,
  onDone,
}: {
  plans: HorseTrainingPlanDetailResponse[];
  horses: Horse[];
  onDone: () => void;
}) {
  const horseNameById = useMemo(() => new Map(horses.map((h) => [h.id, h.name] as const)), [horses]);

  /**
   * The FIRST session of each plan, grouped by lot. The first day is enough to see whether the group was split:
   * later days repeat the same structure, and listing all of them would be a wall of numbers nobody reads.
   */
  const firstSessionByLot = useMemo(() => {
    const byLot = new Map<number, { item: PlanWorkoutItemResponse; horseId: number }[]>();
    plans.forEach((plan) => {
      const first = plan.workouts[0];
      if (!first) return;
      byLot.set(first.lotId, [...(byLot.get(first.lotId) ?? []), { item: first, horseId: plan.plan.horseId }]);
    });
    return [...byLot.entries()].sort((a, b) => a[1][0].item.startTime.localeCompare(b[1][0].item.startTime));
  }, [plans]);

  const split = firstSessionByLot.length > 1;
  const firstDate = plans[0]?.workouts[0]?.lotDate;
  const nameOf = (horseId: number) => horseNameById.get(horseId) ?? `Horse #${horseId}`;

  return (
    <ScreenLayout variant="form">
      <PageHeader
        title={`Created ${plans.length} training plans`}
        description="Each horse has its own plan for recording metrics and feedback."
        eyebrow="Enrollment complete"
        actions={<Button variant="primary" onClick={onDone}>Back to courses</Button>}
      />

      <PageSection title={`First workout${firstDate ? ` — ${formatDate(firstDate)}` : ''}`}>
        <Panel padded className="space-y-2">
          {firstSessionByLot.map(([lotId, entries]) => {
            const sample = entries[0].item;
            return (
              <div key={lotId} className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3">
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <Pill tone="info" size="sm">Lot #{lotId}</Pill>
                  <strong>{formatTime(sample.startTime)} – {formatTime(sample.endTime)}</strong>
                  <span className="text-[var(--color-text-secondary)]">{sample.subjectName}</span>
                  <Pill tone="neutral" size="sm" icon="horse">{sample.lotOccupancy ?? entries.length} horses</Pill>
                </div>
                <p className="mt-1.5 text-xs text-[var(--color-text-secondary)]">
                  {entries.map((entry) => nameOf(entry.horseId)).join(' · ')}
                </p>
              </div>
            );
          })}
          {split && (
            <Notice tone="info">
              The group was split across {firstSessionByLot.length} workout sessions because a lot holds a limited number of
              horses and each groom can lead one horse per session.
            </Notice>
          )}
        </Panel>
      </PageSection>

      <PageSection title="Newly created plans">
        <Panel padded>
          <ul className="space-y-1.5">
            {plans.map((plan) => (
              <li
                key={plan.plan.id}
                className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--radius-md)] border border-[var(--color-border)] p-2.5 text-xs"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <strong className="text-[var(--color-text-primary)]">{nameOf(plan.plan.horseId)}</strong>
                  <span className="text-[var(--color-text-muted)]">
                    {plan.workouts.length} sessions · {formatDate(plan.plan.startDate)} → {formatDate(plan.plan.endDate)}
                  </span>
                  <StatusPill status={PLAN_STATUS[plan.plan.status]} />
                </div>
                <LinkButton href={`/trainer/plans/${plan.plan.id}`} size="sm" variant="link">
                  View details
                  <Icon name="chevron-right" size={12} />
                </LinkButton>
              </li>
            ))}
          </ul>
        </Panel>
      </PageSection>
    </ScreenLayout>
  );
}
