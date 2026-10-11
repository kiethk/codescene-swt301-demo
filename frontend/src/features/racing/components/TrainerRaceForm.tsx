'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, LinkButton, Notice, PageHeader, ScreenLayout } from '@/components/ui';
import { horseService } from '@/features/horses/services/horseService';
import type { HorseSpecialty } from '@/features/horses/types';
import { stableApi } from '@/features/stable/services/stableService';
import type { Horse } from '@/features/stable/types';
import { displayError } from '@/lib/display';
import { toast } from '@/lib/toast';
import { buildRaceRequest, EMPTY_RACE_FORM, validateRaceForm, type RaceFormValues } from '../lib/raceForm';
import { racingService } from '../services/racingService';
import { EventSection } from './form/EventSection';
import { ExtraInfoSection } from './form/ExtraInfoSection';
import { HorseSection } from './form/HorseSection';

type FieldErrors = Partial<Record<keyof RaceFormValues, string>>;

/** Submits an internal race nomination for management review. The backend decides which horses may be nominated. */
export function TrainerRaceForm() {
  const router = useRouter();

  const [horses, setHorses] = useState<Horse[]>([]);
  const [loadingHorses, setLoadingHorses] = useState(true);
  const [horseError, setHorseError] = useState<string | null>(null);
  const [specialty, setSpecialty] = useState<HorseSpecialty | null>(null);

  const [values, setValues] = useState<RaceFormValues>(EMPTY_RACE_FORM);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Eligible horses in the trainer's own area; the backend re-checks eligibility on submit.
  useEffect(() => {
    let active = true;
    stableApi
      .getHorses({ mine: true, status: 'ELIGIBLE' })
      .then((list) => active && setHorses(list))
      .catch((cause) => active && setHorseError(displayError(cause, 'Unable to load horses.')))
      .finally(() => active && setLoadingHorses(false));
    return () => {
      active = false;
    };
  }, []);

  // The specialty of the chosen horse, to help judge whether the race suits it.
  useEffect(() => {
    if (!values.horseId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSpecialty(null);
      return;
    }
    let active = true;
    horseService
      .getSpecialty(Number(values.horseId))
      .then((data) => active && setSpecialty(data))
      .catch(() => active && setSpecialty(null));
    return () => {
      active = false;
    };
  }, [values.horseId]);

  function change(field: keyof RaceFormValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const found = validateRaceForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const created = await racingService.create(buildRaceRequest(values));
      toast.success('Nomination submitted for review.');
      router.push(`/trainer/racing/${created.id}`);
    } catch (cause) {
      setSubmitError(displayError(cause, 'Unable to submit the nomination.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ScreenLayout variant="form">
      <PageHeader
        title="Create a race nomination"
        description="Submit an internal nomination for management review."
        breadcrumbs={[{ label: 'Race nominations', href: '/trainer/racing' }, { label: 'New nomination' }]}
      />

      {submitError && <Notice tone="error" title="Submission error">{submitError}</Notice>}

      <form onSubmit={handleSubmit} className="space-y-5">
        <HorseSection
          horses={horses}
          loading={loadingHorses}
          loadError={horseError}
          values={values}
          errors={errors}
          specialty={specialty}
          onChange={change}
        />
        <EventSection values={values} errors={errors} onChange={change} />
        <ExtraInfoSection values={values} onChange={change} />

        <div className="flex flex-col gap-3 border-t border-[var(--color-border)] pt-4 sm:flex-row sm:items-center sm:justify-between">
          <Notice tone="warning">This is an internal nomination and is not a registration with the event organiser.</Notice>
          <div className="flex shrink-0 items-center gap-3">
            <LinkButton href="/trainer/racing" aria-disabled={submitting}>Cancel</LinkButton>
            <Button type="submit" variant="primary" loading={submitting} disabled={horses.length === 0}>
              Submit for review
            </Button>
          </div>
        </div>
      </form>
    </ScreenLayout>
  );
}
