export type DatePreset = '7D' | '30D' | '90D' | 'ALL' | 'CUSTOM';

export interface DateRange {
  from?: string;
  to?: string;
}

const PRESET_DAYS: Record<'7D' | '30D' | '90D', number> = { '7D': 7, '30D': 30, '90D': 90 };

/** YYYY-MM-DD in the user's own time zone (toISOString would shift the day for anyone east or west of UTC). */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** The date range for a preset. ALL has no bounds; CUSTOM uses the dates the user typed. */
export function rangeForPreset(preset: DatePreset, today: Date, custom: DateRange = {}): DateRange {
  if (preset === 'ALL') return {};
  if (preset === 'CUSTOM') return { from: custom.from || undefined, to: custom.to || undefined };

  const from = new Date(today);
  from.setDate(from.getDate() - PRESET_DAYS[preset]);
  return { from: toIsoDate(from), to: toIsoDate(today) };
}

/** Tomorrow, as YYYY-MM-DD in the user's time zone: the default first training day. */
export function tomorrowIso(today: Date = new Date()): string {
  const next = new Date(today);
  next.setDate(next.getDate() + 1);
  return toIsoDate(next);
}
