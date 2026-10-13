import { apiGet, apiPost } from '@/services/api';
import type {
  CancelCareScheduleRequest,
  CareScheduleDetailResponse,
  CareScheduleResponse,
  CareScheduleStatus,
  CareType,
  CompleteCareScheduleRequest,
  CreateNextScheduleRequest,
  CreatePrescriptionRequest,
  CreatePreventiveCareScheduleRequest,
  CreateTreatmentPlanRequest,
  PageResponse,
  Prescription,
  TreatmentPlan,
} from '../types';

interface ApiResponse<T> {
  data: T;
  message?: string;
  error?: string;
}

export const careService = {
  listSchedules: async (params?: {
    status?: CareScheduleStatus;
    careType?: CareType;
    horseId?: number;
    veterinarianId?: number;
    admissionId?: number;
    page?: number;
    size?: number;
  }): Promise<PageResponse<CareScheduleResponse>> => {
    const qs = new URLSearchParams();
    if (params?.status) qs.set('status', params.status);
    if (params?.careType) qs.set('careType', params.careType);
    if (params?.horseId) qs.set('horseId', String(params.horseId));
    if (params?.veterinarianId) qs.set('veterinarianId', String(params.veterinarianId));
    if (params?.admissionId) qs.set('admissionId', String(params.admissionId));
    if (params?.page != null) qs.set('page', String(params.page));
    if (params?.size != null) qs.set('size', String(params.size));
    const query = qs.toString() ? `?${qs.toString()}` : '';
    const res = await apiGet<ApiResponse<PageResponse<CareScheduleResponse>>>(`/api/care-schedules${query}`);
    return res.data;
  },

  getScheduleDetail: async (id: number): Promise<CareScheduleDetailResponse> => {
    const res = await apiGet<ApiResponse<CareScheduleDetailResponse>>(`/api/care-schedules/${id}`);
    return res.data;
  },

  createPreventiveCare: async (data: CreatePreventiveCareScheduleRequest): Promise<CareScheduleResponse> => {
    const res = await apiPost<ApiResponse<CareScheduleResponse>>('/api/preventive-care-schedules', data);
    return res.data;
  },

  getUpcomingByHorse: async (horseId: number): Promise<CareScheduleResponse[]> => {
    const res = await apiGet<ApiResponse<CareScheduleResponse[]>>(`/api/preventive-care-schedules?horseId=${horseId}`);
    return res.data;
  },

  createNextSchedule: async (data: CreateNextScheduleRequest): Promise<CareScheduleResponse> => {
    const res = await apiPost<ApiResponse<CareScheduleResponse>>('/api/care-schedules/create-next', data);
    return res.data;
  },

  startSchedule: async (id: number): Promise<CareScheduleResponse> => {
    const res = await apiPost<ApiResponse<CareScheduleResponse>>(`/api/care-schedules/${id}/start`, {});
    return res.data;
  },

  completeSchedule: async (id: number, data: CompleteCareScheduleRequest): Promise<CareScheduleResponse> => {
    const res = await apiPost<ApiResponse<CareScheduleResponse>>(`/api/care-schedules/${id}/complete`, data);
    return res.data;
  },

  cancelSchedule: async (id: number, data: CancelCareScheduleRequest): Promise<CareScheduleResponse> => {
    const res = await apiPost<ApiResponse<CareScheduleResponse>>(`/api/care-schedules/${id}/cancel`, data);
    return res.data;
  },

  getTreatmentPlans: async (healthRecordId: number): Promise<TreatmentPlan[]> => {
    const res = await apiGet<ApiResponse<TreatmentPlan[]>>(`/api/health-records/${healthRecordId}/treatment-plans`);
    return res.data;
  },

  createTreatmentPlan: async (
    healthRecordId: number,
    data: CreateTreatmentPlanRequest,
  ): Promise<TreatmentPlan> => {
    const res = await apiPost<ApiResponse<TreatmentPlan>>(
      `/api/health-records/${healthRecordId}/treatment-plans`,
      data,
    );
    return res.data;
  },

  createPrescription: async (
    treatmentPlanId: number,
    data: CreatePrescriptionRequest,
  ): Promise<Prescription> => {
    const res = await apiPost<ApiResponse<Prescription>>(
      `/api/treatment-plans/${treatmentPlanId}/prescriptions`,
      data,
    );
    return res.data;
  },
};
