'use client';

import { EmptyState, Icon, Panel, StatusBadge } from '@/components/ui';
import type { HorsePedigree } from '../types';
import { formatDate } from '@/lib/display';

export function HorsePedigreeCard({ pedigree }: { pedigree: HorsePedigree | null }) {
  if (!pedigree) {
    return (
      <Panel padded>
        <EmptyState
          icon="file-text"
          title="No Pedigree Record Yet"
          description="Official ancestral lineage and registration details have not been submitted for this horse."
        />
      </Panel>
    );
  }

  return (
    <div className="space-y-4">
      {/* Registry Information */}
      <Panel padded>
        <div className="flex items-center gap-2 mb-3">
          <Icon name="star" className="text-[var(--color-primary)]" size={18} />
          <h3 className="text-[14px] font-semibold text-[var(--color-text-primary)]">
            Registration & Registry Information
          </h3>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 text-[13px]">
          <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
            <span className="text-[var(--color-text-muted)]">Registry Name:</span>
            <span className="font-medium text-[var(--color-text-primary)]">
              {pedigree.registryName || 'Not specified'}
            </span>
          </div>
          <div className="flex justify-between py-1.5 border-b border-[var(--color-border-subtle)]">
            <span className="text-[var(--color-text-muted)]">Registration Number:</span>
            <span className="font-medium text-[var(--color-text-primary)]">
              {pedigree.registrationNumber || 'Not specified'}
            </span>
          </div>
        </div>

        {pedigree.pedigreeNotes && (
          <div className="mt-3 text-[12px] text-[var(--color-text-secondary)] bg-[var(--color-surface-muted)] p-2.5 rounded">
            <span className="font-semibold block mb-0.5 text-[var(--color-text-primary)]">Pedigree Notes:</span>
            {pedigree.pedigreeNotes}
          </div>
        )}
      </Panel>

      {/* Lineage Tree (Sire & Dam) */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {/* Sire (Father) */}
        <Panel padded className="border-l-4 border-l-[var(--color-primary)]">
          <div className="flex items-center justify-between mb-2.5">
            <div className="flex items-center gap-1.5 text-[12px] font-semibold text-[var(--color-primary)] uppercase tracking-wide">
              <Icon name="external-link" size={14} />
              Sire (Father)
            </div>
            {pedigree.sire?.currentStatus && (
              <StatusBadge status={pedigree.sire.currentStatus as any} size="sm" />
            )}
          </div>

          {pedigree.sire ? (
            <div className="space-y-1.5 text-[13px]">
              <div className="text-[15px] font-bold text-[var(--color-text-primary)]">
                {pedigree.sire.name}
              </div>
              <div className="flex justify-between text-[12px] text-[var(--color-text-secondary)]">
                <span className="text-[var(--color-text-muted)]">Breed:</span>
                <span>{pedigree.sire.breed || 'Unknown'}</span>
              </div>
              <div className="flex justify-between text-[12px] text-[var(--color-text-secondary)]">
                <span className="text-[var(--color-text-muted)]">Date of Birth:</span>
                <span>{formatDate(pedigree.sire.dateOfBirth)}</span>
              </div>
            </div>
          ) : (
            <div className="text-[12px] text-[var(--color-text-muted)] italic">
              Sire information not recorded.
            </div>
          )}
        </Panel>

        {/* Dam (Mother) */}
        <Panel padded className="border-l-4 border-l-[var(--color-info)]">
          <div className="flex items-center justify-between mb-2.5">
            <div className="flex items-center gap-1.5 text-[12px] font-semibold text-[var(--color-info)] uppercase tracking-wide">
              <Icon name="external-link" size={14} />
              Dam (Mother)
            </div>
            {pedigree.dam?.currentStatus && (
              <StatusBadge status={pedigree.dam.currentStatus as any} size="sm" />
            )}
          </div>

          {pedigree.dam ? (
            <div className="space-y-1.5 text-[13px]">
              <div className="text-[15px] font-bold text-[var(--color-text-primary)]">
                {pedigree.dam.name}
              </div>
              <div className="flex justify-between text-[12px] text-[var(--color-text-secondary)]">
                <span className="text-[var(--color-text-muted)]">Breed:</span>
                <span>{pedigree.dam.breed || 'Unknown'}</span>
              </div>
              <div className="flex justify-between text-[12px] text-[var(--color-text-secondary)]">
                <span className="text-[var(--color-text-muted)]">Date of Birth:</span>
                <span>{formatDate(pedigree.dam.dateOfBirth)}</span>
              </div>
            </div>
          ) : (
            <div className="text-[12px] text-[var(--color-text-muted)] italic">
              Dam information not recorded.
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}
