'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Icon, Modal, Notice, Panel, Pill, Spinner } from '@/components/ui';
import { careService } from '../services/careService';
import { TreatmentPlanModal } from './TreatmentPlanModal';
import { PrescriptionModal } from './PrescriptionModal';
import type { CareScheduleDetailResponse, TreatmentPlan } from '../types';
import { displayError, formatDate } from '@/lib/display';

interface CareRecordDetailModalProps {
  scheduleId: number | null;
  open: boolean;
  onClose: () => void;
  onScheduleUpdated: () => void;
}

export function CareRecordDetailModal({
  scheduleId,
  open,
  onClose,
  onScheduleUpdated,
}: CareRecordDetailModalProps) {
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<CareScheduleDetailResponse | null>(null);
  const [plans, setPlans] = useState<TreatmentPlan[]>([]);
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [activePlanForPrescription, setActivePlanForPrescription] = useState<{
    id: number;
    name: string;
  } | null>(null);

  const loadDetail = useCallback(async () => {
    if (!scheduleId) return;
    try {
      setLoading(true);
      setError(null);
      const res = await careService.getScheduleDetail(scheduleId);
      setDetail(res);

      if (res.healthRecord?.id) {
        const plansRes = await careService.getTreatmentPlans(res.healthRecord.id);
        setPlans(plansRes);
      } else {
        setPlans([]);
      }
    } catch (err) {
      setError(displayError(err, 'Failed to load schedule details.'));
    } finally {
      setLoading(false);
    }
  }, [scheduleId]);

  useEffect(() => {
    if (open && scheduleId) {
      loadDetail();
    } else {
      setDetail(null);
      setPlans([]);
      setError(null);
    }
  }, [open, scheduleId, loadDetail]);

  if (!open) return null;

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title={
          <div className="flex items-center gap-2">
            <Icon name="stethoscope" size={20} className="text-[var(--color-primary)]" />
            <span>Care Schedule #{scheduleId}</span>
          </div>
        }
        description="Clinical examination record, health assessment, and active treatment regimens."
        size="lg"
        footer={
          <div className="flex justify-end">
            <Button variant="secondary" onClick={onClose}>
              Close
            </Button>
          </div>
        }
      >
        {loading ? (
          <div className="py-12 flex justify-center items-center">
            <Spinner size="lg" />
          </div>
        ) : error ? (
          <Notice tone="error" title="Failed to load details">
            {error}
          </Notice>
        ) : !detail ? (
          <p className="text-sm text-[var(--color-text-muted)]">No details available.</p>
        ) : (
          <div className="space-y-4">
            {/* Header metadata summary */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 p-3 bg-[var(--color-surface-muted)] rounded-[var(--radius-sm)] text-[12px]">
              <div>
                <span className="text-[var(--color-text-muted)] block">Horse:</span>
                <span className="font-semibold text-[var(--color-text-primary)]">
                  {detail.horse?.name || `#${detail.schedule.horseId}`}
                </span>
              </div>
              <div>
                <span className="text-[var(--color-text-muted)] block">Care Type:</span>
                <span className="font-medium text-[var(--color-text-primary)]">
                  {detail.schedule.careType}
                </span>
              </div>
              <div>
                <span className="text-[var(--color-text-muted)] block">Status:</span>
                <Pill
                  tone={
                    detail.schedule.status === 'COMPLETED'
                      ? 'success'
                      : detail.schedule.status === 'IN_PROGRESS'
                      ? 'warning'
                      : 'info'
                  }
                  size="sm"
                >
                  {detail.schedule.status}
                </Pill>
              </div>
              <div>
                <span className="text-[var(--color-text-muted)] block">Veterinarian:</span>
                <span className="font-medium text-[var(--color-text-primary)]">
                  {detail.veterinarian?.fullName || 'Assigned Staff'}
                </span>
              </div>
            </div>

            {/* Health Record Section */}
            {detail.healthRecord ? (
              <Panel padded className="space-y-3">
                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                  <h4 className="text-[14px] font-semibold text-[var(--color-text-primary)] flex items-center gap-1.5">
                    <Icon name="clipboard" size={16} className="text-[var(--color-primary)]" />
                    Clinical Record #{detail.healthRecord.id}
                  </h4>
                  <Pill
                    tone={
                      detail.healthRecord.trainingDecision === 'BLOCKED'
                        ? 'danger'
                        : detail.healthRecord.trainingDecision === 'RESTRICTED'
                        ? 'warning'
                        : 'success'
                    }
                    size="sm"
                  >
                    Training: {detail.healthRecord.trainingDecision}
                  </Pill>
                </div>

                <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 text-[13px]">
                  <div>
                    <span className="text-[var(--color-text-muted)] text-[11px] uppercase font-semibold block">
                      Findings:
                    </span>
                    <p className="text-[var(--color-text-primary)]">
                      {detail.healthRecord.findings || 'No abnormalities detected.'}
                    </p>
                  </div>
                  <div>
                    <span className="text-[var(--color-text-muted)] text-[11px] uppercase font-semibold block">
                      Diagnosis:
                    </span>
                    <p className="text-[var(--color-text-primary)]">
                      {detail.healthRecord.diagnosis || 'None diagnosed.'}
                    </p>
                  </div>
                </div>

                {detail.healthRecord.treatment && (
                  <div className="text-[13px]">
                    <span className="text-[var(--color-text-muted)] text-[11px] uppercase font-semibold block">
                      Administered Treatment:
                    </span>
                    <p className="text-[var(--color-text-secondary)]">
                      {detail.healthRecord.treatment}
                    </p>
                  </div>
                )}

                {detail.healthRecord.restrictionDetails && (
                  <Notice tone="warning" title="Training Restriction Details">
                    {detail.healthRecord.restrictionDetails}
                  </Notice>
                )}
              </Panel>
            ) : (
              <Panel padded className="text-[13px] text-[var(--color-text-muted)] italic">
                No clinical health record has been completed for this schedule yet.
              </Panel>
            )}

            {/* Treatment Plans & Prescriptions */}
            {detail.healthRecord && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-[14px] font-semibold text-[var(--color-text-primary)] flex items-center gap-1.5">
                    <Icon name="pill" size={16} className="text-[var(--color-primary)]" />
                    Treatment Plans & Prescriptions ({plans.length})
                  </h4>
                  <Button
                    variant="secondary"
                    size="sm"
                    icon="plus"
                    onClick={() => setShowPlanModal(true)}
                  >
                    Add Treatment Plan
                  </Button>
                </div>

                {plans.length === 0 ? (
                  <Panel padded className="text-center py-6 text-[13px] text-[var(--color-text-muted)]">
                    No active treatment plans assigned to this record.
                  </Panel>
                ) : (
                  <div className="space-y-3">
                    {plans.map((p) => (
                      <Panel key={p.id} padded className="space-y-2.5 border-l-4 border-l-[var(--color-primary)]">
                        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-2">
                          <div>
                            <span className="font-bold text-[14px] text-[var(--color-text-primary)]">
                              {p.treatmentName}
                            </span>
                            <span className="text-[12px] text-[var(--color-text-muted)] ml-2">
                              ({formatDate(p.startDate)} – {formatDate(p.endDate)})
                            </span>
                          </div>
                          <div className="flex items-center gap-2">
                            <Pill tone="info" size="sm">
                              {p.status}
                            </Pill>
                            <Button
                              variant="tertiary"
                              size="sm"
                              icon="plus"
                              onClick={() =>
                                setActivePlanForPrescription({ id: p.id, name: p.treatmentName })
                              }
                            >
                              Prescribe Med
                            </Button>
                          </div>
                        </div>

                        <p className="text-[13px] text-[var(--color-text-secondary)]">
                          {p.instructions}
                        </p>

                        {/* Prescriptions list */}
                        {p.prescriptions && p.prescriptions.length > 0 && (
                          <div className="mt-2.5 pt-2 border-t border-[var(--color-border-subtle)]">
                            <span className="text-[11px] font-semibold text-[var(--color-text-muted)] uppercase block mb-1.5">
                              Prescribed Medications:
                            </span>
                            <div className="space-y-1.5">
                              {p.prescriptions.map((rx) => (
                                <div
                                  key={rx.id}
                                  className="flex flex-wrap items-center justify-between text-[12px] bg-[var(--color-surface-muted)] px-2.5 py-1.5 rounded"
                                >
                                  <div>
                                    <strong className="text-[var(--color-text-primary)]">
                                      {rx.medicationName}
                                    </strong>{' '}
                                    <span className="text-[var(--color-text-secondary)]">
                                      — {rx.dosage} ({rx.route}), {rx.frequency}
                                    </span>
                                  </div>
                                  <span className="text-[var(--color-text-muted)]">
                                    {formatDate(rx.startDate)} - {formatDate(rx.endDate)}
                                  </span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </Panel>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Sub-modals */}
      {detail?.healthRecord?.id && (
        <TreatmentPlanModal
          open={showPlanModal}
          onClose={() => setShowPlanModal(false)}
          healthRecordId={detail.healthRecord.id}
          horseName={detail.horse?.name}
          onSuccess={() => {
            loadDetail();
            onScheduleUpdated();
          }}
        />
      )}

      {activePlanForPrescription && (
        <PrescriptionModal
          open={Boolean(activePlanForPrescription)}
          onClose={() => setActivePlanForPrescription(null)}
          treatmentPlanId={activePlanForPrescription.id}
          treatmentName={activePlanForPrescription.name}
          onSuccess={() => {
            loadDetail();
            onScheduleUpdated();
          }}
        />
      )}
    </>
  );
}
