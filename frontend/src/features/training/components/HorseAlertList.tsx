import { Icon, Notice, PageSection, Panel, Pill } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatDate } from '@/lib/display';
import type { HorseAlert } from '../types';

/**
 * Alerts the backend raised from the last 30 days of data, not only the latest session. One abnormal session
 * stays here for up to 30 days even after later sessions are normal, so each card says when it was recorded.
 */
export function HorseAlertList({ alerts }: { alerts: HorseAlert[] }) {
  return (
    <PageSection
      title="Fitness and injury risk alerts"
      actions={alerts.length > 0 ? <Pill tone="danger" size="sm">{alerts.length} active alerts</Pill> : undefined}
    >
      {alerts.length === 0 ? (
        <Notice tone="success" title="Fitness indicators are within safe ranges">
          No elevated heart rate, poor recovery or recurring incidents detected.
        </Notice>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {alerts.map((alert, index) => {
            const danger = alert.severity === 'DANGER';
            return (
              <Panel key={`${alert.ruleCode}-${index}`} padded tone={danger ? 'danger' : 'warning'} className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className={cn('flex items-center gap-2', danger ? 'text-[var(--color-danger)]' : 'text-[var(--color-warning)]')}>
                    <Icon name="alert-triangle" size={18} />
                    <h3 className="text-sm font-semibold">{alert.title}</h3>
                  </div>
                  <Pill tone={danger ? 'danger' : 'warning'} size="sm">{danger ? 'Critical' : 'Warning'}</Pill>
                </div>
                <p className="text-xs leading-relaxed text-[var(--color-text-secondary)]">{alert.description}</p>
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--color-border)] pt-2 text-xs text-[var(--color-text-secondary)]">
                  <span>
                    Value: <strong>{alert.metricValue}</strong> / Threshold: <strong>{alert.thresholdValue}</strong>
                  </span>
                  <span>Recorded on <strong>{formatDate(alert.triggeredAt)}</strong></span>
                </div>
              </Panel>
            );
          })}
        </div>
      )}
    </PageSection>
  );
}
