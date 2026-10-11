import type { ReactNode } from 'react';
import { EmptyState, Panel, Pill } from '@/components/ui';
import { cn } from '@/lib/cn';
import type { Area, Horse, StableStall } from '../../types';
import { StallCell } from './StallCell';

/**
 * Who is looking at the map. The plan itself is identical for every role; the lens only says how the
 * legend is worded. What a role shows on each cell comes in through `cellFooter`.
 * Extension points not built yet: 'vet' (health status per stall) and 'manager' (groom assignment).
 */
export type StableLens = 'groom' | 'trainer';

interface StableFloorPlanProps {
  lens: StableLens;
  areas: Area[];
  stalls: StableStall[];
  horseByStallId: ReadonlyMap<number, Horse>;
  selectedStallId: number | null;
  onSelectStall: (stall: StableStall) => void;
  /** Stalls in the viewer's own scope. When given, every other stall is faded. */
  ownStallIds?: ReadonlySet<number>;
  /** The lens decoration under the horse name. */
  cellFooter?: (stall: StableStall, horse: Horse | undefined) => ReactNode;
  emptyTitle?: string;
  emptyDescription?: string;
}

const OWN_LABEL: Record<StableLens, string> = {
  groom: 'Your stalls',
  trainer: 'Your block',
};

function LegendItem({ dot, children }: { dot: string; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn('h-2 w-2 rounded-full', dot)} />
      {children}
    </span>
  );
}

function StallRow({
  stalls,
  startIndex,
  props,
}: {
  stalls: StableStall[];
  startIndex: number;
  props: StableFloorPlanProps;
}) {
  return (
    <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
      {stalls.map((stall, index) => {
        const horse = props.horseByStallId.get(stall.id);
        return (
          <StallCell
            key={stall.id}
            stall={stall}
            horse={horse}
            selected={props.selectedStallId === stall.id}
            dimmed={props.ownStallIds ? !props.ownStallIds.has(stall.id) : false}
            footer={props.cellFooter?.(stall, horse)}
            onSelect={props.onSelectStall}
            // Short stagger so the block draws in as one sweep; capped so a large barn does not feel slow.
            style={{ animationDelay: `${Math.min(startIndex + index, 14) * 25}ms` }}
          />
        );
      })}
    </div>
  );
}

/**
 * The stable drawn as a floor plan: each area is a barn with two rows of stalls facing a centre aisle.
 * Regular areas come first, quarantine areas last and visually set apart. Shared by every role.
 */
export function StableFloorPlan(props: StableFloorPlanProps) {
  const { lens, areas, stalls, horseByStallId, ownStallIds } = props;

  // The API returns areas in no particular order: regular before quarantine, then by code (A before B).
  const orderedAreas = [...areas].sort(
    (a, b) =>
      Number(a.type === 'QUARANTINE') - Number(b.type === 'QUARANTINE') ||
      a.code.localeCompare(b.code, 'en', { numeric: true }),
  );

  if (orderedAreas.length === 0) {
    return (
      <Panel padded>
        <EmptyState
          icon="building"
          title={props.emptyTitle ?? 'No stable areas to show'}
          description={props.emptyDescription}
        />
      </Panel>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[var(--color-text-secondary)]">
        <LegendItem dot="bg-[var(--color-success)]">Occupied</LegendItem>
        <LegendItem dot="bg-[var(--color-danger)]">Training blocked</LegendItem>
        <LegendItem dot="bg-[var(--color-warning)]">Maintenance</LegendItem>
        <LegendItem dot="bg-[var(--color-border-strong)]">Empty</LegendItem>
        {ownStallIds && (
          <span className="text-[var(--color-text-muted)]">
            {OWN_LABEL[lens]} are shown at full strength; the rest are faded.
          </span>
        )}
      </div>

      {orderedAreas.map((area) => {
        const areaStalls = stalls
          .filter((stall) => stall.areaId === area.id)
          .sort((a, b) => a.stallNumber - b.stallNumber);
        const half = Math.ceil(areaStalls.length / 2);
        const occupied = areaStalls.filter((stall) => horseByStallId.has(stall.id)).length;
        const quarantine = area.type === 'QUARANTINE';

        return (
          <Panel key={area.id} padded tone={quarantine ? 'warning' : 'default'}>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <h2 className="text-base font-semibold text-[var(--color-text-primary)]">{area.name}</h2>
                {quarantine && <Pill tone="warning" size="sm" icon="shield">Quarantine</Pill>}
              </div>
              <span className="text-xs text-[var(--color-text-muted)]">
                {occupied} of {areaStalls.length} stalls occupied
              </span>
            </div>

            {areaStalls.length === 0 ? (
              <p className="text-xs italic text-[var(--color-text-muted)]">This area has no stalls yet.</p>
            ) : (
              <div className="space-y-2">
                <StallRow stalls={areaStalls.slice(0, half)} startIndex={0} props={props} />
                {areaStalls.length > half && (
                  <>
                    <div
                      aria-hidden="true"
                      className="flex h-6 items-center justify-center rounded-[var(--radius-sm)] bg-[var(--color-surface-muted)] text-xs uppercase tracking-widest text-[var(--color-text-muted)]"
                    >
                      Aisle
                    </div>
                    <StallRow stalls={areaStalls.slice(half)} startIndex={half} props={props} />
                  </>
                )}
              </div>
            )}
          </Panel>
        );
      })}
    </div>
  );
}
