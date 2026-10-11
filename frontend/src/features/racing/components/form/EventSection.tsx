import { FormField, Input, Panel, SectionTitle } from '@/components/ui';
import type { RaceFormValues } from '../../lib/raceForm';

/** Part 2 of the nomination: the race itself, as the trainer researched it. */
export function EventSection({
  values,
  errors,
  onChange,
}: {
  values: RaceFormValues;
  errors: Partial<Record<keyof RaceFormValues, string>>;
  onChange: (field: keyof RaceFormValues, value: string) => void;
}) {
  return (
    <Panel padded className="space-y-4">
      <div>
        <SectionTitle>2. Race and event details</SectionTitle>
        <p className="mt-1 text-xs text-[var(--color-text-secondary)]">Enter event information based on your external research.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <FormField label="Race or event name" required error={errors.raceName}>
          <Input placeholder="e.g. Autumn Horse Racing Festival 2026" value={values.raceName} onChange={(e) => onChange('raceName', e.target.value)} />
        </FormField>
        <FormField label="Nomination category / stage" required error={errors.raceCategory}>
          <Input placeholder="e.g. 3-year-old, 1,200 m or Open Cup" value={values.raceCategory} onChange={(e) => onChange('raceCategory', e.target.value)} />
        </FormField>
        <FormField label="Racecourse and location" required error={errors.location} className="md:col-span-2">
          <Input placeholder="e.g. Soc Son Racecourse, Hanoi" value={values.location} onChange={(e) => onChange('location', e.target.value)} />
        </FormField>
        <FormField label="Event date" required error={errors.eventDate}>
          <Input type="date" value={values.eventDate} onChange={(e) => onChange('eventDate', e.target.value)} />
        </FormField>
        <FormField label="Expected start time (optional)">
          <Input type="time" value={values.eventTime} onChange={(e) => onChange('eventTime', e.target.value)} />
        </FormField>
        <FormField label="Organizer (optional)">
          <Input placeholder="e.g. National Horse Racing Federation" value={values.organizer} onChange={(e) => onChange('organizer', e.target.value)} />
        </FormField>
        <FormField label="Official nomination deadline (optional)">
          <Input type="date" value={values.nominationDeadline} onChange={(e) => onChange('nominationDeadline', e.target.value)} />
        </FormField>
        <FormField label="Rules or official event URL (optional, for management verification)" className="md:col-span-2">
          <Input type="url" placeholder="https://example.org/race-conditions" value={values.sourceUrl} onChange={(e) => onChange('sourceUrl', e.target.value)} />
        </FormField>
      </div>
    </Panel>
  );
}
