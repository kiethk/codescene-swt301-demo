'use client';

import { EmptyState, Icon, MetricCard, Notice, Panel, Pill } from '@/components/ui';
import { HorseAlertList } from '@/features/training/components/HorseAlertList';
import type { HorseAlert } from '@/features/training/types';
import type { HorseHealthMetric } from '../types';
import { formatDate } from '@/lib/display';

export function HorseHealthSummary({
  alerts,
  metrics,
  preventiveCare,
}: {
  alerts: HorseAlert[];
  metrics: HorseHealthMetric[];
  preventiveCare: any[];
}) {
  const latestMetric = metrics[0] || null;

  return (
    <div className="space-y-6">
      {/* Risk and Alerts */}
      <HorseAlertList alerts={alerts} />

      {/* Latest Vitals / Examination Metrics */}
      <div>
        <h3 className="text-[14px] font-semibold text-[var(--color-text-primary)] mb-3 flex items-center gap-1.5">
          <Icon name="activity" size={16} className="text-[var(--color-primary)]" />
          Latest Veterinary Examination Vitals
        </h3>

        {latestMetric ? (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <MetricCard
                label="Heart Rate"
                value={latestMetric.heartRate != null ? `${latestMetric.heartRate} bpm` : 'N/A'}
                icon="heart-pulse"
              />
              <MetricCard
                label="Temperature"
                value={latestMetric.temperature != null ? `${latestMetric.temperature} °C` : 'N/A'}
                icon="activity"
              />
              <MetricCard
                label="Weight"
                value={latestMetric.weight != null ? `${latestMetric.weight} kg` : 'N/A'}
                icon="gauge"
              />
              <MetricCard
                label="Respiratory Rate"
                value={latestMetric.respiratoryRate != null ? `${latestMetric.respiratoryRate} bpm` : 'N/A'}
                icon="trending-up"
              />
            </div>

            <Panel padded className="text-[12px] space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2 text-[var(--color-text-muted)] border-b border-[var(--color-border-subtle)] pb-2">
                <span>Recorded: <strong>{formatDate(latestMetric.recordedAt)}</strong></span>
                {latestMetric.hydrationStatus && (
                  <span>Hydration: <strong className="text-[var(--color-text-primary)]">{latestMetric.hydrationStatus}</strong></span>
                )}
                {latestMetric.bodyConditionScore != null && (
                  <span>BCS: <strong className="text-[var(--color-text-primary)]">{latestMetric.bodyConditionScore}/9</strong></span>
                )}
              </div>
              {latestMetric.notes && (
                <p className="text-[var(--color-text-secondary)] italic">
                  &ldquo;{latestMetric.notes}&rdquo;
                </p>
              )}
            </Panel>
          </div>
        ) : (
          <Panel padded>
            <Notice tone="info" title="No vital measurements recorded">
              No recent veterinary examination vitals have been logged for this horse yet.
            </Notice>
          </Panel>
        )}
      </div>

      {/* Upcoming Preventive Care Schedules */}
      <div>
        <h3 className="text-[14px] font-semibold text-[var(--color-text-primary)] mb-3 flex items-center gap-1.5">
          <Icon name="calendar" size={16} className="text-[var(--color-info)]" />
          Upcoming Preventive Care Schedules
        </h3>

        {preventiveCare && preventiveCare.length > 0 ? (
          <div className="space-y-2">
            {preventiveCare.map((item, idx) => (
              <Panel key={item.id || idx} padded className="flex items-center justify-between gap-3 text-[13px]">
                <div className="flex items-center gap-3">
                  <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-surface-muted)] text-[var(--color-primary)]">
                    <Icon name="shield" size={16} />
                  </span>
                  <div>
                    <span className="font-semibold text-[var(--color-text-primary)] block">
                      {item.careType || item.description || 'Preventive Check'}
                    </span>
                    <span className="text-[12px] text-[var(--color-text-muted)]">
                      Scheduled: {formatDate(item.scheduledDate || item.scheduledAt)}
                    </span>
                  </div>
                </div>
                <Pill
                  tone={item.status === 'COMPLETED' ? 'success' : item.status === 'CANCELLED' ? 'neutral' : 'warning'}
                  size="sm"
                >
                  {item.status || 'SCHEDULED'}
                </Pill>
              </Panel>
            ))}
          </div>
        ) : (
          <Panel padded>
            <EmptyState
              icon="calendar"
              title="No upcoming preventive care schedules"
              description="Routine vaccinations and deworming treatments will appear here when scheduled by the veterinary staff."
            />
          </Panel>
        )}
      </div>
    </div>
  );
}
