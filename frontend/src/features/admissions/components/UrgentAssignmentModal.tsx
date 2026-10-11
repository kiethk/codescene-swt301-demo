'use client';

import type { ReactNode } from 'react';
import { Button, Modal, Notice, Pill, ProtectedImage } from '@/components/ui';
import { formatDateTime } from '@/lib/display';
import type { UrgentAssignmentAlert } from '../types';

interface Props {
  alert: UrgentAssignmentAlert | null;
  onOpenCase: (alert: UrgentAssignmentAlert) => void;
}

function Detail({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  return (
    <div className={className}>
      <dt className="text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-[var(--color-text-primary)]">{children}</dd>
    </div>
  );
}

export function UrgentAssignmentModal({ alert, onOpenCase }: Props) {
  if (!alert) return null;

  return (
    <Modal
      open
      // The assignment is automatic and must be acknowledged by opening the case.
      dismissible={false}
      onClose={() => undefined}
      size="lg"
      tone="danger"
      title="Urgent case assigned"
      description="You have been assigned to this case automatically; no confirmation is required. The horse is blocked from training until the examination is complete."
      footer={(
        <Button variant="destructive" iconRight="chevron-right" className="w-full" onClick={() => onOpenCase(alert)}>
          Open urgent case
        </Button>
      )}
    >
      <div className="space-y-4">
        <dl className="grid gap-4 rounded-[var(--radius-md)] bg-[var(--color-surface-muted)] p-4 sm:grid-cols-2">
          <Detail label="Horse">{alert.horseName} · #{alert.horseId}</Detail>
          <Detail label="Severity"><Pill tone="danger">{alert.severity}</Pill></Detail>
          <Detail label="Stall / location">
            {[alert.stallCode, alert.stableLocation].filter(Boolean).join(' · ') || 'See report'}
          </Detail>
          <Detail label="Reported by">{alert.reportedByName || 'Unknown'} · #{alert.reportedById}</Detail>
          <Detail label="Reported at" className="sm:col-span-2">{formatDateTime(alert.reportedAt)}</Detail>
        </dl>

        <Notice tone="error" title={`Training decision: ${alert.trainingDecision}`}>
          Training is paused until the examination is complete.
        </Notice>

        <section className="rounded-[var(--radius-md)] border border-[var(--color-border)] p-4">
          <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">{alert.title}</h3>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-relaxed text-[var(--color-text-secondary)]">
            {alert.description}
          </p>
        </section>

        {alert.imageUrl && (
          <ProtectedImage
            src={alert.imageUrl}
            alt="Urgent case report"
            openOnClick
            className="max-h-72 w-full object-contain"
          />
        )}
      </div>
    </Modal>
  );
}
