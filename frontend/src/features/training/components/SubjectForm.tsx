'use client';

import { useState } from 'react';
import { Button, FormField, Input, Notice, Panel, SectionTitle, Select, Textarea } from '@/components/ui';
import { useTrainingRules } from '../hooks/useTrainingRules';
import { CATEGORY_OPTIONS, INTENSITY_OPTIONS, SURFACE_OPTIONS } from '../lib/subjectOptions';
import type { CreateSubjectRequest, IntensityLevel, SubjectCategory, SurfaceType } from '../types';

/**
 * Form that creates an exercise. The duration limits come from the backend rules and only configure the field;
 * the backend still validates the request, and its message is shown when it refuses.
 */
export function SubjectForm({
  submitting,
  error,
  onSubmit,
  onCancel,
}: {
  submitting: boolean;
  error: string | null;
  onSubmit: (request: CreateSubjectRequest) => void;
  onCancel: () => void;
}) {
  const rules = useTrainingRules();

  const [category, setCategory] = useState<SubjectCategory>('ENDURANCE');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [surfaceType, setSurfaceType] = useState<SurfaceType>('TURF');
  const [targetDistanceMeters, setTargetDistanceMeters] = useState(1000);
  const [intensityLevel, setIntensityLevel] = useState<IntensityLevel>('MEDIUM');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [validation, setValidation] = useState<string | null>(null);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setValidation('Enter an exercise name.');
      return;
    }
    setValidation(null);
    onSubmit({
      category,
      name: name.trim(),
      description: description.trim() || undefined,
      surfaceType,
      targetDistanceMeters: Number(targetDistanceMeters),
      intensityLevel,
      durationMinutes: Number(durationMinutes),
    });
  }

  const message = validation ?? error;

  return (
    <Panel padded>
      <form onSubmit={handleSubmit} className="space-y-4">
        <SectionTitle>Create exercise</SectionTitle>
        {message && <Notice tone="error">{message}</Notice>}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <FormField label="Exercise name" required>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Gallop 1,200 m" />
          </FormField>

          <FormField label="Category" required>
            <Select value={category} onChange={(e) => setCategory(e.target.value as SubjectCategory)}>
              {CATEGORY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </Select>
          </FormField>

          <FormField label="Track surface">
            <Select value={surfaceType} onChange={(e) => setSurfaceType(e.target.value as SurfaceType)}>
              {SURFACE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </Select>
          </FormField>

          <FormField label="Intensity">
            <Select value={intensityLevel} onChange={(e) => setIntensityLevel(e.target.value as IntensityLevel)}>
              {INTENSITY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </Select>
          </FormField>

          <FormField label="Target distance (metres)">
            <Input
              type="number"
              min={100}
              step={50}
              value={targetDistanceMeters}
              onChange={(e) => setTargetDistanceMeters(Number(e.target.value))}
            />
          </FormField>

          <FormField
            label="Estimated duration (minutes)"
            required
            hint={rules
              ? `Between ${rules.minSubjectDurationMinutes} and ${rules.maxSubjectDurationMinutes} minutes. It sets the length of the lot.`
              : 'It sets the length of the lot.'}
          >
            <Input
              type="number"
              step={5}
              min={rules?.minSubjectDurationMinutes}
              max={rules?.maxSubjectDurationMinutes}
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(Number(e.target.value))}
            />
          </FormField>
        </div>

        <FormField label="Description">
          <Textarea
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Technical notes or instructions for the groom…"
          />
        </FormField>

        <div className="flex justify-end gap-2">
          <Button type="button" onClick={onCancel} disabled={submitting}>Cancel</Button>
          <Button type="submit" variant="primary" loading={submitting}>Save exercise</Button>
        </div>
      </form>
    </Panel>
  );
}
