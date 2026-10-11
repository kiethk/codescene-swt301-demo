import { FormField, Input, Panel, SectionTitle, Textarea } from '@/components/ui';
import type { RaceFormValues } from '../../lib/raceForm';

/** Part 3 of the nomination: optional race conditions, prizes and logistics. */
export function ExtraInfoSection({
  values,
  onChange,
}: {
  values: RaceFormValues;
  onChange: (field: keyof RaceFormValues, value: string) => void;
}) {
  return (
    <Panel padded className="space-y-4">
      <div>
        <SectionTitle>3. Additional event information</SectionTitle>
        <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
          Add race details, prize information, or logistics and documentation notes.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <FormField label="Distance (metres) (optional)">
          <Input type="number" min={1} placeholder="e.g. 1200" value={values.distanceMeters} onChange={(e) => onChange('distanceMeters', e.target.value)} />
        </FormField>
        <FormField label="Track surface (optional)">
          <Input placeholder="e.g. Turf, dirt, synthetic" value={values.trackType} onChange={(e) => onChange('trackType', e.target.value)} />
        </FormField>
        <FormField label="Prize structure (optional)" className="md:col-span-2">
          <Textarea
            rows={2}
            placeholder="e.g. 1st: VND 50 million; 2nd: VND 25 million; 3rd: VND 10 million"
            value={values.prizeDetails}
            onChange={(e) => onChange('prizeDetails', e.target.value)}
          />
        </FormField>
        <FormField label="Trainer notes (optional)" className="md:col-span-2">
          <Textarea
            rows={2}
            placeholder="Age or gender requirements, handicap, veterinary checks, or transport arrangements…"
            value={values.trainerNotes}
            onChange={(e) => onChange('trainerNotes', e.target.value)}
          />
        </FormField>
      </div>
    </Panel>
  );
}
