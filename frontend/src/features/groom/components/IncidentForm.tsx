'use client';

import { ChoiceInput, FileInput, Input, Select, Textarea } from '@/components/ui/Input';
import { FormField } from '@/components/ui/FormField';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { Button, IconButton } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Panel';
import { Icon } from '@/components/ui/Icon';
import { incidentApi } from '../services/incidentService';
import { stableApi } from '@/features/stable/services/stableService';
import type { Horse, StableStall } from '@/features/stable/types';
import type { IncidentReport, IncidentSeverity } from '../types';

const SEVERITY_OPTIONS: { value: IncidentSeverity; label: string; hint: string; tone: string }[] = [
  { value: 'LOW', label: 'Minor', hint: 'Monitor the horse; no urgent intervention is required.', tone: 'text-[var(--color-text-secondary)]' },
  { value: 'MEDIUM', label: 'Moderate', hint: 'Veterinary review is needed today.', tone: 'text-[var(--color-info)]' },
  { value: 'HIGH', label: 'Major', hint: 'Request veterinary attention as soon as possible.', tone: 'text-[var(--color-warning)]' },
  { value: 'CRITICAL', label: 'Critical', hint: 'A health emergency requiring an immediate call.', tone: 'text-[var(--color-danger)] font-semibold' },
];

