import { FormField, LinkButton, ListSkeleton, Notice, Panel, SectionTitle, Select, Textarea } from '@/components/ui';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Horse } from '@/features/stable/types';
import type { RaceFormValues } from '../../lib/raceForm';

/**
 * Part 1 of the nomination: which horse and why. A horse the vet blocked from training stays in the list but cannot
 * be picked; the backend refuses it as well, so this is only a clearer way to say no.
 */
export function HorseSection({
  horses,
  loading,
  loadError,
  values,
  errors,
  specialty,
  onChange,
}: {
  horses: Horse[];
  loading: boolean;
  loadError: string | null;
  values: RaceFormValues;
  errors: Partial<Record<keyof RaceFormValues, string>>;
  specialty: HorseSpecialty | null;
  onChange: (field: keyof RaceFormValues, value: string) => void;
}) {
  const selected = horses.find((horse) => String(horse.id) === values.horseId);

  return (
    <Panel padded className="space-y-4">
      <div>
        <SectionTitle>1. Nominated horse</SectionTitle>
        <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
          Only eligible horses in your area that are not blocked from training can be nominated.
        </p>
      </div>

      {loading ? (
        <ListSkeleton rows={2} />
      ) : loadError ? (
        <Notice tone="error">{loadError}</Notice>
      ) : horses.length === 0 ? (
        <Notice tone="warning" title="No horses are currently available for nomination">
          <p className="mt-1">Horses must be assigned to a stall in your area, be eligible, and not be blocked from training.</p>
          <div className="mt-3 flex gap-2">
            <LinkButton href="/trainer/stable" size="sm">View stable</LinkButton>
            <LinkButton href="/trainer/horses" size="sm">View horses</LinkButton>
          </div>
        </Notice>
      ) : (
        <>
          <FormField label="Select a horse" required error={errors.horseId}>
            <Select value={values.horseId} onChange={(e) => onChange('horseId', e.target.value)}>
              <option value="">Select an assigned horse</option>
              {horses.map((horse) => (
                <option key={horse.id} value={horse.id} disabled={horse.trainingDecision === 'BLOCKED'}>
                  {horse.name}
                  {horse.registrationNumber ? ` (${horse.registrationNumber})` : ''}
                  {horse.breed ? ` · ${horse.breed}` : ''}
                  {horse.trainingDecision === 'BLOCKED' ? ' — training blocked' : ''}
                </option>
              ))}
            </Select>
          </FormField>

          {selected && (
            <div className="space-y-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3 text-xs text-[var(--color-text-secondary)]">
              <div className="flex flex-wrap items-center gap-2">
                <strong className="text-[var(--color-text-primary)]">{selected.name}</strong>
                {selected.registrationNumber && <span className="font-metric">({selected.registrationNumber})</span>}
                {selected.breed && <span>{selected.breed}</span>}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span>Racing specialty</span>
                <HorseSpecialtyBadges specialty={specialty} />
              </div>
            </div>
          )}

          <FormField label="Nomination reason" required error={errors.selectionReason}>
            <Textarea
              rows={3}
              placeholder="Describe the horse’s fitness, preferred distance, current form, and suitability for this event…"
              value={values.selectionReason}
              onChange={(e) => onChange('selectionReason', e.target.value)}
            />
          </FormField>
        </>
      )}
    </Panel>
  );
}
