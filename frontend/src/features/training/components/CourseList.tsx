'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, EmptyState, Icon, LinkButton, ListSkeleton, PageSection, Panel, Pill } from '@/components/ui';
import { displayError } from '@/lib/display';
import { toast } from '@/lib/toast';
import { trainingService } from '../services/trainingService';
import type { Course, CourseDetailResponse, CreateCourseRequest, Subject } from '../types';
import { CourseDetailModal } from './CourseDetailModal';
import { CourseForm } from './CourseForm';

function CourseCard({
  course,
  detailLoading,
  onOpen,
}: {
  course: Course;
  detailLoading: boolean;
  onOpen: () => void;
}) {
  return (
    <Panel padded className="flex h-full flex-col justify-between gap-4">
      <div className="space-y-2">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-base font-semibold text-[var(--color-text-primary)]">{course.name}</h3>
          <Pill tone="primary" size="sm">{course.totalSessions} sessions</Pill>
        </div>
        {course.targetGoal && (
          <p className="flex items-center gap-1.5 text-xs font-medium text-[var(--color-primary)]">
            <Icon name="target" size={13} />
            {course.targetGoal}
          </p>
        )}
        {course.description && (
          <p className="line-clamp-3 text-xs text-[var(--color-text-secondary)]">{course.description}</p>
        )}
      </div>
      <div className="flex items-center justify-between gap-2 border-t border-[var(--color-border)] pt-3">
        <Button size="sm" onClick={onOpen} disabled={detailLoading}>View course details</Button>
        <LinkButton href={`/trainer/plans/new?courseId=${course.id}`} variant="primary" size="sm">Enroll</LinkButton>
      </div>
    </Panel>
  );
}

export function CourseList() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [detail, setDetail] = useState<CourseDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [courseList, subjectList] = await Promise.all([trainingService.getCourses(), trainingService.getSubjects()]);
      setCourses(courseList);
      setSubjects(subjectList);
    } catch (cause) {
      setError(displayError(cause, 'Unable to load training courses.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function openDetail(courseId: number) {
    setDetailLoading(true);
    try {
      setDetail(await trainingService.getCourse(courseId));
    } catch (cause) {
      toast.error(displayError(cause, 'Unable to load the course details.'));
    } finally {
      setDetailLoading(false);
    }
  }

  async function createCourse(request: CreateCourseRequest) {
    setFormError(null);
    setSubmitting(true);
    try {
      await trainingService.createCourse(request);
      toast.success('Course created.');
      setShowForm(false);
      await load();
    } catch (cause) {
      setFormError(displayError(cause, 'Unable to create the course.'));
    } finally {
      setSubmitting(false);
    }
  }

  if (loading && courses.length === 0) return <Panel padded><ListSkeleton rows={4} /></Panel>;

  if (error) {
    return (
      <Panel padded>
        <EmptyState
          icon="alert-triangle"
          title="Unable to load training courses"
          description={error}
          action={<Button size="sm" icon="refresh" onClick={load}>Try again</Button>}
        />
      </Panel>
    );
  }

  return (
    <PageSection
      title={`Training courses (${courses.length})`}
      description="Create complete training courses with rotating exercises for horse enrollment."
      actions={
        <>
          <LinkButton href="/trainer/plans/new" size="sm">Enroll horses</LinkButton>
          <Button variant="primary" size="sm" onClick={() => setShowForm((open) => !open)}>
            {showForm ? 'Close form' : 'Create course'}
          </Button>
        </>
      }
    >
      {showForm && (
        <CourseForm
          subjects={subjects}
          submitting={submitting}
          error={formError}
          onSubmit={createCourse}
          onCancel={() => setShowForm(false)}
        />
      )}

      {courses.length === 0 ? (
        <Panel padded>
          <EmptyState icon="activity" title="No training courses yet" description="Create your first course to prepare horses for training." />
        </Panel>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {courses.map((course) => (
            <CourseCard key={course.id} course={course} detailLoading={detailLoading} onOpen={() => openDetail(course.id)} />
          ))}
        </div>
      )}

      <CourseDetailModal detail={detail} onClose={() => setDetail(null)} />
    </PageSection>
  );
}
