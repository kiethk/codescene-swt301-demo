'use client';

import { useMemo, useState } from 'react';
import { EmptyState, Panel, SegmentedControl } from '@/components/ui';
import type { HorseFitnessTrendItem, ReadinessAssessment, TrainingRules } from '../types';
import { HeartRateChart } from './charts/HeartRateChart';
import { PerformanceChart, type ReadinessHover } from './charts/PerformanceChart';
import { SpeedChart } from './charts/SpeedChart';
import { ReadinessTooltip, WorkoutTooltip, type WorkoutHover } from './charts/ChartTooltips';

type MetricView = 'ALL' | 'PERFORMANCE' | 'HEART_RATE' | 'SPEED';

/** The three fitness charts of a horse, with one switch to show them all or one at a time. */
export function FitnessTrendChart({
  trend,
  readiness,
  rules,
}: {
  trend: HorseFitnessTrendItem[];
  readiness: ReadinessAssessment[];
  rules: TrainingRules | null;
}) {
  const [view, setView] = useState<MetricView>('ALL');
  const [workoutHover, setWorkoutHover] = useState<WorkoutHover | null>(null);
  const [readinessHover, setReadinessHover] = useState<ReadinessHover | null>(null);

  const sorted = useMemo(
    () => [...trend].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()),
    [trend],
  );

  if (sorted.length === 0) {
    return (
      <Panel padded>
        <EmptyState
          icon="activity"
          title="No completed sessions in this date range"
          description="Fitness charts appear once sessions are completed and their metrics are recorded."
        />
      </Panel>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-[var(--color-text-primary)]">Fitness and performance trends</h2>
          <p className="text-xs text-[var(--color-text-secondary)]">
            Track fitness trends across training sessions and periodic assessments.
          </p>
        </div>
        <SegmentedControl
          label="Chart"
          value={view}
          onChange={setView}
          options={[
            { value: 'ALL', label: 'All metrics' },
            { value: 'PERFORMANCE', label: 'Performance and readiness' },
            { value: 'HEART_RATE', label: 'Heart rate' },
            { value: 'SPEED', label: 'Speed' },
          ]}
        />
      </div>

      {(view === 'ALL' || view === 'PERFORMANCE') && (
        <PerformanceChart
          trend={sorted}
          readiness={readiness}
          onHoverWorkout={setWorkoutHover}
          onHoverReadiness={setReadinessHover}
        />
      )}
      {(view === 'ALL' || view === 'HEART_RATE') && (
        <HeartRateChart trend={sorted} rules={rules} onHover={setWorkoutHover} />
      )}
      {(view === 'ALL' || view === 'SPEED') && <SpeedChart trend={sorted} onHover={setWorkoutHover} />}

      {workoutHover && <WorkoutTooltip hover={workoutHover} rules={rules} />}
      {readinessHover && <ReadinessTooltip hover={readinessHover} />}
    </div>
  );
}
