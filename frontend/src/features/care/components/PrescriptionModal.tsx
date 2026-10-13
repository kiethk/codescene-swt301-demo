'use client';

import { useState } from 'react';
import { Button, FormField, Input, Modal, Notice, Select, Textarea } from '@/components/ui';
import { careService } from '../services/careService';
import { displayError } from '@/lib/display';

interface PrescriptionModalProps {
  open: boolean;
  onClose: () => void;
  treatmentPlanId: number;
  treatmentName: string;
  onSuccess: () => void;
}

export function PrescriptionModal({
  open,
  onClose,
  treatmentPlanId,
  treatmentName,
  onSuccess,
}: PrescriptionModalProps) {
  const [medicationName, setMedicationName] = useState('');
  const [dosage, setDosage] = useState('');
  const [frequency, setFrequency] = useState('Once daily');
  const [route, setRoute] = useState('Oral');
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [endDate, setEndDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + 7);
    return d.toISOString().slice(0, 10);
  });
  const [instructions, setInstructions] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!medicationName.trim()) {
      setError('Medication name is required.');
      return;
    }
    if (!dosage.trim()) {
      setError('Dosage is required.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await careService.createPrescription(treatmentPlanId, {
        medicationName: medicationName.trim(),
        dosage: dosage.trim(),
        frequency,
        route,
        startDate,
        endDate,
        instructions: instructions.trim(),
      });
      onSuccess();
      onClose();
    } catch (err) {
      setError(displayError(err, 'Failed to save prescription.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Add Prescription"
      description={`Prescribing medication under plan: ${treatmentName}`}
      size="md"
      dismissible={!loading}
      footer={
        <div className="flex justify-end gap-2.5">
          <Button variant="secondary" onClick={onClose} disabled={loading}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSubmit} loading={loading}>
            Save Prescription
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

        <FormField label="Medication Name" required>
          <Input
            placeholder="e.g. Phenylbutazone, Flunixin"
            value={medicationName}
            onChange={(e) => setMedicationName(e.target.value)}
            disabled={loading}
          />
        </FormField>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <FormField label="Dosage" required>
            <Input
              placeholder="e.g. 2.2 mg/kg, 10 ml"
              value={dosage}
              onChange={(e) => setDosage(e.target.value)}
              disabled={loading}
            />
          </FormField>

          <FormField label="Administration Route">
            <Select value={route} onChange={(e) => setRoute(e.target.value)} disabled={loading}>
              <option value="Oral">Oral</option>
              <option value="Intravenous (IV)">Intravenous (IV)</option>
              <option value="Intramuscular (IM)">Intramuscular (IM)</option>
              <option value="Subcutaneous (SC)">Subcutaneous (SC)</option>
              <option value="Topical">Topical</option>
              <option value="Inhalation">Inhalation</option>
            </Select>
          </FormField>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <FormField label="Frequency">
            <Select value={frequency} onChange={(e) => setFrequency(e.target.value)} disabled={loading}>
              <option value="Once daily">Once daily</option>
              <option value="Twice daily">Twice daily</option>
              <option value="Three times daily">Three times daily</option>
              <option value="Every 12 hours">Every 12 hours</option>
              <option value="As needed (PRN)">As needed (PRN)</option>
            </Select>
          </FormField>

          <FormField label="Start Date" required>
            <Input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              disabled={loading}
            />
          </FormField>

          <FormField label="End Date" required>
            <Input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              disabled={loading}
            />
          </FormField>
        </div>

        <FormField label="Administration Instructions">
          <Textarea
            placeholder="e.g. Administer with feed. Monitor for gastrointestinal symptoms."
            value={instructions}
            onChange={(e) => setInstructions(e.target.value)}
            disabled={loading}
            rows={3}
          />
        </FormField>
      </form>
    </Modal>
  );
}
