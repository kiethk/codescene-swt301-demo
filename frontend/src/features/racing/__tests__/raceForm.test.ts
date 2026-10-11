import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { buildRaceRequest, EMPTY_RACE_FORM, validateRaceForm } from '../lib/raceForm.ts';
import type { RaceFormValues } from '../lib/raceForm.ts';

const filled: RaceFormValues = {
  ...EMPTY_RACE_FORM,
  horseId: '7',
  selectionReason: '  In top form  ',
  raceName: ' Autumn Cup ',
  raceCategory: '3-year-old, 1,200 m',
  location: 'Soc Son',
  eventDate: '2026-11-20',
};

describe('validateRaceForm', () => {
  it('reports every missing required field', () => {
    const errors = validateRaceForm(EMPTY_RACE_FORM);
    assert.deepEqual(Object.keys(errors).sort(), ['eventDate', 'horseId', 'location', 'raceCategory', 'raceName', 'selectionReason']);
  });

  it('treats a blank field as missing', () => {
    assert.ok(validateRaceForm({ ...filled, raceName: '   ' }).raceName);
  });

  it('accepts a filled form and leaves date and URL checks to the backend', () => {
    assert.deepEqual(validateRaceForm({ ...filled, eventDate: '1999-01-01', sourceUrl: 'not a url' }), {});
  });
});

describe('buildRaceRequest', () => {
  it('trims text and turns empty optional fields into null', () => {
    const request = buildRaceRequest(filled);
    assert.equal(request.horseId, 7);
    assert.equal(request.raceName, 'Autumn Cup');
    assert.equal(request.selectionReason, 'In top form');
    assert.equal(request.eventTime, null);
    assert.equal(request.organizer, null);
    assert.equal(request.distanceMeters, null);
    assert.equal(request.trackType, null);
  });

  it('converts the distance to a number when it is given', () => {
    assert.equal(buildRaceRequest({ ...filled, distanceMeters: '1200' }).distanceMeters, 1200);
  });
});
