import { Button, LinkButton, Modal } from '@/components/ui';
import type { CourseDetailResponse } from '../types';

/** The ordered exercise rotation of one course, with a shortcut to enroll a group in it. */
export function CourseDetailModal({ detail, onClose }: { detail: CourseDetailResponse | null; onClose: () => void }) {
  return (
    <Modal
      open={detail !== null}
      onClose={onClose}
      title={detail?.course.name ?? ''}
      description={detail ? `Total sessions: ${detail.course.totalSessions}` : undefined}
      footer={
        detail && (
          <>
            <Button onClick={onClose}>Close</Button>
            <LinkButton variant="primary" href={`/trainer/plans/new?courseId=${detail.course.id}`}>
              Enroll a group in this course
            </LinkButton>
          </>
        )
      }
    >
      {detail && (
        <div className="space-y-4">
          {detail.course.targetGoal && (
            <p className="text-sm font-medium text-[var(--color-primary)]">Goal: {detail.course.targetGoal}</p>
          )}
          <div>
            <h3 className="mb-2 text-sm font-medium text-[var(--color-text-primary)]">
              Exercise rotation ({detail.subjects.length} exercises)
            </h3>
            <ol className="space-y-1.5">
              {detail.subjects.map((subject) => (
                <li
                  key={subject.id}
                  className="flex items-center justify-between gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] p-2 text-xs"
                >
                  <span className="font-semibold text-[var(--color-primary)]">Session {subject.orderIndex}</span>
                  <span className="min-w-0 flex-1 truncate font-medium text-[var(--color-text-primary)]">{subject.subjectName}</span>
                  <span className="text-[var(--color-text-muted)]">{subject.durationMinutes} min</span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      )}
    </Modal>
  );
}
