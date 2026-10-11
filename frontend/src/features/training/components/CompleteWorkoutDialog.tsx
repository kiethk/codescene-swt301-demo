'use client';

import { useState } from 'react';
import { Button, FormField, Input, Modal, Notice, Textarea } from '@/components/ui';
import { displayError, formatDate, formatTime } from '@/lib/display';
import { toast } from '@/lib/toast';
import { trainingService } from '../services/trainingService';
import type { PlanWorkoutItemResponse, CompleteWorkoutRequest } from '../types';

const FORM_ID = 'complete-workout-form';

interface CompleteWorkoutDialogProps {
  workout: PlanWorkoutItemResponse | null;
  open: boolean;
  onClose: () => void;
  onSuccess: () => Promise<void> | void;
}

export function CompleteWorkoutDialog({
  workout,
  open,
  onClose,
  onSuccess,
}: CompleteWorkoutDialogProps) {
  const [actualDistanceMeters, setActualDistanceMeters] = useState<number | ''>('');
  const [actualDurationMinutes, setActualDurationMinutes] = useState<number | ''>('');
  const [topSpeedKmh, setTopSpeedKmh] = useState<number | ''>('');
  const [averageSpeedKmh, setAverageSpeedKmh] = useState<number | ''>('');
  const [averageHeartRate, setAverageHeartRate] = useState<number | ''>('');
  const [maxHeartRate, setMaxHeartRate] = useState<number | ''>('');
  const [recoveryHeartRate, setRecoveryHeartRate] = useState<number | ''>('');
  const [performanceRating, setPerformanceRating] = useState<number>(8);
  const [trainerFeedback, setTrainerFeedback] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open || !workout) return null;
  const target = workout;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (performanceRating < 1 || performanceRating > 10) {
      setError('Performance rating must be between 1 and 10.');
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const payload: CompleteWorkoutRequest = {
        actualDistanceMeters: actualDistanceMeters !== '' ? Number(actualDistanceMeters) : null,
        actualDurationMinutes: actualDurationMinutes !== '' ? Number(actualDurationMinutes) : null,
        topSpeedKmh: topSpeedKmh !== '' ? Number(topSpeedKmh) : null,
        averageSpeedKmh: averageSpeedKmh !== '' ? Number(averageSpeedKmh) : null,
        averageHeartRate: averageHeartRate !== '' ? Number(averageHeartRate) : null,
        maxHeartRate: maxHeartRate !== '' ? Number(maxHeartRate) : null,
        recoveryHeartRate: recoveryHeartRate !== '' ? Number(recoveryHeartRate) : null,
        performanceRating: Number(performanceRating),
        trainerFeedback: trainerFeedback.trim() || null,
      };

      await trainingService.completeWorkout(target.workoutId, payload);
      toast.success('Workout results recorded.');
      await onSuccess();
      onClose();
    } catch (err) {
      setError(displayError(err, 'Unable to complete the workout.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!submitting}
      title={`Record workout results #${workout.workoutId}`}
      description={`${workout.subjectName} · ${formatDate(workout.lotDate)} (${formatTime(workout.startTime)} – ${formatTime(workout.endTime)})`}
      footer={(
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form={FORM_ID} loading={submitting}>
            Complete workout
          </Button>
        </>
      )}
    >
        <form id={FORM_ID} onSubmit={handleSubmit} className="space-y-4">
          {error && <Notice tone="error">{error}</Notice>}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <FormField label="Performance rating (1–10)" required>
                <Input
                  type="number"
                  min="1"
                  max="10"
                  required
                  value={performanceRating}
                  onChange={(e) => setPerformanceRating(Number(e.target.value))}
                />
              </FormField>
            </div>

            <div>
              <FormField label="Actual distance (metres)">
                <Input
                  type="number"
                  min="0"
                  step="10"
                  value={actualDistanceMeters}
                  onChange={(e) => setActualDistanceMeters(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 1200"
                />
              </FormField>
            </div>

            <div>
              <FormField label="Actual duration (minutes)">
                <Input
                  type="number"
                  min="0"
                  step="0.1"
                  value={actualDurationMinutes}
                  onChange={(e) => setActualDurationMinutes(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 4.5"
                />
              </FormField>
            </div>

            <div>
              <FormField label="Top speed (km/h)">
                <Input
                  type="number"
                  min="0"
                  step="0.1"
                  value={topSpeedKmh}
                  onChange={(e) => setTopSpeedKmh(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 58.5"
                />
              </FormField>
            </div>

            <div>
              <FormField label="Average speed (km/h)">
                <Input
                  type="number"
                  min="0"
                  step="0.1"
                  value={averageSpeedKmh}
                  onChange={(e) => setAverageSpeedKmh(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 46.2"
                />
              </FormField>
            </div>

            <div>
              <FormField label="Average heart rate (bpm)">
                <Input
                  type="number"
                  min="0"
                  value={averageHeartRate}
                  onChange={(e) => setAverageHeartRate(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 165"
                />
              </FormField>
            </div>

            <div>
              <FormField label="Maximum heart rate (bpm)">
                <Input
                  type="number"
                  min="0"
                  value={maxHeartRate}
                  onChange={(e) => setMaxHeartRate(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 210"
                />
              </FormField>
            </div>

            <div>
              <FormField label="Recovery heart rate (bpm after 15 min)">
                <Input
                  type="number"
                  min="0"
                  value={recoveryHeartRate}
                  onChange={(e) => setRecoveryHeartRate(e.target.value ? Number(e.target.value) : '')}
                  placeholder="e.g. 88"
                />
              </FormField>
            </div>
          </div>

          <div>
            <FormField label="Trainer feedback">
              <Textarea
                rows={3}
                value={trainerFeedback}
                onChange={(e) => setTrainerFeedback(e.target.value)}
                  placeholder="Notes about gait, acceleration, or endurance…"
              />
            </FormField>
          </div>

        </form>
    </Modal>
  );
}
