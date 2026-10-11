import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { planProgress } from '../lib/planProgress.ts';
import { ratingDistribution, ratingTier, ratingTone } from '../lib/rating.ts';
import { lotFillTone, PLAN_STATUS } from '../lib/status.ts';
import { hourMarks, minutesOf, stripPosition } from '../lib/timeline.ts';
import { rangeForPreset, rangeLabel, toIsoDate, tomorrowIso } from '../lib/dateRange.ts';

describe('planProgress', () => {
  it('removes cancelled sessions from the denominator so the plan can reach 100%', () => {
    const progress = planProgress(11, 12, 1);
    assert.equal(progress.total, 11);
    assert.equal(progress.percent, 100);
  });

  it('never divides by zero for an empty plan or one whose sessions are all cancelled', () => {
    assert.deepEqual(planProgress(0, 0, 0), { completed: 0, total: 0, cancelled: 0, percent: 0 });
    assert.equal(planProgress(0, 4, 4).percent, 0);
  });

  it('caps the percentage at 100', () => {
    assert.equal(planProgress(5, 3, 0).percent, 100);
  });
});

describe('rating tiers', () => {
  it('puts the boundaries into the higher tier', () => {
    assert.equal(ratingTier(8), 'excellent');
    assert.equal(ratingTier(7.9), 'good');
    assert.equal(ratingTier(6), 'good');
    assert.equal(ratingTier(5.9), 'needsImprovement');
  });

  it('maps each tier to one tone', () => {
    assert.equal(ratingTone(9), 'success');
    assert.equal(ratingTone(7), 'info');
    assert.equal(ratingTone(3), 'warning');
  });

  it('counts a missing rating as no data', () => {
    assert.deepEqual(ratingDistribution([9, 8, 7, 4, null, undefined]), {
      excellent: 2,
      good: 1,
      needsImprovement: 1,
      noData: 2,
    });
  });
});

describe('status maps', () => {
  it('covers every plan status the dashboard can return, including NONE', () => {
    assert.deepEqual(Object.keys(PLAN_STATUS).sort(), ['ACTIVE', 'CANCELLED', 'COMPLETED', 'NONE', 'UPCOMING']);
  });

  it('flags a full lot red and a nearly full lot amber', () => {
    assert.equal(lotFillTone(0), 'danger');
    assert.equal(lotFillTone(2), 'warning');
    assert.equal(lotFillTone(3), 'success');
  });
});

describe('date ranges', () => {
  const today = new Date(2026, 9, 11); // 11 Oct 2026, local time

  it('formats a date in the local time zone, not UTC', () => {
    assert.equal(toIsoDate(new Date(2026, 0, 5, 0, 30)), '2026-01-05');
  });

  it('counts a preset back from today', () => {
    assert.deepEqual(rangeForPreset('7D', today), { from: '2026-10-04', to: '2026-10-11' });
    assert.deepEqual(rangeForPreset('30D', today), { from: '2026-09-11', to: '2026-10-11' });
  });

  it('has no bounds for all time and uses the typed dates for a custom range', () => {
    assert.deepEqual(rangeForPreset('ALL', today), {});
    assert.deepEqual(rangeForPreset('CUSTOM', today, { from: '2026-01-01', to: '' }), { from: '2026-01-01', to: undefined });
  });

  it('suggests tomorrow as the first training day, across a month end', () => {
    assert.equal(tomorrowIso(new Date(2026, 9, 31)), '2026-11-01');
  });
});

describe('schedule timeline', () => {
  it('reads backend times with or without seconds', () => {
    assert.equal(minutesOf('06:30'), 390);
    assert.equal(minutesOf('06:30:00'), 390);
  });

  it('places a lot as a share of the window', () => {
    assert.deepEqual(stripPosition('07:00:00', '08:00:00', '06:00:00', '10:00:00'), { leftPercent: 25, widthPercent: 25 });
  });

  it('keeps a lot that overruns the window inside the strip', () => {
    const position = stripPosition('09:30:00', '10:30:00', '06:00:00', '10:00:00');
    assert.equal(position.leftPercent, 87.5);
    assert.equal(position.widthPercent, 12.5);
  });

  it('marks every full hour of the window', () => {
    assert.deepEqual(hourMarks('06:00:00', '10:00:00').map((m) => m.label), ['06:00', '07:00', '08:00', '09:00', '10:00']);
    assert.equal(hourMarks('06:00:00', '10:00:00')[2].percent, 50);
  });
});

describe('range label', () => {
  const format = (date: string) => `<${date}>`;

  it('names a preset', () => {
    assert.equal(rangeLabel('30D', {}, format), 'Last 30 days');
    assert.equal(rangeLabel('ALL', {}, format), 'All time');
  });

  it('spells out a custom range, open ended on one side', () => {
    assert.equal(rangeLabel('CUSTOM', { from: '2026-01-01', to: '2026-02-01' }, format), '<2026-01-01> – <2026-02-01>');
    assert.equal(rangeLabel('CUSTOM', { from: '2026-01-01' }, format), 'From <2026-01-01>');
    assert.equal(rangeLabel('CUSTOM', { to: '2026-02-01' }, format), 'Until <2026-02-01>');
    assert.equal(rangeLabel('CUSTOM', {}, format), 'All time');
  });
});
