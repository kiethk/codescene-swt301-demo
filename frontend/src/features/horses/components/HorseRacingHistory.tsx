'use client';

import { EmptyState, Icon, Panel } from '@/components/ui';
import { RaceStatusPill } from '@/features/racing/shared/components/RaceStatusPill';
import type { RaceRegistrationResponse } from '@/features/racing/types';
import { formatDate } from '@/lib/display';

export function HorseRacingHistory({
  registrations,
}: {
  registrations: RaceRegistrationResponse[];
}) {
  if (!registrations || registrations.length === 0) {
    return (
      <Panel padded>
        <EmptyState
          icon="flag"
          title="No Race Records Yet"
          description="This horse has not been nominated for or participated in any official races."
        />
      </Panel>
    );
  }

  return (
    <div className="space-y-3">
      {registrations.map((race) => (
        <Panel
          key={race.id}
          padded
          className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between transition-colors hover:border-[var(--color-border-hover)]"
        >
          <div className="space-y-1 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-[14px] font-semibold text-[var(--color-text-primary)]">
                {race.raceName}
              </span>
              <RaceStatusPill status={race.status} size="sm" />
              {race.raceCategory && (
                <span className="rounded bg-[var(--color-surface-muted)] px-1.5 py-0.5 text-[11px] font-medium text-[var(--color-text-muted)]">
                  {race.raceCategory}
                </span>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-[var(--color-text-muted)]">
              {race.location && (
                <span className="flex items-center gap-1">
                  <Icon name="building" size={12} />
                  {race.location}
                </span>
              )}
              {race.eventDate && (
                <span className="flex items-center gap-1">
                  <Icon name="calendar" size={12} />
                  {formatDate(race.eventDate)} {race.eventTime ? `at ${race.eventTime}` : ''}
                </span>
              )}
              {race.distanceMeters && (
                <span className="flex items-center gap-1">
                  <Icon name="target" size={12} />
                  {race.distanceMeters}m {race.trackType ? `(${race.trackType})` : ''}
                </span>
              )}
            </div>

            {race.selectionReason && (
              <p className="text-[12px] text-[var(--color-text-secondary)] mt-1">
                <span className="font-medium text-[var(--color-text-muted)]">Entry Reason: </span>
                {race.selectionReason}
              </p>
            )}

            {race.trainerNotes && (
              <p className="text-[12px] text-[var(--color-text-secondary)] italic">
                <span className="font-medium not-italic text-[var(--color-text-muted)]">Trainer Notes: </span>
                &ldquo;{race.trainerNotes}&rdquo;
              </p>
            )}
          </div>

          {race.prizeDetails && (
            <div className="text-right sm:border-l sm:border-[var(--color-border-subtle)] sm:pl-4">
              <span className="text-[11px] text-[var(--color-text-muted)] block">Prize Purse</span>
              <span className="text-[13px] font-semibold text-[var(--color-primary)]">
                {race.prizeDetails}
              </span>
            </div>
          )}
        </Panel>
      ))}
    </div>
  );
}
