/**
 * Display tiers for a performance rating (1-10). One place, so the dashboard, the horse cards, the plan lists and
 * the history table colour the same rating the same way. This is a display convention, not a business rule.
 */
export type RatingTier = 'excellent' | 'good' | 'needsImprovement';
export type RatingTone = 'success' | 'info' | 'warning';

const EXCELLENT_FROM = 8;
const GOOD_FROM = 6;

export function ratingTier(rating: number): RatingTier {
  if (rating >= EXCELLENT_FROM) return 'excellent';
  if (rating >= GOOD_FROM) return 'good';
  return 'needsImprovement';
}

const TIER_TONE: Record<RatingTier, RatingTone> = {
  excellent: 'success',
  good: 'info',
  needsImprovement: 'warning',
};

export function ratingTone(rating: number): RatingTone {
  return TIER_TONE[ratingTier(rating)];
}

export interface RatingDistribution {
  excellent: number;
  good: number;
  needsImprovement: number;
  noData: number;
}

/** Counts how many ratings fall in each tier; a null rating counts as "no data". */
export function ratingDistribution(ratings: Array<number | null | undefined>): RatingDistribution {
  const result: RatingDistribution = { excellent: 0, good: 0, needsImprovement: 0, noData: 0 };
  for (const rating of ratings) {
    if (rating == null) result.noData += 1;
    else result[ratingTier(rating)] += 1;
  }
  return result;
}

/** Describes the tier boundaries for a legend, so the numbers live next to the constants above. */
export const RATING_LEGEND = {
  excellent: `Excellent (≥${EXCELLENT_FROM}.0)`,
  good: `On target (${GOOD_FROM}.0–${EXCELLENT_FROM - 0.1})`,
  needsImprovement: `Needs improvement (<${GOOD_FROM}.0)`,
} as const;
