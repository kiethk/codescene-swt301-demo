import type { CreateRaceRegistrationRequest } from '../types';

/** What the nomination form holds. Numbers stay as text until the request is built, so an empty field is "no value". */
export interface RaceFormValues {
  horseId: string;
  selectionReason: string;
  raceName: string;
  raceCategory: string;
  location: string;
  eventDate: string;
  eventTime: string;
  organizer: string;
  sourceUrl: string;
  nominationDeadline: string;
  distanceMeters: string;
  trackType: string;
  prizeDetails: string;
  trainerNotes: string;
}

export const EMPTY_RACE_FORM: RaceFormValues = {
  horseId: '',
  selectionReason: '',
  raceName: '',
  raceCategory: '',
  location: '',
  eventDate: '',
  eventTime: '',
  organizer: '',
  sourceUrl: '',
  nominationDeadline: '',
  distanceMeters: '',
  trackType: '',
  prizeDetails: '',
  trainerNotes: '',
};

const REQUIRED: Array<[keyof RaceFormValues, string]> = [
  ['horseId', 'Select a horse assigned to you.'],
  ['selectionReason', 'Enter a reason for nominating this horse.'],
  ['raceName', 'Enter the race or event name.'],
  ['raceCategory', 'Enter a specific race category (for example, 3-year-old, 1,200 m).'],
  ['location', 'Enter the racecourse and location.'],
  ['eventDate', 'Select the race date.'],
];

/**
 * Only the fields the nomination cannot exist without. Dates, URLs and the horse's eligibility are checked by the
 * backend, whose message is shown when it refuses, so the rules live in one place.
 */
export function validateRaceForm(values: RaceFormValues): Partial<Record<keyof RaceFormValues, string>> {
  const errors: Partial<Record<keyof RaceFormValues, string>> = {};
  for (const [field, message] of REQUIRED) {
    if (!values[field].trim()) errors[field] = message;
  }
  return errors;
}

const orNull = (value: string): string | null => (value.trim() ? value.trim() : null);

export function buildRaceRequest(values: RaceFormValues): CreateRaceRegistrationRequest {
  return {
    horseId: Number(values.horseId),
    raceName: values.raceName.trim(),
    raceCategory: values.raceCategory.trim(),
    location: values.location.trim(),
    eventDate: values.eventDate,
    eventTime: values.eventTime || null,
    organizer: orNull(values.organizer),
    sourceUrl: orNull(values.sourceUrl),
    nominationDeadline: values.nominationDeadline || null,
    distanceMeters: values.distanceMeters.trim() ? Number(values.distanceMeters) : null,
    trackType: orNull(values.trackType),
    prizeDetails: orNull(values.prizeDetails),
    selectionReason: values.selectionReason.trim(),
    trainerNotes: orNull(values.trainerNotes),
  };
}
