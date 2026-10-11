import type { ReactNode } from 'react';
import { Panel, SectionTitle } from '@/components/ui';

/** Titled card of a race nomination screen. */
export function RaceSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Panel padded>
      <SectionTitle>{title}</SectionTitle>
      <dl className="mt-4 space-y-3 text-sm">{children}</dl>
    </Panel>
  );
}

/** One label and value of a nomination; an empty value reads as a dash instead of leaving a hole. */
export function RaceField({ label, children }: { label: string; children?: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-[var(--color-text-secondary)]">{label}</dt>
      <dd className="mt-0.5 font-medium text-[var(--color-text-primary)]">{children || '—'}</dd>
    </div>
  );
}

/** Free text written by the trainer (reasons, notes), kept in its own line breaks. */
export function RaceNote({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="border-t border-[var(--color-border)] pt-3">
      <dt className="text-xs font-medium text-[var(--color-text-secondary)]">{label}</dt>
      <dd className="mt-1 whitespace-pre-wrap rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3 text-[var(--color-text-primary)]">
        {children}
      </dd>
    </div>
  );
}
