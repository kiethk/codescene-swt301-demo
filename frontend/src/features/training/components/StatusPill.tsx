import { Pill } from '@/components/ui';
import type { StatusStyle } from '../lib/status';

/** A plan, workout or lot status drawn with the shared Pill, using the tone map from lib/status. */
export function StatusPill({ status, size = 'sm' }: { status: StatusStyle; size?: 'sm' | 'md' }) {
  return <Pill tone={status.tone} size={size}>{status.label}</Pill>;
}
