'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, EmptyState, ListSkeleton, PageSection, Panel, Pill } from '@/components/ui';
import { displayError, formatNumber } from '@/lib/display';
import { toast } from '@/lib/toast';
import { categoryLabel, intensityOption, surfaceLabel } from '../lib/subjectOptions';
import { trainingService } from '../services/trainingService';
import type { CreateSubjectRequest, Subject } from '../types';
import { SubjectForm } from './SubjectForm';

function SubjectCard({ subject }: { subject: Subject }) {
  const intensity = intensityOption(subject.intensityLevel);
  return (
    <Panel padded className="space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-[var(--color-text-primary)]">{subject.name}</h3>
          <p className="text-xs text-[var(--color-text-muted)]">{categoryLabel(subject.category)}</p>
        </div>
        <Pill tone={intensity.tone} size="sm">{intensity.label}</Pill>
      </div>
      {subject.description && (
        <p className="line-clamp-2 text-xs text-[var(--color-text-secondary)]">{subject.description}</p>
      )}
      <dl className="flex flex-wrap gap-x-4 gap-y-1 border-t border-[var(--color-border)] pt-2 text-xs text-[var(--color-text-muted)]">
        <div className="flex gap-1"><dt>Duration</dt><dd className="font-medium text-[var(--color-text-secondary)]">{subject.durationMinutes} min</dd></div>
        <div className="flex gap-1"><dt>Distance</dt><dd className="font-medium text-[var(--color-text-secondary)]">{formatNumber(subject.targetDistanceMeters)} m</dd></div>
        <div className="flex gap-1"><dt>Surface</dt><dd className="font-medium text-[var(--color-text-secondary)]">{surfaceLabel(subject.surfaceType)}</dd></div>
      </dl>
    </Panel>
  );
}

export function SubjectList() {
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSubjects(await trainingService.getSubjects());
    } catch (cause) {
      setError(displayError(cause, 'Unable to load the subject library.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function createSubject(request: CreateSubjectRequest) {
    setFormError(null);
    setSubmitting(true);
    try {
      await trainingService.createSubject(request);
      toast.success('Subject created.');
      setShowForm(false);
      await load();
    } catch (cause) {
      setFormError(displayError(cause, 'Unable to create the subject.'));
    } finally {
      setSubmitting(false);
    }
  }

  if (loading && subjects.length === 0) return <Panel padded><ListSkeleton rows={4} /></Panel>;

  if (error) {
    return (
      <Panel padded>
        <EmptyState
          icon="alert-triangle"
          title="Unable to load the subject library"
          description={error}
          action={<Button size="sm" icon="refresh" onClick={load}>Try again</Button>}
        />
      </Panel>
    );
  }

  return (
    <PageSection
      title={`Subject library (${subjects.length})`}
      description="Standard subjects for building training courses."
      actions={
        <Button variant="primary" size="sm" onClick={() => setShowForm((open) => !open)}>
          {showForm ? 'Close form' : 'Create subject'}
        </Button>
      }
    >
      {showForm && (
        <SubjectForm submitting={submitting} error={formError} onSubmit={createSubject} onCancel={() => setShowForm(false)} />
      )}

      {subjects.length === 0 ? (
        <Panel padded>
          <EmptyState icon="clipboard" title="No subjects yet" description="Create your first subject to start building a training course." />
        </Panel>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
          {subjects.map((subject) => <SubjectCard key={subject.id} subject={subject} />)}
        </div>
      )}
    </PageSection>
  );
}
