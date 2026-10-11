import { Button, FieldLabel, FormField, Input, Panel, SectionTitle } from '@/components/ui';
import type { TrainingDay } from '../../types';

export const DAYS_OF_WEEK: { value: TrainingDay; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

/** Step 3: first training date, weekdays and an optional note for the plans. */
export function ScheduleStep({
  startDate,
  onStartDateChange,
  trainingDays,
  onToggleDay,
  notes,
  onNotesChange,
}: {
  startDate: string;
  onStartDateChange: (value: string) => void;
  trainingDays: TrainingDay[];
  onToggleDay: (day: TrainingDay) => void;
  notes: string;
  onNotesChange: (value: string) => void;
}) {
  return (
    <Panel padded className="space-y-4">
      <SectionTitle>3. Schedule and training days</SectionTitle>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField label="Start date" required hint="The group’s first session will start on this date.">
          <Input type="date" value={startDate} onChange={(e) => onStartDateChange(e.target.value)} />
        </FormField>

        <div className="space-y-1.5">
          <FieldLabel>Training days *</FieldLabel>
          <div className="flex flex-wrap gap-1.5">
            {DAYS_OF_WEEK.map((day) => {
              const active = trainingDays.includes(day.value);
              return (
                <Button
                  key={day.value}
                  size="sm"
                  type="button"
                  variant={active ? 'primary' : 'secondary'}
                  aria-pressed={active}
                  onClick={() => onToggleDay(day.value)}
                >
                  {day.label}
                </Button>
              );
            })}
          </div>
        </div>
      </div>

      <FormField label="Plan notes">
        <Input value={notes} onChange={(e) => onNotesChange(e.target.value)} placeholder="e.g. Three horses preparing for the autumn racing season…" />
      </FormField>
    </Panel>
  );
}
