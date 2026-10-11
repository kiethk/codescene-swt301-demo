'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  ConfirmDialog,
  EmptyState,
  FormField,
  Input,
  ListSkeleton,
  Notice,
  PageHeader,
  PageSection,
  Panel,
  Pill,
  ScreenLayout,
} from '@/components/ui';
import { cn } from '@/lib/cn';
import { displayError, formatDate, formatTime } from '@/lib/display';
import { toast } from '@/lib/toast';
import { useTrainingRules } from '../hooks/useTrainingRules';
import { toIsoDate } from '../lib/dateRange';
import { lotFillTone } from '../lib/status';
import { hourMarks, stripPosition } from '../lib/timeline';
import { trainingService } from '../services/trainingService';
import type { TrainingLotResponse, TrainingRules } from '../types';
import { RescheduleDialog } from './RescheduleDialog';

const STRIP_TONE = {
  danger: 'border-[var(--color-danger)] bg-[var(--color-danger-soft)] text-[var(--color-danger)]',
  warning: 'border-[var(--color-warning)] bg-[var(--color-warning-soft)] text-[var(--color-warning)]',
  success: 'border-[var(--color-success)] bg-[var(--color-success-soft)] text-[var(--color-success)]',
} as const;

/** The golden-hour window as a strip, with every lot drawn at its time. The window itself comes from the backend. */
function TimelineStrip({ lots, rules, date }: { lots: TrainingLotResponse[]; rules: TrainingRules; date: string }) {
  const usedMinutes = lots.reduce((sum, lot) => sum + (lot.durationMinutes || 0), 0);
  const start = rules.goldenHoursStart;
  const end = rules.goldenHoursEnd;

  return (
    <Panel padded className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-semibold text-[var(--color-text-primary)]">
          Schedule window {formatTime(start)}–{formatTime(end)} ({formatDate(date)})
        </span>
        <span className="text-xs text-[var(--color-text-secondary)]">
          Used: <strong>{usedMinutes}/{rules.goldenWindowMinutes} minutes</strong> · Available:{' '}
          <strong>{Math.max(0, rules.goldenWindowMinutes - usedMinutes)} minutes</strong>
        </span>
      </div>

      <div className="relative h-5 border-b border-[var(--color-border)] text-xs text-[var(--color-text-muted)]" aria-hidden="true">
        {hourMarks(start, end).map((mark) => (
          <span
            key={mark.label}
            className="absolute -translate-x-1/2 first:translate-x-0 last:-translate-x-full"
            style={{ left: `${mark.percent}%` }}
          >
            {mark.label}
          </span>
        ))}
      </div>

      <div className="relative h-16 w-full overflow-hidden rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-muted)]">
        {lots.map((lot) => {
          const { leftPercent, widthPercent } = stripPosition(lot.startTime, lot.endTime, start, end);
          return (
            <div
              key={lot.lotId}
              style={{ left: `${leftPercent}%`, width: `${widthPercent}%` }}
              className={cn(
                'absolute bottom-1 top-1 flex flex-col justify-center overflow-hidden rounded-[var(--radius-sm)] border px-2',
                STRIP_TONE[lotFillTone(lot.remainingSlots)],
              )}
              title={`Lot #${lot.lotId}: ${lot.subjectName} (${formatTime(lot.startTime)}–${formatTime(lot.endTime)}), ${lot.occupied}/${lot.maxCapacity} horses`}
            >
              <span className="truncate text-xs font-semibold">#{lot.lotId} {lot.subjectName}</span>
              <span className="truncate text-xs">
                {formatTime(lot.startTime)}–{formatTime(lot.endTime)} ({lot.occupied}/{lot.maxCapacity})
              </span>
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

function LotCard({
  lot,
  onReschedule,
  onCancel,
}: {
  lot: TrainingLotResponse;
  onReschedule: () => void;
  onCancel: () => void;
}) {
  return (
    <Panel padded className="flex flex-col justify-between gap-4">
      <div className="space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h3 className="text-base font-semibold text-[var(--color-text-primary)]">Lot #{lot.lotId} · {lot.subjectName}</h3>
            <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
              <strong>{formatTime(lot.startTime)} – {formatTime(lot.endTime)}</strong> ({lot.durationMinutes} min)
            </p>
          </div>
          <Pill tone={lotFillTone(lot.remainingSlots)} size="sm">
            {lot.occupied}/{lot.maxCapacity} horses{lot.remainingSlots === 0 ? ' (full)' : ''}
          </Pill>
        </div>

        <div className="space-y-1.5 border-t border-[var(--color-border)] pt-2">
          <p className="text-xs text-[var(--color-text-muted)]">Participating horses ({lot.horseNames.length})</p>
          {lot.horseNames.length === 0 ? (
            <p className="text-xs text-[var(--color-text-muted)]">No horses</p>
          ) : (
            <ul className="flex flex-wrap gap-1.5">
              {lot.horseNames.map((name) => (
                <li key={name}><Pill tone="neutral" size="sm" icon="horse">{name}</Pill></li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="flex justify-end gap-2 border-t border-[var(--color-border)] pt-3">
        <Button size="sm" onClick={onReschedule}>Reschedule</Button>
        <Button size="sm" variant="destructive" onClick={onCancel}>Cancel lot</Button>
      </div>
    </Panel>
  );
}

export function LotTimeline() {
  const rules = useTrainingRules();

  const [date, setDate] = useState(() => toIsoDate(new Date()));
  const [lots, setLots] = useState<TrainingLotResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [rescheduleTarget, setRescheduleTarget] = useState<TrainingLotResponse | null>(null);
  const [cancelTarget, setCancelTarget] = useState<TrainingLotResponse | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setLots(await trainingService.getLots(date, date, false));
    } catch (cause) {
      setError(displayError(cause, 'Unable to load training lots for the selected date.'));
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const sortedLots = useMemo(() => [...lots].sort((a, b) => a.startTime.localeCompare(b.startTime)), [lots]);

  async function confirmCancel() {
    if (!cancelTarget) return;
    setCancelling(true);
    try {
      await trainingService.cancelLot(cancelTarget.lotId);
      toast.success(`Lot #${cancelTarget.lotId} cancelled.`);
      setCancelTarget(null);
      await load();
    } catch (cause) {
      toast.error(displayError(cause, 'Unable to cancel this lot.'));
    } finally {
      setCancelling(false);
    }
  }

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Daily training schedule"
        description={
          rules
            ? `View training lots between ${formatTime(rules.goldenHoursStart)} and ${formatTime(rules.goldenHoursEnd)} and identify available time slots.`
            : 'View the training lots of a day and identify available time slots.'
        }
        actions={
          <>
            <FormField label="Date" className="w-40">
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </FormField>
            <Button icon="refresh" onClick={load} loading={loading} className="self-end">Refresh</Button>
          </>
        }
      />

      {error ? (
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Unable to load the schedule"
            description={error}
            action={<Button size="sm" icon="refresh" onClick={load}>Try again</Button>}
          />
        </Panel>
      ) : loading && lots.length === 0 ? (
        <Panel padded><ListSkeleton rows={4} /></Panel>
      ) : (
        <>
          {rules && <TimelineStrip lots={sortedLots} rules={rules} date={date} />}

          <PageSection title={`Lots for this day (${sortedLots.length})`}>
            {sortedLots.length === 0 ? (
              <Panel padded>
                <EmptyState
                  icon="calendar"
                  title="No lots scheduled"
                  description="There are no lots scheduled on this day."
                />
              </Panel>
            ) : (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {sortedLots.map((lot) => (
                  <LotCard
                    key={lot.lotId}
                    lot={lot}
                    onReschedule={() => setRescheduleTarget(lot)}
                    onCancel={() => setCancelTarget(lot)}
                  />
                ))}
              </div>
            )}
          </PageSection>
        </>
      )}

      {!rules && !loading && !error && (
        <Notice tone="info">The schedule window could not be loaded, so the timeline strip is hidden.</Notice>
      )}

      <RescheduleDialog
        key={rescheduleTarget?.lotId ?? 'none'}
        lot={rescheduleTarget}
        open={rescheduleTarget !== null}
        rules={rules}
        onClose={() => setRescheduleTarget(null)}
        onSuccess={load}
      />

      <ConfirmDialog
        open={cancelTarget !== null}
        title={`Cancel lot #${cancelTarget?.lotId} (${cancelTarget?.subjectName})?`}
        description={
          <>
            All <strong>{cancelTarget?.occupied} horse sessions</strong> in this lot will also be cancelled. The{' '}
            {cancelTarget && `${formatTime(cancelTarget.startTime)}–${formatTime(cancelTarget.endTime)}`} time slot will be released.
          </>
        }
        tone="danger"
        confirmLabel="Confirm cancellation"
        loading={cancelling}
        onConfirm={confirmCancel}
        onCancel={() => setCancelTarget(null)}
      />
    </ScreenLayout>
  );
}
