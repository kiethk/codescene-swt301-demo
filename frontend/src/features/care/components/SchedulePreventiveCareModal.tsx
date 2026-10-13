'use client';

import { useState } from 'react';
import { Button, FormField, Input, Modal, Notice, Select, Textarea } from '@/components/ui';
import { careService } from '../services/careService';
import { displayError } from '@/lib/display';
import type { Horse } from '@/features/stable/types';

interface SchedulePreventiveCareModalProps {
  open: boolean;
  onClose: () => void;
  horses: Horse[];
  initialHorseId?: number;
  onSuccess: () => void;
}

export function SchedulePreventiveCareModal({
  open,
  onClose,
  horses,
  initialHorseId,
  onSuccess,
}: SchedulePreventiveCareModalProps) {
  const [selectedHorseId, setSelectedHorseId] = useState<number | ''>(
    initialHorseId ?? (horses.length > 0 ? horses[0].id : ''),
  );
  const [careType, setCareType] = useState<'ROUTINE_EXAM' | 'VACCINATION' | 'DEWORMING'>('VACCINATION');
  const [scheduledDate, setScheduledDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().slice(0, 10);
  });
  const [description, setDescription] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedHorseId) {
      setError('Please select a horse.');
      return;
    }
    if (!scheduledDate) {
      setError('Please choose a scheduled date.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await careService.createPreventiveCare({
        horseId: Number(selectedHorseId),
        careType,
        scheduledDate,
        description: description.trim() || undefined,
      });
      onSuccess();
      onClose();
    } catch (err) {
      setError(displayError(err, 'Failed to schedule preventive care.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Schedule Preventive Care"
      description="Plan proactive equine health measures including vaccines, deworming, and routine exams."
      size="md"
      dismissible={!loading}
      footer={
        <div className="flex justify-end gap-2.5">
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSubmit} loading={loading}>
            Schedule Care
          </Button>
        </div>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-3.5">
        {error && (
          <Notice tone="error" title="Submission Error" onDismiss={() => setError(null)}>
            {error}
          </Notice>
        )}

        <FormField label="Select Horse" required>
          {horses.length > 0 ? (
            <Select
              value={selectedHorseId}
              onChange={(e) => setSelectedHorseId(Number(e.target.value))}
              disabled={loading}
            >
              {horses.map((h) => (
                <option key={h.id} value={h.id}>
                  {h.name} {h.breed ? `(${h.breed})` : ''} - #{h.id}
                </option>
              ))}
            </Select>
          ) : (
            <Input
              type="number"
              placeholder="Enter Horse ID"
              value={selectedHorseId}
              onChange={(e) => setSelectedHorseId(e.target.value ? Number(e.target.value) : '')}
              disabled={loading}
            />
          )}
        </FormField>

        <FormField label="Care Category" required>
          <Select
            value={careType}
            onChange={(e) =>
              setCareType(e.target.value as 'ROUTINE_EXAM' | 'VACCINATION' | 'DEWORMING')
            }
            disabled={loading}
          >
            <option value="VACCINATION">Vaccination (Tetanus, Influenza, West Nile, etc.)</option>
            <option value="DEWORMING">Deworming (Anthelmintic Protocol)</option>
            <option value="ROUTINE_EXAM">Routine Equine Examination</option>
          </Select>
        </FormField>

        <FormField label="Scheduled Date" required>
          <Input
            type="date"
            value={scheduledDate}
            onChange={(e) => setScheduledDate(e.target.value)}
            disabled={loading}
          />
        </FormField>

        <FormField label="Clinical Description / Medication Notes">
          <Textarea
            placeholder="e.g. Annual booster vaccination protocol. Inspect oral cavity and body condition."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={loading}
            rows={3}
          />
        </FormField>
      </form>
    </Modal>
  );
}
