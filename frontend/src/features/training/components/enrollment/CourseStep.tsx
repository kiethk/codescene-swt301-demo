import { Button, Icon, Pill, Panel, Select, SectionTitle } from '@/components/ui';
import { formatDate } from '@/lib/display';
import type { Course, JoinableCohortResponse } from '../../types';

/** Step 1: pick the course, and optionally join the schedule of a group that is already forming. */
export function CourseStep({
  courses,
  courseId,
  onCourseChange,
  cohorts,
  cohortsLoading,
  onUseCohort,
}: {
  courses: Course[];
  courseId: number | '';
  onCourseChange: (courseId: number | '') => void;
  cohorts: JoinableCohortResponse[];
  cohortsLoading: boolean;
  onUseCohort: (cohort: JoinableCohortResponse) => void;
}) {
  const selected = courses.find((course) => course.id === courseId);

  return (
    <Panel padded className="space-y-4">
      <SectionTitle>1. Select a training course</SectionTitle>

      <div className="grid grid-cols-1 items-center gap-3 sm:grid-cols-2">
        <Select
          aria-label="Training course"
          value={courseId}
          onChange={(e) => onCourseChange(e.target.value ? Number(e.target.value) : '')}
        >
          {courses.map((course) => (
            <option key={course.id} value={course.id}>{course.name} ({course.totalSessions} sessions)</option>
          ))}
        </Select>
        {selected?.targetGoal && (
          <p className="flex items-center gap-1.5 text-xs text-[var(--color-text-secondary)]">
            <Icon name="target" size={13} />
            {selected.targetGoal}
          </p>
        )}
      </div>

      <div className="space-y-2 border-t border-[var(--color-border)] pt-3">
        <p className="flex items-center gap-1.5 text-xs font-semibold text-[var(--color-text-primary)]">
          Suggested groups to join
          {cohortsLoading && <span className="font-normal text-[var(--color-text-muted)]">(Searching…)</span>}
        </p>

        {cohorts.length === 0 ? (
          <p className="text-xs text-[var(--color-text-muted)]">
            No groups are open for this course. The group you create will be the first.
          </p>
        ) : (
          <ul className="space-y-2">
            {cohorts.map((cohort) => (
              <li
                key={`${cohort.cohortStatus}-${cohort.suggestedStartDate}-${cohort.trainingDays.join()}`}
                className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3 text-xs"
              >
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2 font-semibold text-[var(--color-text-primary)]">
                    <span>Group “{cohort.courseName}”</span>
                    <Pill tone={cohort.cohortStatus === 'ACTIVE' ? 'success' : 'info'} size="sm">
                      {cohort.cohortStatus === 'ACTIVE' ? 'In training' : 'Upcoming'} · {cohort.horseCount} horses
                    </Pill>
                  </div>
                  <p className="text-[var(--color-text-secondary)]">
                    {cohort.note} · Starts {formatDate(cohort.suggestedStartDate)} (in {cohort.waitDays} days)
                  </p>
                  {/* Matching the schedule does not guarantee the same lot: a full lot sends extra horses to another one. */}
                  {cohort.horseCount >= cohort.lotCapacity && (
                    <p className="text-[var(--color-warning)]">
                      This group is at capacity ({cohort.lotCapacity} horses). Additional horses will train in another session that day.
                    </p>
                  )}
                </div>
                <Button size="sm" type="button" onClick={() => onUseCohort(cohort)}>Use this group schedule</Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Panel>
  );
}
