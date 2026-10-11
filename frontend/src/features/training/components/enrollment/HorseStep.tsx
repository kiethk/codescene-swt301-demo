import { Button, ChoiceInput, Panel, Pill, SectionTitle } from '@/components/ui';
import type { Horse } from '@/features/stable/types';
import { cn } from '@/lib/cn';

/**
 * Step 2: pick the horses of the group. A horse the vet blocked from training is shown but cannot be selected,
 * with the vet's reason; the backend makes the final call either way.
 */
export function HorseStep({
  horses,
  selectedIds,
  onToggle,
  onToggleAll,
}: {
  horses: Horse[];
  selectedIds: number[];
  onToggle: (horseId: number) => void;
  onToggleAll: (horseIds: number[]) => void;
}) {
  const selectable = horses.filter((horse) => horse.trainingDecision !== 'BLOCKED');
  const allSelected = selectable.length > 0 && selectedIds.length === selectable.length;

  return (
    <Panel padded className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <SectionTitle>2. Select horses for the group ({selectedIds.length} selected)</SectionTitle>
          <p className="text-xs text-[var(--color-text-secondary)]">Only eligible horses assigned to your area are shown.</p>
        </div>
        {selectable.length > 0 && (
          <Button size="sm" type="button" onClick={() => onToggleAll(allSelected ? [] : selectable.map((horse) => horse.id))}>
            {allSelected ? 'Clear selection' : 'Select all'}
          </Button>
        )}
      </div>

      {horses.length === 0 ? (
        <p className="text-xs text-[var(--color-text-muted)]">
          No eligible horses are assigned to your area. Complete admission or assign horses to stalls first.
        </p>
      ) : (
        <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2 md:grid-cols-3">
          {horses.map((horse) => {
            const blocked = horse.trainingDecision === 'BLOCKED';
            const selected = selectedIds.includes(horse.id);
            return (
              <li key={horse.id}>
                <label
                  className={cn(
                    'flex items-center gap-2.5 rounded-[var(--radius-md)] border p-2.5 text-xs transition-colors duration-[var(--duration-fast)]',
                    blocked && 'cursor-not-allowed opacity-70',
                    !blocked && 'cursor-pointer hover:bg-[var(--color-surface-muted)]',
                    selected ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)]' : 'border-[var(--color-border)]',
                  )}
                >
                  <ChoiceInput type="checkbox" checked={selected} disabled={blocked} onChange={() => onToggle(horse.id)} />
                  <div className="min-w-0 space-y-0.5">
                    <div className="truncate font-semibold text-[var(--color-text-primary)]">{horse.name}</div>
                    <div className="text-[var(--color-text-muted)]">{horse.breed || 'Breed unknown'}</div>
                    {blocked && (
                      <Pill tone="danger" size="sm" icon="lock">Training blocked</Pill>
                    )}
                    {blocked && horse.trainingDecisionReason && (
                      <div className="text-[var(--color-danger)]">{horse.trainingDecisionReason}</div>
                    )}
                  </div>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}
