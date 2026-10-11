'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  EmptyState,
  LinkButton,
  ListSkeleton,
  PageHeader,
  PageSection,
  Panel,
  Pill,
  ProgressBar,
  ScreenLayout,
} from '@/components/ui';
import { stableApi } from '@/features/stable/services/stableService';
import type { UserSummary } from '@/features/stable/types';
import { displayError, formatDate, formatTime } from '@/lib/display';
import { planProgress } from '../lib/planProgress';
import { PLAN_STATUS, WORKOUT_STATUS } from '../lib/status';
import { trainingService } from '../services/trainingService';
import type { HorseTrainingPlanDetailResponse, PlanWorkoutItemResponse } from '../types';
import { CompleteWorkoutDialog } from './CompleteWorkoutDialog';
import { RatingPill } from './RatingPill';
import { StatusPill } from './StatusPill';

function WorkoutRow({
  workout,
  index,
  groomName,
  onRecord,
}: {
  workout: PlanWorkoutItemResponse;
  index: number;
  groomName: string | undefined;
  onRecord: () => void;
}) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <div className="space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-[var(--color-primary)]">#{index + 1}</span>
          <span className="text-sm font-semibold text-[var(--color-text-primary)]">{workout.subjectName}</span>
          <StatusPill status={WORKOUT_STATUS[workout.status]} />
        </div>

        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--color-text-secondary)]">
          <span>Date: <strong>{formatDate(workout.lotDate)}</strong></span>
          <span>Time: <strong>{formatTime(workout.startTime)} – {formatTime(workout.endTime)}</strong></span>
          {/* The lot is shared with other horses, while the groom belongs to THIS horse only, so they are labelled apart. */}
          <span>
            Lot #{workout.lotId}
            {workout.lotOccupancy != null && workout.lotOccupancy > 1 && (
              <span className="text-[var(--color-text-muted)]"> ({workout.lotOccupancy - 1} other horses)</span>
            )}
          </span>
          {/* A ternary and not &&: an unassigned groom must stay visible, because that is what needs attention. */}
          {workout.assignedGroomId ? (
            <span>Groom: <strong>{groomName ?? `#${workout.assignedGroomId}`}</strong></span>
          ) : (
            <span className="text-[var(--color-warning)]">Groom not assigned</span>
          )}
        </div>

        {workout.status === 'COMPLETED' && (
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-[var(--radius-md)] bg-[var(--color-surface-muted)] px-3 py-2 text-xs text-[var(--color-text-secondary)]">
            <RatingPill rating={workout.performanceRating} />
            {workout.topSpeedKmh != null && <span>Max speed: {workout.topSpeedKmh} km/h</span>}
            {workout.actualDistanceMeters != null && <span>Distance: {workout.actualDistanceMeters} m</span>}
            {workout.trainerFeedback && <span className="basis-full">Feedback: {workout.trainerFeedback}</span>}
          </div>
        )}
      </div>

      {workout.status === 'SCHEDULED' && (
        <Button variant="primary" size="sm" onClick={onRecord}>Record session results</Button>
      )}
    </li>
  );
}

export function PlanDetail({ planId }: { planId: number }) {
  const [detail, setDetail] = useState<HorseTrainingPlanDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeWorkout, setActiveWorkout] = useState<PlanWorkoutItemResponse | null>(null);
  /** Groom names come from the staff directory endpoint that the stable screen already uses. */
  const [grooms, setGrooms] = useState<UserSummary[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [plan, groomList] = await Promise.all([trainingService.getPlanById(planId), stableApi.getGrooms()]);
      setDetail(plan);
      setGrooms(groomList);
    } catch (cause) {
      setError(displayError(cause, 'Unable to load training plan details.'));
    } finally {
      setLoading(false);
    }
  }, [planId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const groomNameById = useMemo(() => new Map(grooms.map((g) => [g.id, g.fullName] as const)), [grooms]);

  const progress = useMemo(() => {
    const workouts = detail?.workouts ?? [];
    return planProgress(
      workouts.filter((w) => w.status === 'COMPLETED').length,
      workouts.length,
      workouts.filter((w) => w.status === 'CANCELLED').length,
    );
  }, [detail]);

  if (loading && !detail) return <ListSkeleton rows={5} />;

  if (error || !detail) {
    return (
      <Panel padded>
        <EmptyState
          icon="alert-triangle"
          title="Training plan unavailable"
          description={error ?? 'This training plan could not be found.'}
          action={<LinkButton href="/trainer/plans" size="sm" icon="arrow-left">Back to plans</LinkButton>}
        />
      </Panel>
    );
  }

  const { plan, workouts } = detail;
  const horseName = detail.horseName ?? `Horse #${plan.horseId}`;

  return (
    <ScreenLayout variant="detail">
      <PageHeader
        title={horseName}
        description={
          <>
            Course: <strong>{detail.courseName ?? `#${plan.courseId}`}</strong>
            <br />
            {formatDate(plan.startDate)} – {formatDate(plan.endDate)} · {workouts.length} planned sessions
          </>
        }
        breadcrumbs={[{ label: 'Training plans', href: '/trainer/plans' }, { label: horseName }]}
        actions={
          <>
            <Pill tone={PLAN_STATUS[plan.status].tone}>{PLAN_STATUS[plan.status].label}</Pill>
            <LinkButton href="/trainer/schedule" size="sm" icon="calendar">View lot schedule</LinkButton>
          </>
        }
      />

      <Panel padded className="space-y-2">
        <div className="flex items-center justify-between text-sm">
          <span className="font-medium text-[var(--color-text-primary)]">
            Progress: {progress.completed}/{progress.total} sessions ({progress.percent}%)
          </span>
          {progress.cancelled > 0 && (
            <span className="text-xs text-[var(--color-text-muted)]">({progress.cancelled} cancelled)</span>
          )}
        </div>
        <ProgressBar label="Plan progress" value={progress.percent} />
      </Panel>

      <PageSection title="Session schedule">
        <Panel padded>
          {workouts.length === 0 ? (
            <p className="text-xs text-[var(--color-text-muted)]">No sessions have been scheduled for this plan yet.</p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {workouts.map((workout, index) => (
                <WorkoutRow
                  key={workout.workoutId}
                  workout={workout}
                  index={index}
                  groomName={workout.assignedGroomId ? groomNameById.get(workout.assignedGroomId) : undefined}
                  onRecord={() => setActiveWorkout(workout)}
                />
              ))}
            </ul>
          )}
        </Panel>
      </PageSection>

      <CompleteWorkoutDialog
        key={activeWorkout?.workoutId ?? 'none'}
        workout={activeWorkout}
        open={activeWorkout !== null}
        onClose={() => setActiveWorkout(null)}
        onSuccess={load}
      />
    </ScreenLayout>
  );
}
