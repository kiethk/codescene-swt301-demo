'use client';

import { useState } from 'react';
import { Button, FieldLabel, FormField, IconButton, Input, Notice, Panel, Pill, SectionTitle, Textarea } from '@/components/ui';
import type { CreateCourseRequest, Subject } from '../types';

const DEFAULT_SESSIONS = 12;

/** Form that builds a course out of an ordered rotation of subjects. The parent owns the request and its errors. */
export function CourseForm({
  subjects,
  submitting,
  error,
  onSubmit,
  onCancel,
}: {
  subjects: Subject[];
  submitting: boolean;
  error: string | null;
  onSubmit: (request: CreateCourseRequest) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [targetGoal, setTargetGoal] = useState('');
  const [totalSessions, setTotalSessions] = useState(DEFAULT_SESSIONS);
  const [rotation, setRotation] = useState<number[]>([]);
  const [validation, setValidation] = useState<string | null>(null);

  const subjectById = new Map(subjects.map((subject) => [subject.id, subject]));

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim() || totalSessions <= 0) {
      setValidation('Enter a course name and a valid number of sessions.');
      return;
    }
    if (rotation.length === 0) {
      setValidation('Select at least one subject for the course rotation.');
      return;
    }
    setValidation(null);
    onSubmit({
      name: name.trim(),
      description: description.trim() || undefined,
      targetGoal: targetGoal.trim() || undefined,
      totalSessions: Number(totalSessions),
      subjects: rotation.map((subjectId, index) => ({ subjectId, orderIndex: index + 1 })),
    });
  }

  const message = validation ?? error;

  return (
    <Panel padded>
      <form onSubmit={handleSubmit} className="space-y-4">
        <SectionTitle>Create course with subject rotation</SectionTitle>
        {message && <Notice tone="error">{message}</Notice>}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <FormField label="Course name" required>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Sprint 2YO Foundation" />
          </FormField>
          <FormField
            label="Total sessions"
            required
            hint="Subjects repeat in order until the total number of sessions is reached."
          >
            <Input type="number" min={1} value={totalSessions} onChange={(e) => setTotalSessions(Number(e.target.value))} />
          </FormField>
        </div>

        <FormField label="Course goal">
          <Input
            value={targetGoal}
            onChange={(e) => setTargetGoal(e.target.value)}
            placeholder="e.g. Prepare for short-distance races"
          />
        </FormField>

        <FormField label="Course description">
          <Textarea
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe the training path and suitable fitness level…"
          />
        </FormField>

        <div className="space-y-2 border-t border-[var(--color-border)] pt-3">
          <FieldLabel>Subject rotation ({rotation.length} selected)</FieldLabel>
          <div className="min-h-12 rounded-[var(--radius-md)] border border-dashed border-[var(--color-border-strong)] bg-[var(--color-surface-muted)] p-2">
            {rotation.length === 0 ? (
              <span className="text-xs text-[var(--color-text-muted)]">No subjects have been added to the rotation.</span>
            ) : (
              <ol className="flex flex-wrap gap-2">
                {rotation.map((subjectId, index) => (
                  <li key={`${subjectId}-${index}`} className="inline-flex items-center gap-1">
                    <Pill tone="neutral">#{index + 1} {subjectById.get(subjectId)?.name ?? `Subject #${subjectId}`}</Pill>
                    <IconButton
                      icon="x"
                      size="sm"
                      label={`Remove subject ${index + 1} from the rotation`}
                      onClick={() => setRotation((current) => current.filter((_, i) => i !== index))}
                    />
                  </li>
                ))}
              </ol>
            )}
          </div>
          <p className="text-xs text-[var(--color-text-muted)]">Select subjects to add them to the rotation in order:</p>
          <div className="flex flex-wrap gap-1.5">
            {subjects.map((subject) => (
              <Button
                key={subject.id}
                size="sm"
                icon="plus"
                onClick={() => setRotation((current) => [...current, subject.id])}
              >
                {subject.name} ({subject.durationMinutes} min)
              </Button>
            ))}
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-[var(--color-border)] pt-3">
          <Button type="button" onClick={onCancel} disabled={submitting}>Cancel</Button>
          <Button type="submit" variant="primary" loading={submitting}>Save course</Button>
        </div>
      </form>
    </Panel>
  );
}
