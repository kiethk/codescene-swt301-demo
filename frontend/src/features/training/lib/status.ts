import type { PlanStatus, WorkoutStatus } from '../types';

export type StatusTone = 'primary' | 'success' | 'info' | 'warning' | 'danger' | 'neutral';

export interface StatusStyle {
  label: string;
  tone: StatusTone;
}

/** One status-to-tone map for training plans, so the list, the detail and the dashboard agree. */
export const PLAN_STATUS: Record<PlanStatus | 'NONE', StatusStyle> = {
  UPCOMING: { label: 'Upcoming', tone: 'info' },
  ACTIVE: { label: 'In training', tone: 'primary' },
  COMPLETED: { label: 'Completed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
  NONE: { label: 'No plan', tone: 'neutral' },
};

export const WORKOUT_STATUS: Record<WorkoutStatus, StatusStyle> = {
  SCHEDULED: { label: 'Scheduled', tone: 'info' },
  COMPLETED: { label: 'Completed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
};

/** Free slots left in a lot decide how urgent it looks: full is red, nearly full is amber. */
export function lotFillTone(remainingSlots: number, nearlyFullAt = 2): 'danger' | 'warning' | 'success' {
  if (remainingSlots === 0) return 'danger';
  if (remainingSlots <= nearlyFullAt) return 'warning';
  return 'success';
}
