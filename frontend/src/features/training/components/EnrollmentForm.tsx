'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Button, EmptyState, ListSkeleton, Notice, PageHeader, Panel, ScreenLayout } from '@/components/ui';
import { stableApi } from '@/features/stable/services/stableService';
import type { Horse } from '@/features/stable/types';
import { displayError } from '@/lib/display';
import { tomorrowIso } from '../lib/dateRange';
import { trainingService } from '../services/trainingService';
import type {
  Course,
  CreateHorseTrainingPlanRequest,
  HorseTrainingPlanDetailResponse,
  JoinableCohortResponse,
  TrainingDay,
} from '../types';
import { CourseStep } from './enrollment/CourseStep';
import { HorseStep } from './enrollment/HorseStep';
import { ScheduleStep } from './enrollment/ScheduleStep';
import { EnrollmentResultSummary } from './EnrollmentResultSummary';

const DEFAULT_DAYS: TrainingDay[] = ['MONDAY', 'WEDNESDAY', 'FRIDAY'];

/** Enrolls a group of horses in a course. The backend decides lots, grooms and capacity; this only collects the request. */
export function EnrollmentForm() {
  const router = useRouter();
  const initialCourseId = useSearchParams().get('courseId');

  const [courses, setCourses] = useState<Course[]>([]);
  const [horses, setHorses] = useState<Horse[]>([]);
  const [cohorts, setCohorts] = useState<JoinableCohortResponse[]>([]);
  const [cohortsLoading, setCohortsLoading] = useState(false);
  /** Enrollment result: when set, the form is replaced by the summary screen. */
  const [result, setResult] = useState<HorseTrainingPlanDetailResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [courseId, setCourseId] = useState<number | ''>(initialCourseId ? Number(initialCourseId) : '');
  const [selectedHorseIds, setSelectedHorseIds] = useState<number[]>([]);
  const [startDate, setStartDate] = useState(() => tomorrowIso());
  const [trainingDays, setTrainingDays] = useState<TrainingDay[]>(DEFAULT_DAYS);
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Courses, and the horses the trainer can enroll: eligible horses in the trainer's own area.
  const loadInitial = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [courseList, horseList] = await Promise.all([
        trainingService.getCourses(),
        stableApi.getHorses({ mine: true, status: 'ELIGIBLE' }),
      ]);
      setCourses(courseList);
      setHorses(horseList);
      if (!initialCourseId && courseList.length > 0) setCourseId(courseList[0].id);
    } catch (cause) {
      setLoadError(displayError(cause, 'Unable to load courses or eligible horses.'));
    } finally {
      setLoading(false);
    }
  }, [initialCourseId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadInitial();
  }, [loadInitial]);

  // Groups already forming for the chosen course.
  useEffect(() => {
    if (!courseId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setCohorts([]);
      return;
    }
    let active = true;
    setCohortsLoading(true);
    trainingService
      .getJoinableCohorts(courseId)
      .then((data) => active && setCohorts(data))
      .catch(() => active && setCohorts([]))
      .finally(() => active && setCohortsLoading(false));
    return () => {
      active = false;
    };
  }, [courseId]);

  function toggleHorse(horseId: number) {
    setSelectedHorseIds((current) =>
      current.includes(horseId) ? current.filter((id) => id !== horseId) : [...current, horseId],
    );
  }

  function toggleDay(day: TrainingDay) {
    setTrainingDays((current) => {
      if (!current.includes(day)) return [...current, day];
      return current.length > 1 ? current.filter((d) => d !== day) : current;
    });
  }

  function applyCohortSchedule(cohort: JoinableCohortResponse) {
    setStartDate(cohort.suggestedStartDate);
    setTrainingDays([...cohort.trainingDays]);
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!courseId) {
      setSubmitError('Select a course.');
      return;
    }
    if (selectedHorseIds.length === 0) {
      setSubmitError('Select at least one horse for the training group.');
      return;
    }

    setSubmitError(null);
    setSubmitting(true);
    try {
      const request: CreateHorseTrainingPlanRequest = {
        courseId,
        startDate,
        trainingDays,
        notes: notes.trim() || undefined,
        horses: selectedHorseIds.map((horseId) => ({ horseId })),
      };
      const plans = await trainingService.createPlan(request);
      if (plans.length > 0) {
        // Show every plan of the group, not just the first: a group can be split over several lots and the
        // trainer needs to see how many and why.
        setResult(plans);
      } else {
        router.push('/trainer/courses');
      }
    } catch (cause) {
      // The backend's own message is kept: for example the golden-hour notice carries the exact minutes left.
      setSubmitError(displayError(cause, 'Unable to create training plans.'));
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <Panel padded><ListSkeleton rows={5} /></Panel>;

  if (loadError) {
    return (
      <Panel padded>
        <EmptyState
          icon="alert-triangle"
          title="Unable to load enrollment data"
          description={loadError}
          action={<Button size="sm" icon="refresh" onClick={loadInitial}>Try again</Button>}
        />
      </Panel>
    );
  }

  if (result) {
    return <EnrollmentResultSummary plans={result} horses={horses} onDone={() => router.push('/trainer/courses')} />;
  }

  return (
    <ScreenLayout variant="form">
      <PageHeader
        title="Group training enrollment"
        description="Select multiple horses and the system will schedule them in shared workouts."
        breadcrumbs={[{ label: 'Training', href: '/trainer/courses' }, { label: 'Enroll horses' }]}
      />

      {submitError && <Notice tone="error" title="Unable to create the training plan">{submitError}</Notice>}

      <form onSubmit={handleSubmit} className="space-y-5">
        <CourseStep
          courses={courses}
          courseId={courseId}
          onCourseChange={setCourseId}
          cohorts={cohorts}
          cohortsLoading={cohortsLoading}
          onUseCohort={applyCohortSchedule}
        />
        <HorseStep horses={horses} selectedIds={selectedHorseIds} onToggle={toggleHorse} onToggleAll={setSelectedHorseIds} />
        <ScheduleStep
          startDate={startDate}
          onStartDateChange={setStartDate}
          trainingDays={trainingDays}
          onToggleDay={toggleDay}
          notes={notes}
          onNotesChange={setNotes}
        />

        <div className="flex items-center justify-end gap-3">
          <Button type="button" onClick={() => router.back()} disabled={submitting}>Back</Button>
          <Button type="submit" variant="primary" loading={submitting} disabled={selectedHorseIds.length === 0}>
            {submitting ? 'Scheduling workouts…' : `Enroll ${selectedHorseIds.length} horses`}
          </Button>
        </div>
      </form>
    </ScreenLayout>
  );
}
