import type { SurfaceType } from '@/features/training/types';

/** Race-distance band, defined by the backend (the limits live in one place there). */
export type DistanceBand = 'SPRINT' | 'MILE' | 'MIDDLE' | 'STAYER';

/** Evidence behind a distance badge: the horse's rated workouts in one band. */
export interface DistanceStat {
  band: DistanceBand;
  workoutCount: number;
  averageRating: number;
  /** Null when no workout in the band recorded a top speed. */
  averageTopSpeedKmh: number | null;
}

/** Racing specialty inferred by the backend from completed workouts. Nothing is stored or typed in by hand. */
export interface HorseSpecialty {
  /** Rated workouts a group needs before it can be judged (from the backend, never hard-coded in the UI). */
  minWorkoutsPerGroup: number;
  /** Bands the horse does well in. Empty when none qualifies; may hold several. */
  strongDistances: DistanceBand[];
  /** Null when no surface clearly stands out. */
  preferredSurface: SurfaceType | null;
  distanceStats: DistanceStat[];
}
