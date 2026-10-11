import { apiGet, apiPatch, apiPost } from '@/services/api';
import type { ApiResponse } from '@/types/horse';
import type {
  Subject,
  CreateSubjectRequest,
  Course,
  CourseDetailResponse,
  CreateCourseRequest,
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
  TrainingRules,
} from '../types';

/**
 * Every call goes through services/api.ts, so it carries the Bearer token, refreshes it once on a 401, and keeps
 * the backend's detailed error message (for example the notice that a lot is full with the exact minutes).
 */
export const trainingService = {
  // Rules the backend enforces, so the UI never hard-codes them
  getRules: async (): Promise<TrainingRules> =>
    (await apiGet<ApiResponse<TrainingRules>>('/api/training-rules')).data,

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

  // Training plans
  getJoinableCohorts: async (courseId: number): Promise<JoinableCohortResponse[]> =>
    (await apiGet<ApiResponse<JoinableCohortResponse[]>>(
      `/api/training-plans/joinable-cohorts?courseId=${courseId}`,
    )).data,

  /** Enrols a group; the response holds one plan per horse. */
  createPlan: async (data: CreateHorseTrainingPlanRequest): Promise<HorseTrainingPlanDetailResponse[]> =>
    (await apiPost<ApiResponse<HorseTrainingPlanDetailResponse[]>>('/api/training-plans', data)).data,

  getPlanById: async (id: number): Promise<HorseTrainingPlanDetailResponse> =>
    (await apiGet<ApiResponse<HorseTrainingPlanDetailResponse>>(`/api/training-plans/${id}`)).data,

  /** Plans with horse name, course name and progress. */
  getPlans: async (horseId?: number): Promise<PlanSummaryResponse[]> => {
    const url = horseId ? `/api/training-plans?horseId=${horseId}` : '/api/training-plans';
    return (await apiGet<ApiResponse<PlanSummaryResponse[]>>(url)).data;
  },

  // Training lots
  getLots: async (from: string, to: string, includeCancelled = false): Promise<TrainingLotResponse[]> =>
    (await apiGet<ApiResponse<TrainingLotResponse[]>>(
      `/api/lots?from=${from}&to=${to}&includeCancelled=${includeCancelled}`,
    )).data,

  rescheduleLot: async (id: number, data: RescheduleLotRequest): Promise<TrainingLotResponse> =>
    (await apiPatch<ApiResponse<TrainingLotResponse>>(`/api/lots/${id}/reschedule`, data)).data,

  cancelLot: async (id: number): Promise<TrainingLotResponse> =>
    (await apiPatch<ApiResponse<TrainingLotResponse>>(`/api/lots/${id}/cancel`)).data,

  // Workouts
  completeWorkout: async (workoutId: number, data: CompleteWorkoutRequest): Promise<void> => {
    await apiPatch(`/api/workouts/${workoutId}/complete`, data);
  },

  // Dashboard, fitness and alerts
  getDashboard: async (): Promise<TrainerDashboardHorse[]> =>
    (await apiGet<ApiResponse<TrainerDashboardHorse[]>>('/api/training-plans/dashboard')).data,

  getFitnessTrend: async (horseId: number, from?: string, to?: string): Promise<HorseFitnessTrendItem[]> => {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return (await apiGet<ApiResponse<HorseFitnessTrendItem[]>>(`/api/horses/${horseId}/fitness-trend${qs}`)).data;
  },

  getHorseAlerts: async (horseId: number): Promise<HorseAlert[]> =>
    (await apiGet<ApiResponse<HorseAlert[]>>(`/api/horses/${horseId}/alerts`)).data,

  getReadinessHistory: async (horseId: number, includeAdmission = false): Promise<ReadinessAssessment[]> =>
    (await apiGet<ApiResponse<ReadinessAssessment[]>>(
      `/api/admissions/horses/${horseId}/readiness-history?includeAdmission=${includeAdmission}`,
    )).data,
};
