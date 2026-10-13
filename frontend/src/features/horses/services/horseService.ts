import { apiGet } from '@/services/api';
import type { ApiResponse } from '@/types/horse';
import type { Horse } from '@/features/stable/types';
import type { HorseSpecialty, HorsePedigree, HorseHealthMetric } from '../types';

export const horseService = {
  getById: async (horseId: number): Promise<Horse> =>
    (await apiGet<ApiResponse<Horse>>(`/api/horses/${horseId}`)).data,

  /** Racing specialty of one horse; any role that may view the horse can read it. */
  getSpecialty: async (horseId: number): Promise<HorseSpecialty> =>
    (await apiGet<ApiResponse<HorseSpecialty>>(`/api/horses/${horseId}/specialty`)).data,

  /** List horses belonging to the current user (e.g. Owner). */
  listMine: async (): Promise<Horse[]> =>
    (await apiGet<ApiResponse<Horse[]>>('/api/horses?mine=true')).data,

  /** List all accessible horses (e.g. Veterinarian, Trainer). */
  listAll: async (): Promise<Horse[]> =>
    (await apiGet<ApiResponse<Horse[]>>('/api/horses')).data,

  /** Get pedigree of horse; returns null if not found. */
  getPedigree: async (horseId: number): Promise<HorsePedigree | null> => {
    try {
      return (await apiGet<ApiResponse<HorsePedigree>>(`/api/horses/${horseId}/pedigree`)).data;
    } catch {
      return null;
    }
  },

  /** Get health metrics recorded for a horse. */
  getHealthMetrics: async (horseId: number): Promise<HorseHealthMetric[]> => {
    try {
      return (await apiGet<ApiResponse<HorseHealthMetric[]>>(`/api/horses/${horseId}/health-metrics`)).data;
    } catch {
      return [];
    }
  },

  /** Get upcoming preventive care for a horse. */
  getPreventiveCare: async (horseId: number): Promise<any[]> => {
    try {
      return (await apiGet<ApiResponse<any[]>>(`/api/preventive-care-schedules?horseId=${horseId}`)).data;
    } catch {
      return [];
    }
  },
};

