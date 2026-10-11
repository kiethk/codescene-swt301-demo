import { apiGet, apiPatch, apiPost } from '@/services/api';
import type { TodayTaskItem } from '../types';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export const groomApi = {
  getTodayTasks: async (date?: string, groomId?: number): Promise<TodayTaskItem[]> => {
    const params = new URLSearchParams();
    if (date) params.set('date', date);
    if (groomId) params.set('groomId', String(groomId));
    const qs = params.toString();
    const url = `/api/groom-daily-tasks/today${qs ? `?${qs}` : ''}`;
    return (await apiGet<ApiResponse<TodayTaskItem[]>>(url)).data;
  },

  completeTask: async (id: number): Promise<void> => {
    // Through the shared client: it sends the access token and refreshes it once on 401.
    await apiPatch<ApiResponse<unknown>>(`/api/groom-daily-tasks/${id}/complete`);
  },

  generateRoutine: async (date?: string): Promise<void> => {
    const qs = date ? `?date=${date}` : '';
    await apiPost<ApiResponse<unknown>>(`/api/groom-daily-tasks/generate-routine${qs}`, {});
  },
};
