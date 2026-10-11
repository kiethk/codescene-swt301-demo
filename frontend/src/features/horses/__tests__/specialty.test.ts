import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { describeSpecialty } from '../lib/specialty.ts';
import type { HorseSpecialty } from '../types/index.ts';

const base: HorseSpecialty = {
  minWorkoutsPerGroup: 3,
  strongDistances: [],
  preferredSurface: null,
  distanceStats: [],
};

describe('describeSpecialty', () => {
  it('shows one badge per strong distance, with the evidence as its detail', () => {
    const view = describeSpecialty({
      ...base,
      strongDistances: ['SPRINT', 'MILE'],
      distanceStats: [
        { band: 'SPRINT', workoutCount: 4, averageRating: 8, averageTopSpeedKmh: 59.3 },
        { band: 'MILE', workoutCount: 3, averageRating: 7.3, averageTopSpeedKmh: null },
      ],
    });

    assert.deepEqual(view.badges.map((b) => b.label), ['Sprint specialist', 'Mile specialist']);
    assert.equal(view.badges[0].detail, '4 rated workouts · average rating 8.0/10 · average top speed 59.3 km/h');
    assert.equal(view.badges[1].detail, '3 rated workouts · average rating 7.3/10');
  });

  it('adds a surface badge only when the backend names a preferred surface', () => {
    const withSurface = describeSpecialty({ ...base, strongDistances: ['SPRINT'], preferredSurface: 'TURF' });
    assert.deepEqual(withSurface.badges.map((b) => b.label), ['Sprint specialist', 'Prefers turf']);

    const without = describeSpecialty({ ...base, strongDistances: ['SPRINT'] });
    assert.deepEqual(without.badges.map((b) => b.label), ['Sprint specialist']);
  });

  it('says what each distance still needs instead of concluding the horse has no talent', () => {
    const view = describeSpecialty({
      ...base,
      distanceStats: [
        { band: 'SPRINT', workoutCount: 3, averageRating: 5, averageTopSpeedKmh: null },
        { band: 'MILE', workoutCount: 1, averageRating: 9, averageTopSpeedKmh: null },
      ],
    });

    assert.equal(view.badges.length, 0);
    assert.deepEqual(view.progress, [
      'Sprint: 3 rated workouts, average 5.0/10, no badge yet',
      'Mile: 1/3 rated workouts',
    ]);
  });

  it('reports a horse without any rated workout', () => {
    assert.deepEqual(describeSpecialty(base).progress, ['No rated workouts yet']);
  });

  it('gives no progress lines once a badge is earned', () => {
    const view = describeSpecialty({ ...base, strongDistances: ['SPRINT'] });
    assert.deepEqual(view.progress, []);
  });
});
