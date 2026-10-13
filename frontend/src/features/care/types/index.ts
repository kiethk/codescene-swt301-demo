export type CareScheduleStatus = 'REQUESTED' | 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type CareType = 'INITIAL' | 'ROUTINE' | 'URGENT';
export type TrainingDecision = 'ALLOWED' | 'RESTRICTED' | 'BLOCKED';

export interface CareScheduleResponse {
  id: number;
  horseId: number;
  veterinarianId: number | null;
  admissionId: number | null;
  sourceIncidentId: number | null;
  careType: CareType;
  status: CareScheduleStatus;
  scheduledAt: string | null;
  durationMinutes: number;
  description: string | null;
  completedAt: string | null;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CareScheduleDetailResponse {
  schedule: CareScheduleResponse;
  horse: {
    id: number;
    name: string;
    breed: string | null;
    registrationNumber: string | null;
    trainingDecision?: TrainingDecision;
  } | null;
  veterinarian: {
    id: number;
    fullName: string;
    email: string;
  } | null;
  healthRecord: {
    id: number;
    findings: string | null;
    diagnosis: string | null;
    treatment: string | null;
    trainingDecision: TrainingDecision;
    restrictionDetails: string | null;
  } | null;
}

export interface CreatePreventiveCareScheduleRequest {
  horseId: number;
  veterinarianId?: number;
  careType: 'ROUTINE_EXAM' | 'VACCINATION' | 'DEWORMING';
  scheduledDate: string; // YYYY-MM-DD
  description?: string;
}

export interface CreateNextScheduleRequest {
  horseId: number;
  admissionId?: number;
  sourceScheduleId?: number;
  careType: CareType;
  scheduledDate?: string;
  scheduledAt?: string;
  description?: string;
  idempotencyKey?: string;
}

export interface CompleteCareScheduleRequest {
  symptoms: string;
  findings: string;
  diagnosis: string;
  treatment?: string;
  trainingDecision: TrainingDecision;
  restrictionDetails?: string;
  notes?: string;
  metrics?: Array<{
    heartRate?: number;
    temperature?: number;
    respiratoryRate?: number;
    weight?: number;
    hydrationStatus?: string;
    bodyConditionScore?: number;
    notes?: string;
  }>;
  nextSchedule?: {
    horseId: number;
    careType: CareType;
    scheduledDate: string;
    description?: string;
    idempotencyKey?: string;
  };
}

export interface CancelCareScheduleRequest {
  cancelReason: string;
}

export interface Prescription {
  id: number;
  medicationName: string;
  dosage: string;
  frequency: string;
  route: string;
  startDate: string;
  endDate: string;
  instructions: string;
}

export interface CreatePrescriptionRequest {
  medicationName: string;
  dosage: string;
  frequency: string;
  route: string;
  startDate: string;
  endDate: string;
  instructions: string;
}

export interface TreatmentPlan {
  id: number;
  healthRecordId: number;
  treatmentName: string;
  instructions: string;
  startDate: string;
  endDate: string;
  status: string;
  prescriptions?: Prescription[];
}

export interface CreateTreatmentPlanRequest {
  treatmentName: string;
  instructions: string;
  startDate: string;
  endDate: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
