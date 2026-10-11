import type { ReactNode } from 'react';
import { EmptyState, HorseAvatar, Panel, Pill, StatusBadge } from '@/components/ui';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Area, Horse, StableStall } from '../../types';
import { stallState } from './StallCell';

interface StallDetailPanelProps {
  stall: StableStall | null;
  area: Area | undefined;
  horse: Horse | undefined;
  /** Null while loading or when the viewer may not read it; the badges then render nothing. */
  specialty?: HorseSpecialty | null;
  /** What the role wants to see about this stall (tasks, incidents, groom…). */
  children?: ReactNode;
  /** What the role can do with this stall. */
  actions?: ReactNode;
  /** Shown before any stall is picked. */
  hint: string;
}

/** The side panel next to the floor plan. Sticks under the top bar while the map scrolls. */
export function StallDetailPanel({ stall, area, horse, specialty, children, actions, hint }: StallDetailPanelProps) {
  if (!stall) {
    return (
      <Panel className="xl:sticky xl:top-[var(--sticky-top)]">
        <EmptyState icon="building" title="Select a stall" description={hint} />
      </Panel>
    );
  }

  const state = stallState(stall, horse);
  const occupancy = state.label === 'Training blocked' ? 'Occupied' : state.label;

  return (
    <Panel
      key={stall.id}
      padded
      className="space-y-4 animate-in fade-in slide-in-from-right-2 duration-200 xl:sticky xl:top-[var(--sticky-top)]"
    >
      <div className="flex items-start justify-between gap-2 border-b border-[var(--color-border)] pb-3">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">Stall {stall.stallCode}</h2>
          <p className="truncate text-xs text-[var(--color-text-muted)]">{area?.name ?? 'Unknown area'}</p>
        </div>
        {/* Occupancy only: whether the horse may train is shown once, with the horse below. */}
        <Pill size="sm" tone={occupancy === 'Occupied' ? 'success' : occupancy === 'Maintenance' ? 'warning' : 'neutral'}>
          {occupancy}
        </Pill>
      </div>

      {horse ? (
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <HorseAvatar name={horse.name} size={44} rounded="md" />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-[var(--color-text-primary)]">{horse.name}</p>
              <p className="truncate text-xs text-[var(--color-text-secondary)]">{horse.breed ?? 'Breed not recorded'}</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-1.5">
            <StatusBadge status={horse.currentStatus} />
            {horse.trainingDecision && (
              <Pill size="sm" tone={horse.trainingDecision === 'BLOCKED' ? 'danger' : 'success'}>
                {horse.trainingDecision === 'BLOCKED' ? 'Training blocked' : 'Training allowed'}
              </Pill>
            )}
          </div>
          {horse.trainingDecision === 'BLOCKED' && horse.trainingDecisionReason && (
            <p className="text-xs text-[var(--color-danger)]">{horse.trainingDecisionReason}</p>
          )}
          <HorseSpecialtyBadges specialty={specialty} />
        </div>
      ) : (
        <p className="text-sm italic text-[var(--color-text-muted)]">No horse is stabled here.</p>
      )}

      {children}

      {actions && <div className="flex flex-col gap-2 border-t border-[var(--color-border)] pt-3">{actions}</div>}
    </Panel>
  );
}
