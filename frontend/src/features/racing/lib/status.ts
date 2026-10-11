import type { RaceRegistrationStatus } from '../types';

export interface RaceStatusStyle {
  label: string;
  tone: 'warning' | 'success' | 'danger';
}

/** One status-to-tone map for race nominations, shared by every screen that shows one. */
export const RACE_STATUS: Record<RaceRegistrationStatus, RaceStatusStyle> = {
  PENDING: { label: 'Pending', tone: 'warning' },
  APPROVED: { label: 'Approved', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
};
