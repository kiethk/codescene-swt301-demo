export interface PlanProgress {
  completed: number;
  /** Sessions that still count: cancelled ones are removed so a plan can reach 100%. */
  total: number;
  cancelled: number;
  percent: number;
}

/**
 * Progress of a plan. Cancelled sessions leave the denominator: a 12-session plan with 1 cancelled reaches 100%
 * at 11 completed sessions instead of hanging at 91% forever.
 */
export function planProgress(completed: number, planned: number, cancelled: number): PlanProgress {
  const total = Math.max(planned - cancelled, 0);
  const percent = total === 0 ? 0 : Math.min(Math.round((completed / total) * 100), 100);
  return { completed, total, cancelled, percent };
}
