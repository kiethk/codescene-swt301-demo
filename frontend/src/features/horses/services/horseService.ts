import { apiGet } from '@/services/api';
import type { ApiResponse } from '@/types/horse';
import type { Horse } from '@/features/stable/types';
import type { HorseSpecialty } from '../types';

export const horseService = {
  getById: async (horseId: number): Promise<Horse> =>
    (await apiGet<ApiResponse<Horse>>(`/api/horses/${horseId}`)).data,

  /** Racing specialty of one horse; any role that may view the horse can read it. */
  getSpecialty: async (horseId: number): Promise<HorseSpecialty> =>
    (await apiGet<ApiResponse<HorseSpecialty>>(`/api/horses/${horseId}/specialty`)).data,
};
