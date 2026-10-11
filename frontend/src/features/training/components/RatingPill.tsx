import { Pill } from '@/components/ui';
import { ratingTone } from '../lib/rating';

/** A performance rating (1-10) as a coloured pill; a missing rating shows a dash. */
export function RatingPill({ rating, size = 'sm' }: { rating: number | null | undefined; size?: 'sm' | 'md' }) {
  if (rating == null) return <span className="text-[var(--color-text-muted)]">—</span>;
  return <Pill tone={ratingTone(rating)} size={size}>{rating}/10</Pill>;
}
