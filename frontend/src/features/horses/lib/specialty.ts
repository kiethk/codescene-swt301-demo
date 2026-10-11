import type { IconName } from '@/components/ui/Icon';
import type { DistanceBand, DistanceStat, HorseSpecialty } from '../types';

const DISTANCE_LABEL: Record<DistanceBand, string> = {
  SPRINT: 'Sprint',
  MILE: 'Mile',
  MIDDLE: 'Middle distance',
  STAYER: 'Stayer',
};

const SURFACE_LABEL: Record<string, string> = {
  TURF: 'turf',
  DIRT: 'dirt',
  SYNTHETIC: 'synthetic',
};

export interface SpecialtyBadge {
  id: string;
  label: string;
  tone: 'primary' | 'info';
  icon: IconName;
  /** One line of evidence for the tooltip. */
  detail: string;
}

export interface SpecialtyView {
  badges: SpecialtyBadge[];
  /**
   * What each distance still needs, shown when there is no badge. It states facts (workouts so far, average) and
   * never concludes that the horse has no talent: the other distances may simply not have been tried yet.
   */
  progress: string[];
}

function describeStat(stat: DistanceStat): string {
  const parts = [
    `${stat.workoutCount} rated workouts`,
    `average rating ${stat.averageRating.toFixed(1)}/10`,
  ];
  if (stat.averageTopSpeedKmh != null) {
    parts.push(`average top speed ${stat.averageTopSpeedKmh.toFixed(1)} km/h`);
  }
  return parts.join(' · ');
}

/**
 * Turns the backend specialty into what the UI shows. It only formats: which badges a horse earns is decided
 * by the backend and is never recomputed here.
 */
export function describeSpecialty(specialty: HorseSpecialty): SpecialtyView {
  const statByBand = new Map(specialty.distanceStats.map((stat) => [stat.band, stat] as const));

  const badges: SpecialtyBadge[] = specialty.strongDistances.map((band) => {
    const stat = statByBand.get(band);
    return {
      id: `distance-${band}`,
      label: `${DISTANCE_LABEL[band]} specialist`,
      tone: 'primary',
      icon: 'flag',
      detail: stat ? describeStat(stat) : 'Strong results at this distance',
    };
  });

  if (specialty.preferredSurface) {
    const surface = SURFACE_LABEL[specialty.preferredSurface] ?? specialty.preferredSurface.toLowerCase();
    badges.push({
      id: `surface-${specialty.preferredSurface}`,
      label: `Prefers ${surface}`,
      tone: 'info',
      icon: 'target',
      detail: 'Best-rated surface in recent workouts',
    });
  }

  if (badges.length > 0) {
    return { badges, progress: [] };
  }

  const min = specialty.minWorkoutsPerGroup;
  const progress = specialty.distanceStats.map((stat) =>
    stat.workoutCount < min
      ? `${DISTANCE_LABEL[stat.band]}: ${stat.workoutCount}/${min} rated workouts`
      : `${DISTANCE_LABEL[stat.band]}: ${stat.workoutCount} rated workouts, average ${stat.averageRating.toFixed(1)}/10, no badge yet`,
  );
  return { badges, progress: progress.length > 0 ? progress : ['No rated workouts yet'] };
}
