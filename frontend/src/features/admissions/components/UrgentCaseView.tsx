'use client';

import { Notice } from '@/components/ui/Notice';
import { ChoiceInput, Input, Textarea } from '@/components/ui/Input';
import { FormField } from '@/components/ui/FormField';
import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '@/services/api';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { Panel } from '@/components/ui/Panel';
import { Pill } from '@/components/ui/StatusBadge';
import { ProtectedImage } from '@/components/ui/ProtectedImage';
import { admissionsApi } from '../services/api';
import { displayError } from '@/lib/display';
import type {
  CompleteCareScheduleRequest,
  HorseHealthMetricRequest,
  TrainingDecision,
  UrgentAssignmentAlert,
} from '../types';

/** Date in Vietnam time, plus dayOffset days, as yyyy-MM-dd (matches the backend). */
function businessDate(dayOffset: number) {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
  const [year, month, day] = today.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + dayOffset)).toISOString().slice(0, 10);
}

function newIdempotencyKey() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `follow-up-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function formatDateTime(value: string | null) {
  if (!value) return 'Not specified';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function UrgentCaseView({ scheduleId }: { scheduleId: number }) {
  const [urgentCase, setUrgentCase] = useState<UrgentAssignmentAlert | null>(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [completedData, setCompletedData] = useState<CompleteCareScheduleRequest | null>(null);

  // Form states
  const [symptoms, setSymptoms] = useState('');
  const [findings, setFindings] = useState('');
  const [diagnosis, setDiagnosis] = useState('');
  const [treatment, setTreatment] = useState('');
  const [trainingDecision, setTrainingDecision] = useState<TrainingDecision>('BLOCKED');
  const [restrictionDetails, setRestrictionDetails] = useState('');
  // Blocking training requires a follow-up exam — "rest until" is that exam's date.
  const [followUpDate, setFollowUpDate] = useState(() => businessDate(3));
  const [followUpDescription, setFollowUpDescription] = useState('Follow-up after the urgent examination');
  const [followUpKey] = useState(newIdempotencyKey);
  const [notes, setNotes] = useState('');
  const [temperature, setTemperature] = useState('');
  const [heartRate, setHeartRate] = useState('');
  const [respiratoryRate, setRespiratoryRate] = useState('');
  const [weight, setWeight] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    let active = true;
    admissionsApi
      .getUrgentCase(scheduleId)
      .then((data) => {
        if (!active) return;
        setUrgentCase(data);
        if (data.description) {
          setSymptoms((prev) => prev || data.description);
        }
      })
      .catch((cause: unknown) => {
        if (!active) return;
      setError(cause instanceof ApiError ? displayError(cause, 'Unable to load the urgent case.') : 'Unable to load the urgent case.');
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [scheduleId]);

  async function startExam() {
    if (!urgentCase || urgentCase.status !== 'SCHEDULED') return;
    setStarting(true);
    setError('');
    try {
      const schedule = await admissionsApi.startCareSchedule(urgentCase.scheduleId);
      setUrgentCase((current) => (current ? { ...current, status: schedule.status } : current));
    } catch (cause) {
      setError(cause instanceof ApiError ? displayError(cause, 'Unable to start the examination.') : 'Unable to start the examination.');
    } finally {
      setStarting(false);
    }
  }

  function validateForm(): boolean {
    const errors: Record<string, string> = {};

    if (!symptoms.trim()) {
      errors.symptoms = 'Symptoms are required for an urgent examination.';
    }
    if (!findings.trim()) {
      errors.findings = 'Examination findings are required.';
    }
    if (!diagnosis.trim()) {
      errors.diagnosis = 'A clinical diagnosis is required.';
    }
    if (trainingDecision === 'BLOCKED') {
      if (!restrictionDetails.trim()) {
      errors.restrictionDetails = 'Restriction details are required when training is blocked.';
      }
      if (!followUpDate) {
      errors.followUpDate = 'Select a follow-up date when training is blocked.';
      } else if (followUpDate < businessDate(1)) {
      errors.followUpDate = 'The follow-up date must be tomorrow or later.';
      }
      if (!followUpDescription.trim()) {
      errors.followUpDescription = 'A follow-up description is required.';
      }
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!urgentCase) return;

    if (!validateForm()) {
      setSubmitError('Check the required fields and try again.');
      return;
    }

    setSubmitting(true);
    setSubmitError('');

    const metrics: HorseHealthMetricRequest = {};
    if (temperature.trim()) metrics.temperature = Number(temperature);
    if (heartRate.trim()) metrics.heartRate = Number(heartRate);
    if (respiratoryRate.trim()) metrics.respiratoryRate = Number(respiratoryRate);
    if (weight.trim()) metrics.weight = Number(weight);

    const payload: CompleteCareScheduleRequest = {
      symptoms: symptoms.trim(),
      findings: findings.trim(),
      diagnosis: diagnosis.trim(),
      treatment: treatment.trim() || undefined,
      trainingDecision,
      restrictionDetails: restrictionDetails.trim() || undefined,
      notes: notes.trim() || undefined,
      metrics: Object.keys(metrics).length > 0 ? [metrics] : undefined,
      nextSchedule:
        trainingDecision === 'BLOCKED'
          ? {
              horseId: urgentCase.horseId,
              careType: 'ROUTINE',
              scheduledDate: followUpDate,
              description: followUpDescription.trim(),
              idempotencyKey: followUpKey,
            }
          : undefined,
    };

    try {
      await admissionsApi.completeCareSchedule(urgentCase.scheduleId, payload);
      setCompletedData(payload);
      setUrgentCase((current) => (current ? { ...current, status: 'COMPLETED' } : current));
    } catch (cause) {
      setSubmitError(
        cause instanceof ApiError
          ? cause.message
          : 'Unable to complete the examination. Your entries have been kept; please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <Panel className="p-8 text-sm text-[var(--color-text-secondary)]">Loading urgent case…</Panel>;
  }
  if (!urgentCase) {
    return (
      <Panel className="border-[var(--color-danger)] p-8 text-sm text-[var(--color-danger)]">
        {error || 'Urgent case not found.'}
      </Panel>
    );
  }

  return (
    <div className="mx-auto w-full max-w-4xl space-y-5">
      <Panel className="overflow-hidden border-2 border-[var(--color-danger)] p-0">
        <header className="flex flex-wrap items-start gap-4 border-b border-[var(--color-danger)]/30 bg-[var(--color-danger-soft)] p-6">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-danger)] text-[var(--color-text-inverse)]">
            <Icon name="alert-triangle" size={24} />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[var(--color-danger)]">
            Urgent case #{urgentCase.scheduleId}
            </p>
            <h1 className="mt-1 text-2xl font-bold text-[var(--color-text-primary)]">{urgentCase.title}</h1>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            You have been assigned directly to this case.
            </p>
          </div>
          <div className="flex gap-2">
            <Pill tone="danger">{urgentCase.severity}</Pill>
            <Pill tone={urgentCase.status === 'COMPLETED' ? 'success' : 'info'}>{urgentCase.status}</Pill>
          </div>
        </header>

        <div className="space-y-5 p-6">
          <dl className="grid gap-4 rounded-[var(--radius-md)] bg-[var(--color-surface-muted)] p-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs font-semibold uppercase text-[var(--color-text-muted)]">Horse</dt>
              <dd className="mt-1 font-bold">
                {urgentCase.horseName} · #{urgentCase.horseId}
              </dd>
            </div>
            <div>
            <dt className="text-xs font-semibold uppercase text-[var(--color-text-muted)]">Stall / Location</dt>
              <dd className="mt-1 font-semibold">
            {[urgentCase.stallCode, urgentCase.stableLocation].filter(Boolean).join(' · ') || 'Not provided'}
              </dd>
            </div>
            <div>
            <dt className="text-xs font-semibold uppercase text-[var(--color-text-muted)]">Reported by</dt>
              <dd className="mt-1 font-semibold">
            {urgentCase.reportedByName || 'Unknown'} · #{urgentCase.reportedById}
              </dd>
            </div>
            <div>
            <dt className="text-xs font-semibold uppercase text-[var(--color-text-muted)]">Reported at</dt>
              <dd className="mt-1 font-semibold">{formatDateTime(urgentCase.reportedAt)}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase text-[var(--color-text-muted)]">
            Assigned at
              </dt>
              <dd className="mt-1 font-semibold">{formatDateTime(urgentCase.assignedAt)}</dd>
            </div>
            <div>
            <dt className="text-xs font-semibold uppercase text-[var(--color-text-muted)]">Training decision</dt>
              <dd className="mt-1 font-bold text-[var(--color-danger)]">
            {urgentCase.trainingDecision} — Training is paused until the examination is complete.
              </dd>
            </div>
          </dl>

          <section className="rounded-[var(--radius-md)] border border-[var(--color-border)] p-4">
          <h2 className="text-sm font-bold text-[var(--color-text-primary)]">Symptoms / Initial description</h2>
            <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-[var(--color-text-secondary)]">
              {urgentCase.description}
            </p>
          </section>

          {urgentCase.imageUrl && (
            <ProtectedImage
              src={urgentCase.imageUrl}
              alt="Urgent case report"
              openOnClick
              className="max-h-[28rem] w-full object-contain"
            />
          )}

          {error && (
            <p role="alert" className="text-sm font-medium text-[var(--color-danger)]">
              {error}
            </p>
          )}

          {/* Workflow Transitions */}
          {urgentCase.status === 'SCHEDULED' && (
            <div className="pt-2">
              <Button variant="destructive" icon="activity" disabled={starting} onClick={startExam}>
                {starting ? 'Starting…' : 'Start examination'}
              </Button>
            </div>
          )}

          {urgentCase.status === 'IN_PROGRESS' && (
            <form onSubmit={handleSubmit} className="space-y-5 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6">
              <div className="border-b border-[var(--color-border)] pb-3">
                <h3 className="text-lg font-bold text-[var(--color-text-primary)]">
                  Urgent Examination Record
                </h3>
                <p className="text-xs text-[var(--color-text-secondary)]">
                  Record clinical findings and the medical training decision for this urgent case.
                </p>
              </div>

              {submitError && (
                <Notice tone="error">{submitError}</Notice>
              )}

              {/* Vitals Telemetry */}
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-text-secondary)] mb-2">
                  1. Clinical Vitals
                </label>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <div>
                    <FormField label="Temperature (°C)" htmlFor="vital-temp">
                      <Input
                        id="vital-temp"
                        type="number"
                        step="0.1"
                        placeholder="38.0"
                        value={temperature}
                        onChange={(e) => setTemperature(e.target.value)}
                      />
                    </FormField>
                  </div>
                  <div>
                    <FormField label="Heart rate (bpm)" htmlFor="vital-hr">
                      <Input
                        id="vital-hr"
                        type="number"
                        step="1"
                        placeholder="36"
                        value={heartRate}
                        onChange={(e) => setHeartRate(e.target.value)}
                      />
                    </FormField>
                  </div>
                  <div>
                    <FormField label="Respiratory rate (rpm)" htmlFor="vital-rr">
                      <Input
                        id="vital-rr"
                        type="number"
                        step="1"
                        placeholder="12"
                        value={respiratoryRate}
                        onChange={(e) => setRespiratoryRate(e.target.value)}
                      />
                    </FormField>
                  </div>
                  <div>
                    <FormField label="Weight (kg)" htmlFor="vital-wt">
                      <Input
                        id="vital-wt"
                        type="number"
                        step="0.5"
                        placeholder="500"
                        value={weight}
                        onChange={(e) => setWeight(e.target.value)}
                      />
                    </FormField>
                  </div>
                </div>
              </div>

              {/* Clinical Details */}
              <div className="space-y-4">
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-text-secondary)]">
                  2. Examination & Diagnosis
                </label>

                <div>
                  <FormField label="Observed urgent symptoms" htmlFor="urgent-symptoms" required>
                    <Textarea
                      id="urgent-symptoms"
                      rows={2}
                      value={symptoms}
                      onChange={(e) => {
                        setSymptoms(e.target.value);
                        if (fieldErrors.symptoms) setFieldErrors((prev) => ({ ...prev, symptoms: '' }));
                      }}
                    placeholder="Describe the horse’s symptoms or signs of illness or injury…"
                    />
                  </FormField>
                  {fieldErrors.symptoms && (
                    <p className="mt-1 text-xs text-[var(--color-danger)]">{fieldErrors.symptoms}</p>
                  )}
                </div>

                <div>
                  <FormField label="Examination findings" htmlFor="urgent-findings" required>
                    <Textarea
                      id="urgent-findings"
                      rows={3}
                      value={findings}
                      onChange={(e) => {
                        setFindings(e.target.value);
                        if (fieldErrors.findings) setFieldErrors((prev) => ({ ...prev, findings: '' }));
                      }}
                    placeholder="Describe the horse’s condition, injuries, or areas of pain…"
                    />
                  </FormField>
                  {fieldErrors.findings && (
                    <p className="mt-1 text-xs text-[var(--color-danger)]">{fieldErrors.findings}</p>
                  )}
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <FormField label="Diagnosis" htmlFor="urgent-diagnosis" required>
                      <Input
                        id="urgent-diagnosis"
                        type="text"
                        value={diagnosis}
                        onChange={(e) => {
                          setDiagnosis(e.target.value);
                          if (fieldErrors.diagnosis) setFieldErrors((prev) => ({ ...prev, diagnosis: '' }));
                        }}
                    placeholder="Enter a confirmed or provisional diagnosis…"
                      />
                    </FormField>
                    {fieldErrors.diagnosis && (
                      <p className="mt-1 text-xs text-[var(--color-danger)]">{fieldErrors.diagnosis}</p>
                    )}
                  </div>

                  <div>
                    <FormField label="Treatment / First aid" htmlFor="urgent-treatment">
                      <Input
                        id="urgent-treatment"
                        type="text"
                        value={treatment}
                        onChange={(e) => setTreatment(e.target.value)}
                    placeholder="Medication, bandaging, or care instructions…"
                      />
                    </FormField>
                  </div>
                </div>

                <div>
                  <FormField label="Additional notes" htmlFor="urgent-notes">
                    <Textarea
                      id="urgent-notes"
                      rows={2}
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                    placeholder="Care instructions or follow-up observations…"
                    />
                  </FormField>
                </div>
              </div>

              {/* Training Clearance Decision */}
              <div className="space-y-3 border-t border-[var(--color-border)] pt-4">
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-text-secondary)]">
                  3. Training Decision <span className="text-[var(--color-danger)]">*</span>
                </label>
                <div className="grid gap-3 sm:grid-cols-2">
                  {[
                    { value: 'ALLOWED' as const, label: 'ALLOWED', desc: 'Normal training may continue' },
                    {
                      value: 'BLOCKED' as const,
                      label: 'BLOCKED',
                      desc: 'Rest until the follow-up examination; upcoming workouts will be cancelled',
                    },
                  ].map((option) => (
                    <label
                      key={option.value}
                      className={`flex cursor-pointer flex-col rounded-[var(--radius-md)] border p-3 transition-colors ${
                        trainingDecision === option.value
                          ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)]'
                          : 'border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-muted)]'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <ChoiceInput
                          type="radio"
                          name="trainingDecision"
                          value={option.value}
                          checked={trainingDecision === option.value}
                          onChange={() => {
                            setTrainingDecision(option.value);
                            if (option.value === 'ALLOWED') {
                              setFieldErrors((prev) => ({
                                ...prev,
                                restrictionDetails: '',
                                followUpDate: '',
                                followUpDescription: '',
                              }));
                            }
                          }}
                        />
                        <span className="text-xs font-bold">{option.label}</span>
                      </div>
                      <p className="mt-1 text-xs text-[var(--color-text-secondary)]">{option.desc}</p>
                    </label>
                  ))}
                </div>

                {trainingDecision === 'BLOCKED' && (
                  <div className="mt-3 space-y-3">
                    <div>
                      <FormField label="Training restriction / Rest instructions" htmlFor="restriction-details" required>
                        <Textarea
                          id="restriction-details"
                          rows={2}
                          value={restrictionDetails}
                          onChange={(e) => {
                            setRestrictionDetails(e.target.value);
                            if (fieldErrors.restrictionDetails) {
                              setFieldErrors((prev) => ({ ...prev, restrictionDetails: '' }));
                            }
                          }}
                          placeholder="Describe the restrictions (for example, 15 minutes of walking only or complete stall rest)…"
                        />
                      </FormField>
                      {fieldErrors.restrictionDetails && (
                        <p className="mt-1 text-xs text-[var(--color-danger)]">{fieldErrors.restrictionDetails}</p>
                      )}
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                      <div>
                        <FormField label="Follow-up date" htmlFor="follow-up-date" required>
                          <Input
                            id="follow-up-date"
                            type="date"
                            min={businessDate(1)}
                            value={followUpDate}
                            onChange={(e) => {
                              setFollowUpDate(e.target.value);
                              if (fieldErrors.followUpDate) setFieldErrors((prev) => ({ ...prev, followUpDate: '' }));
                            }}
                          />
                        </FormField>
                        <div className="mt-1 flex gap-1">
                          {[3, 7, 14].map((days) => (
                            <Button
                              key={days}
                              size="sm"
                              variant="secondary"
                              onClick={() => setFollowUpDate(businessDate(days))}
                            >
                              +{days} days
                            </Button>
                          ))}
                        </div>
                        {fieldErrors.followUpDate && (
                          <p className="mt-1 text-xs text-[var(--color-danger)]">{fieldErrors.followUpDate}</p>
                        )}
                      </div>
                      <div>
                        <FormField label="Follow-up instructions" htmlFor="follow-up-desc" required>
                          <Input
                            id="follow-up-desc"
                            type="text"
                            value={followUpDescription}
                            onChange={(e) => {
                              setFollowUpDescription(e.target.value);
                              if (fieldErrors.followUpDescription) {
                                setFieldErrors((prev) => ({ ...prev, followUpDescription: '' }));
                              }
                            }}
                          />
                        </FormField>
                        {fieldErrors.followUpDescription && (
                          <p className="mt-1 text-xs text-[var(--color-danger)]">{fieldErrors.followUpDescription}</p>
                        )}
                      </div>
                    </div>
                    <p className="text-xs text-[var(--color-text-secondary)]">
                      The horse will rest until this follow-up examination. The trainer will see the reason and date when creating a plan.
                    </p>
                  </div>
                )}
              </div>

              <div className="border-t border-[var(--color-border)] pt-4">
                <Button type="submit" variant="primary" loading={submitting} icon="check" className="w-full sm:w-auto">
                  Complete urgent examination
                </Button>
              </div>
            </form>
          )}

          {urgentCase.status === 'COMPLETED' && (
            <div className="space-y-4 rounded-[var(--radius-md)] border border-[var(--color-success)] bg-[var(--color-success-soft)] p-6">
              <div className="flex items-center gap-3 text-[var(--color-success)]">
                <Icon name="check" size={24} />
                <h3 className="text-base font-bold">Urgent examination completed successfully</h3>
              </div>
              <p className="text-xs text-[var(--color-text-secondary)]">
                The medical record and training decision have been saved.
              </p>
              {completedData && (
                <dl className="grid gap-3 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="font-semibold text-[var(--color-text-muted)]">Diagnosis</dt>
                    <dd className="mt-1 font-bold text-[var(--color-text-primary)]">{completedData.diagnosis}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold text-[var(--color-text-muted)]">Training decision</dt>
                    <dd className="mt-1 font-bold text-[var(--color-danger)]">{completedData.trainingDecision}</dd>
                  </div>
                  <div className="sm:col-span-2">
                    <dt className="font-semibold text-[var(--color-text-muted)]">Examination findings</dt>
                    <dd className="mt-1 text-[var(--color-text-secondary)]">{completedData.findings}</dd>
                  </div>
                  {completedData.restrictionDetails && (
                    <div className="sm:col-span-2">
                      <dt className="font-semibold text-[var(--color-text-muted)]">Training restriction</dt>
                      <dd className="mt-1 text-[var(--color-danger)]">{completedData.restrictionDetails}</dd>
                    </div>
                  )}
                  {completedData.nextSchedule && (
                    <div className="sm:col-span-2">
                      <dt className="font-semibold text-[var(--color-text-muted)]">Follow-up</dt>
                      <dd className="mt-1 text-[var(--color-text-primary)]">
                        {completedData.nextSchedule.scheduledDate} — {completedData.nextSchedule.description}
                      </dd>
                    </div>
                  )}
                </dl>
              )}
            </div>
          )}
        </div>
      </Panel>
    </div>
  );
}
