import { Pill } from '@/components/ui';
import { RACE_STATUS } from '../../lib/status';
import type { RaceRegistrationStatus } from '../../types';

export function RaceStatusPill({ status, size = 'sm' }: { status: RaceRegistrationStatus; size?: 'sm' | 'md' }) {
  const style = RACE_STATUS[status];
  return <Pill tone={style.tone} size={size}>{style.label}</Pill>;
}
