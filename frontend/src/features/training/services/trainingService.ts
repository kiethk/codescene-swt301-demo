import { apiGet, apiPost } from '@/services/api';
import type {
  Subject,
  CreateSubjectRequest,
  Course,
  CourseDetailResponse,
  CreateCourseRequest,
  CourseSubjectItem,
  CourseSubjectResponse,
  JoinableCohortResponse,
  CreateHorseTrainingPlanRequest,
  HorseTrainingPlanDetailResponse,
  PlanSummaryResponse,
  CompleteWorkoutRequest,
  TrainingLotResponse,
  RescheduleLotRequest,
  HorseFitnessTrendItem,
  HorseAlert,
  TrainerDashboardHorse,
  ReadinessAssessment,
} from '../types';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Keeps the backend's detailed error message (e.g. the golden-hour-full notice with the exact minutes)
 */
async function postWithMessage<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  const payload = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(payload?.message || `API error: ${res.status}`);
  }
  return payload?.data !== undefined ? payload.data : (payload as T);
}

async function patchWithMessage<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const payload = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(payload?.message || `API error: ${res.status}`);
  }
  return payload?.data !== undefined ? payload.data : (payload as T);
}

export const trainingApi = {
  // Subjects
  getSubjects: async (): Promise<Subject[]> =>
    (await apiGet<ApiResponse<Subject[]>>('/api/subjects')).data,

  createSubject: async (data: CreateSubjectRequest): Promise<Subject> =>
    (await apiPost<ApiResponse<Subject>>('/api/subjects', data)).data,

  // Courses
  getCourses: async (): Promise<Course[]> =>
    (await apiGet<ApiResponse<Course[]>>('/api/courses')).data,

  getCourse: async (id: number): Promise<CourseDetailResponse> =>
    (await apiGet<ApiResponse<CourseDetailResponse>>(`/api/courses/${id}`)).data,

  createCourse: async (data: CreateCourseRequest): Promise<Course> =>
    (await apiPost<ApiResponse<Course>>('/api/courses', data)).data,

  addSubjectToCourse: async (
    courseId: number,
    data: CourseSubjectItem,
  ): Promise<CourseSubjectResponse> =>
    (await apiPost<ApiResponse<CourseSubjectResponse>>(`/api/courses/${courseId}/subjects`, data)).data,

  // Training plans
  getJoinableCohorts: async (courseId: number): Promise<JoinableCohortResponse[]> =>
    (await apiGet<ApiResponse<JoinableCohortResponse[]>>(
      `/api/training-plans/joinable-cohorts?courseId=${courseId}`,
    )).data,

  createPlan: async (
    data: CreateHorseTrainingPlanRequest,
  ): Promise<HorseTrainingPlanDetailResponse[]> =>
    await postWithMessage<HorseTrainingPlanDetailResponse[]>('/api/training-plans', data),

  getPlanById: async (id: number): Promise<HorseTrainingPlanDetailResponse> =>
    (await apiGet<ApiResponse<HorseTrainingPlanDetailResponse>>(`/api/training-plans/${id}`)).data,

  /** Plans with horse name, course name and progress. */
  getPlans: async (horseId?: number): Promise<PlanSummaryResponse[]> => {
    const url = horseId ? `/api/training-plans?horseId=${horseId}` : '/api/training-plans';
    return (await apiGet<ApiResponse<PlanSummaryResponse[]>>(url)).data;
  },

  // Training lots
  getLots: async (from: string, to: string, includeCancelled: boolean = false): Promise<TrainingLotResponse[]> =>
    (await apiGet<ApiResponse<TrainingLotResponse[]>>(
      `/api/lots?from=${from}&to=${to}&includeCancelled=${includeCancelled}`,
    )).data,

  getLotById: async (id: number): Promise<TrainingLotResponse> =>
    (await apiGet<ApiResponse<TrainingLotResponse>>(`/api/lots/${id}`)).data,

  rescheduleLot: async (id: number, data: RescheduleLotRequest): Promise<TrainingLotResponse> =>
    await patchWithMessage<TrainingLotResponse>(`/api/lots/${id}/reschedule`, data),

  cancelLot: async (id: number): Promise<TrainingLotResponse> =>
    await patchWithMessage<TrainingLotResponse>(`/api/lots/${id}/cancel`),

  // Workouts
  completeWorkout: async (workoutId: number, data: CompleteWorkoutRequest): Promise<void> => {
    await patchWithMessage(`/api/workouts/${workoutId}/complete`, data);
  },

  // Phase 7: dashboard, fitness & alerts
  getDashboard: async (): Promise<TrainerDashboardHorse[]> => {
    const res = await apiGet<ApiResponse<TrainerDashboardHorse[]>>('/api/training-plans/dashboard');
    return res.data;
  },

  getFitnessTrend: async (horseId: number, from?: string, to?: string): Promise<HorseFitnessTrendItem[]> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString() ? `?${params.toString()}` : '';
    const res = await apiGet<ApiResponse<HorseFitnessTrendItem[]>>(`/api/horses/${horseId}/fitness-trend${qs}`);
    return res.data;
  },

  getHorseAlerts: async (horseId: number): Promise<HorseAlert[]> => {
    const res = await apiGet<ApiResponse<HorseAlert[]>>(`/api/horses/${horseId}/alerts`);
    return res.data;
  },

  getReadinessHistory: async (horseId: number, includeAdmission = false): Promise<ReadinessAssessment[]> => {
    const res = await apiGet<ApiResponse<ReadinessAssessment[]>>(
      `/api/admissions/horses/${horseId}/readiness-history?includeAdmission=${includeAdmission}`
    );
    return res.data;
  },

  getHorseById: async (horseId: number): Promise<{ id: number; name: string; ueln: string; status: string }> => {
    const res = await apiGet<ApiResponse<{ id: number; name: string; ueln: string; status: string }>>(`/api/horses/${horseId}`);
    return res.data;
  },
};

export const trainingService = trainingApi;