export function IncidentForm({ initialHorseId }: { initialHorseId?: number } = {}) {
  const router = useRouter();
  const { user } = useAuth();

  const [loadingInitial, setLoadingInitial] = useState(true);
  const [horses, setHorses] = useState<Horse[]>([]);
  const [stalls, setStalls] = useState<StableStall[]>([]);

  const [horseId, setHorseId] = useState<number | ''>('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState<IncidentSeverity>('MEDIUM');
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadData() {
      try {
        setLoadingInitial(true);
        const [stallList, horseList] = await Promise.all([
          stableApi.getStalls(),
          stableApi.getHorses(),
        ]);

        setStalls(stallList);

        // Keep only horses assigned to stalls managed by the current groom.
        const myStallIds = stallList
          .filter((s) => s.groomId === user?.userId)
          .map((s) => s.id);

        const myHorses = horseList.filter(
          (h) => h.currentStallId && myStallIds.includes(h.currentStallId)
        );

        setHorses(myHorses);
        if (myHorses.length > 0) {
          // Opened from a stall on the Stable screen: start on that horse when it is one of the Groom's own.
          const preselected = myHorses.find((h) => h.id === initialHorseId);
          setHorseId((preselected ?? myHorses[0]).id);
        }
      } catch (err) {
        console.error('Unable to load horse and stall data:', err);
        setError('Unable to load the horse list. Please reload the page.');
      } finally {
        setLoadingInitial(false);
      }
    }

    if (user?.userId) {
      loadData();
    }
  }, [user?.userId, initialHorseId]);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0] ?? null;
    if (f) {
      if (f.size > 10 * 1024 * 1024) {
        setError('The image must not exceed 10 MB.');
        return;
      }
      setFile(f);
      const url = URL.createObjectURL(f);
      setPreviewUrl(url);
    } else {
      setFile(null);
      setPreviewUrl(null);
    }
  }

  function handleRemoveFile() {
    setFile(null);
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
      setPreviewUrl(null);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!horseId) {
      setError('Please select the horse involved in the incident.');
      return;
    }
    if (!title.trim()) {
      setError('Please enter an incident title.');
      return;
    }
    if (!description.trim()) {
      setError('Please enter a detailed incident description.');
      return;
    }

    setError(null);
    setSubmitting(true);
    let created: IncidentReport | null = null;

    try {
      // Step 1: create the report and receive its id.
      created = await incidentApi.create({
        horseId: Number(horseId),
        title: title.trim(),
        description: description.trim(),
        severity,
      });

      // Step 2: attach the image when one was selected.
      if (file) {
        await incidentApi.uploadImage(created.id, file);
      }

      router.push('/groom/incidents');
    } catch (err) {
      console.error('Unable to submit incident report:', err);
      setError(
        created
          ? `Report #${created.id} was submitted, but the image upload failed. Open the report to try again.`
          : (err instanceof Error ? err.message : 'Failed to submit the incident report.')
      );
    } finally {
      setSubmitting(false);
    }
  }

  const stallMap = new Map<number, string>();
  for (const s of stalls) {
    stallMap.set(s.id, s.stallCode || `Stall #${s.stallNumber}`);
  }

  if (loadingInitial) {
    return (
      <Panel padded>
        <div className="flex items-center justify-center py-12 text-sm text-[var(--color-text-secondary)]">
          Loading stall and horse information...
        </div>
      </Panel>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-[var(--color-text-primary)]">
            Incident Report
          </h1>
          <p className="text-xs text-[var(--color-text-secondary)]">
            Report unusual symptoms or injuries to the veterinary team.
          </p>
        </div>
        <Link
          href="/groom/incidents"
          className="inline-flex items-center gap-1.5 text-xs text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
        >
          <Icon name="arrow-left" size={14} /> Back to reports
        </Link>
      </div>

      {error && (
        <div className="rounded-[var(--radius-md)] border border-[var(--color-danger)] bg-[var(--color-danger-soft)] p-3 text-sm text-[var(--color-danger)]">
          {error}
        </div>
      )}

      {horses.length === 0 ? (
        <Panel padded>
          <div className="py-8 text-center text-sm text-[var(--color-text-secondary)]">
            You are not currently assigned to any stalls or horses.
            <div className="mt-4">
              <Link href="/groom/incidents">
                <Button variant="secondary">Back</Button>
              </Link>
            </div>
          </div>
        </Panel>
      ) : (
        <Panel padded>
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Horse selection */}
            <div>
              <FormField label="Horse involved in the incident" required>
                <Select
                  value={horseId}
                  onChange={(e) => setHorseId(Number(e.target.value))}
                  required
                >
                  {horses.map((h) => {
                    const stallLabel = h.currentStallId
                      ? stallMap.get(h.currentStallId) || `Stall #${h.currentStallId}`
                      : 'No stall assigned';
                    return (
                      <option key={h.id} value={h.id}>
                        {h.name} (#{h.id}) — {stallLabel}
                      </option>
                    );
                  })}
                </Select>
              </FormField>
              <p className="mt-1 text-xs text-[var(--color-text-muted)]">
                Only horses in your assigned stalls are shown.
              </p>
            </div>

            {/* Severity */}
            <div>
              <label className="block text-xs font-medium text-[var(--color-text-secondary)]">
                Severity <span className="text-[var(--color-danger)]">*</span>
              </label>
              <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
                {SEVERITY_OPTIONS.map((opt) => (
                  <label
                    key={opt.value}
                    className={`flex cursor-pointer items-start gap-2.5 rounded-[var(--radius-md)] border p-2.5 transition-colors ${
                      severity === opt.value
                        ? 'border-[var(--color-primary)] bg-[var(--color-primary-soft)]'
                        : 'border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-border)]'
                    }`}
                  >
                    <ChoiceInput
                      type="radio"
                      name="severity"
                      value={opt.value}
                      checked={severity === opt.value}
                      onChange={() => setSeverity(opt.value)}
                    />
                    <div>
                      <div className={`text-xs ${opt.tone}`}>{opt.label}</div>
                      <div className="text-xs text-[var(--color-text-muted)]">{opt.hint}</div>
                    </div>
                  </label>
                ))}
              </div>

              {severity === 'CRITICAL' && (
                <div className="mt-2.5 rounded-[var(--radius-md)] border border-[var(--color-danger)] bg-[var(--color-danger-soft)] p-2.5 text-xs text-[var(--color-danger)]">
                  <div className="font-semibold">Emergency warning:</div>
                  The system does not send instant notifications. For a <strong>Critical</strong> incident,{' '}
                  <strong>call the veterinary team directly</strong> or notify a manager immediately after submitting this report.
                </div>
              )}
            </div>

            {/* Title */}
            <div>
              <FormField label="Incident title" required>
                <Input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Example: Swollen right front knee, missed breakfast..."
                  required
                />
              </FormField>
            </div>

            {/* Detailed description */}
            <div>
              <FormField label="Detailed description" required>
                <Textarea
                  rows={4}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Describe the symptoms, time discovered, horse behavior, and pain or injury location..."
                  required
                />
              </FormField>
            </div>

            {/* Optional image attachment */}
            <div>
              <label className="block text-xs font-medium text-[var(--color-text-secondary)]">
                Scene or injury image (optional)
              </label>
              <div className="mt-1.5 flex flex-col gap-2">
                <FileInput
                  accept="image/jpeg,image/png,image/webp"
                  onChange={handleFileChange}
                />
                <p className="text-xs text-[var(--color-text-muted)]">
                  JPEG, PNG, and WebP only. Maximum size: 10 MB.
                </p>

                {previewUrl && (
                  <div className="relative mt-2 inline-block max-w-xs">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={previewUrl}
                      alt="Image preview"
                      className="max-h-48 rounded-[var(--radius-md)] border border-[var(--color-border)] object-cover"
                    />
                    <IconButton icon="x" size="sm" variant="secondary" label="Remove image" className="absolute right-2 top-2 rounded-full" onClick={handleRemoveFile} />
                  </div>
                )}
              </div>
            </div>

            {/* Form actions */}
            <div className="flex items-center justify-end gap-3 pt-3">
              <Link href="/groom/incidents">
                <Button type="button" variant="secondary" disabled={submitting}>
                  Cancel
                </Button>
              </Link>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? 'Submitting report...' : 'Submit incident report'}
              </Button>
            </div>
          </form>
        </Panel>
      )}
    </div>
  );
}
