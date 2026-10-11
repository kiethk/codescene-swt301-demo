'use client';

import { useState } from 'react';
import { Button, FormField, Input, Modal, Notice } from '@/components/ui';
import { displayError, formatTime } from '@/lib/display';
import { toast } from '@/lib/toast';
import { minutesOf } from '../lib/timeline';
import { trainingService } from '../services/trainingService';
import type { TrainingLotResponse, TrainingRules } from '../types';

const FORM_ID = 'reschedule-lot-form';

function addMinutes(time: string, minutes: number): string {
  const total = minutesOf(time) + minutes;
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

/**
 * Moves a whole lot to a new start time. The golden-hour window is shown as a hint (from the backend rules) but
 * is NOT checked here: the backend validates the move and its message is shown when it refuses.
 */
export function RescheduleDialog({
  lot,
  open,
  rules,
  onClose,
  onSuccess,
}: {
  lot: TrainingLotResponse | null;
  open: boolean;
  rules: TrainingRules | null;
  onClose: () => void;
  onSuccess: () => Promise<void> | void;
}) {
  const [startTime, setStartTime] = useState(lot ? lot.startTime.slice(0, 5) : '');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open || !lot) return null;
  const target = lot;

  const duration = target.durationMinutes || 60;
  const windowHint = rules ? ` Lots run between ${formatTime(rules.goldenHoursStart)} and ${formatTime(rules.goldenHoursEnd)}.` : '';

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await trainingService.rescheduleLot(target.lotId, {
        newStartTime: `${startTime}:00`,
        reason: reason.trim() || undefined,
      });
      toast.success(`Lot #${target.lotId} rescheduled.`);
      await onSuccess();
      onClose();
    } catch (cause) {
      setError(displayError(cause, 'Unable to reschedule this lot.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      dismissible={!submitting}
      title={`Reschedule lot #${target.lotId} (${target.subjectName})`}
      description={`All ${target.occupied} horses in this lot will move to the new time slot.`}
      footer={
        <>
          <Button onClick={onClose} disabled={submitting}>Cancel</Button>
          <Button variant="primary" type="submit" form={FORM_ID} loading={submitting}>Confirm reschedule</Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={handleSubmit} className="space-y-4">
        {error && <Notice tone="error">{error}</Notice>}

        <dl className="space-y-1 rounded-[var(--radius-md)] bg-[var(--color-surface-muted)] p-3 text-xs">
          <div className="flex gap-1"><dt>Session duration:</dt><dd className="font-semibold">{duration} minutes</dd></div>
          <div className="flex gap-1"><dt>Current time:</dt><dd className="font-semibold">{formatTime(target.startTime)} – {formatTime(target.endTime)}</dd></div>
        </dl>

        <FormField
          label="New start time"
          required
          hint={`Estimated end time: ${startTime ? addMinutes(startTime, duration) : '—'}.${windowHint}`}
        >
          <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
        </FormField>

        <FormField label="Reason for rescheduling">
          <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="For example: weather conditions, track maintenance…" />
        </FormField>
      </form>
    </Modal>
  );
}
