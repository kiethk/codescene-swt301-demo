'use client';

import { useState } from 'react';
import { Button, FormField, Input, Modal, Notice, Select, Textarea } from '@/components/ui';
import { careService } from '../services/careService';
import { displayError } from '@/lib/display';

interface TreatmentPlanModalProps {
  open: boolean;
  onClose: () => void;
  healthRecordId: number;
  horseName?: string;
  onSuccess: () => void;
}

export function TreatmentPlanModal({
  open,
  onClose,
  healthRecordId,
  horseName,
  onSuccess,
}: TreatmentPlanModalProps) {
  const [treatmentName, setTreatmentName] = useState('');
  const [instructions, setInstructions] = useState('');
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [endDate, setEndDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 14);
    return d.toISOString().slice(0, 10);
  });

  // Initial Prescription
  const [includePrescription, setIncludePrescription] = useState(false);
  const [medicationName, setMedicationName] = useState('');
  const [dosage, setDosage] = useState('');
  const [frequency, setFrequency] = useState('Once daily');
  const [route, setRoute] = useState('Oral');
  const [prescriptionInstructions, setPrescriptionInstructions] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!treatmentName.trim()) {
      setError('Treatment plan name is required.');
      return;
    }
    if (!instructions.trim()) {
      setError('Clinical instructions are required.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const plan = await careService.createTreatmentPlan(healthRecordId, {
        treatmentName: treatmentName.trim(),
        instructions: instructions.trim(),
        startDate,
        endDate,
      });

      if (includePrescription && medicationName.trim() && dosage.trim()) {
        await careService.createPrescription(plan.id, {
          medicationName: medicationName.trim(),
          dosage: dosage.trim(),
          frequency,
          route,
          startDate,
          endDate,
          instructions: prescriptionInstructions.trim(),
        });
      }

      onSuccess();
      onClose();
    } catch (err) {
      setError(displayError(err, 'Failed to create treatment plan.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Create Treatment Plan"
      description={horseName ? `Formulating clinical plan for ${horseName}` : 'Formulate a clinical treatment plan'}
      size="lg"
      dismissible={!loading}
      footer={
        <div className="flex justify-end gap-2.5">
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSubmit} loading={loading}>
            Create Plan
          </Button>
        </div>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <Notice tone="error" title="Submission Error" onDismiss={() => setError(null)}>
            {error}
          </Notice>
        )}

        <FormField label="Treatment Plan Name" required>
          <Input
            placeholder="e.g. Superficial Digital Flexor Tendon Rehabilitation"
            value={treatmentName}
            onChange={(e) => setTreatmentName(e.target.value)}
            disabled={loading}
          />
        </FormField>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <FormField label="Plan Start Date" required>
            <Input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              disabled={loading}
            />
          </FormField>

          <FormField label="Target End Date" required>
            <Input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              disabled={loading}
            />
          </FormField>
        </div>

        <FormField label="Treatment Protocol & Instructions" required>
          <Textarea
            placeholder="Specify therapeutic modalities, bandage changes, stall rest, hand-walking schedule..."
            value={instructions}
            onChange={(e) => setInstructions(e.target.value)}
            disabled={loading}
            rows={3}
          />
        </FormField>

        {/* Initial Prescription Toggle */}
        <div className="pt-2 border-t border-[var(--color-border-subtle)]">
          <label className="flex items-center gap-2 cursor-pointer text-[13px] font-medium text-[var(--color-text-primary)]">
            <input
              type="checkbox"
              checked={includePrescription}
              onChange={(e) => setIncludePrescription(e.target.checked)}
              className="rounded border-[var(--color-border-strong)] text-[var(--color-primary)] focus:ring-[var(--color-focus)]"
            />
            Attach initial prescription / medication
          </label>

          {includePrescription && (
            <div className="mt-3 p-3.5 bg-[var(--color-surface-muted)] rounded-[var(--radius-sm)] space-y-3">
              <FormField label="Medication Name" required={includePrescription}>
                <Input
                  placeholder="e.g. Meloxicam, Omeprazole"
                  value={medicationName}
                  onChange={(e) => setMedicationName(e.target.value)}
                  disabled={loading}
                />
              </FormField>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <FormField label="Dosage" required={includePrescription}>
                  <Input
                    placeholder="e.g. 0.6 mg/kg"
                    value={dosage}
                    onChange={(e) => setDosage(e.target.value)}
                    disabled={loading}
                  />
                </FormField>

                <FormField label="Route">
                  <Select value={route} onChange={(e) => setRoute(e.target.value)} disabled={loading}>
                    <option value="Oral">Oral</option>
                    <option value="Intravenous (IV)">Intravenous (IV)</option>
                    <option value="Intramuscular (IM)">Intramuscular (IM)</option>
                    <option value="Topical">Topical</option>
                  </Select>
                </FormField>
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <FormField label="Frequency">
                  <Select value={frequency} onChange={(e) => setFrequency(e.target.value)} disabled={loading}>
                    <option value="Once daily">Once daily</option>
                    <option value="Twice daily">Twice daily</option>
                    <option value="Every 8 hours">Every 8 hours</option>
                    <option value="As needed">As needed</option>
                  </Select>
                </FormField>

                <FormField label="Prescription Instructions">
                  <Input
                    placeholder="e.g. Administer in morning grain"
                    value={prescriptionInstructions}
                    onChange={(e) => setPrescriptionInstructions(e.target.value)}
                    disabled={loading}
                  />
                </FormField>
              </div>
            </div>
          )}
        </div>
      </form>
    </Modal>
  );
}
