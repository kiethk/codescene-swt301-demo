import { apiGet, apiPost, apiUpload } from '@/services/api';
import type { IncidentReport, CreateIncidentRequest } from '../types';

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}


export const incidentApi = {
  list: async (status?: string): Promise<IncidentReport[]> => {
    const url = status
      ? `/api/groom-incident-reports?status=${status}`
      : '/api/groom-incident-reports';
    return (await apiGet<ApiResponse<IncidentReport[]>>(url)).data;
  },

  create: async (body: CreateIncidentRequest): Promise<IncidentReport> =>
    (await apiPost<ApiResponse<IncidentReport>>('/api/groom-incident-reports', body)).data,

  /**
   * Attach an image after create returns the report id.
   * apiUpload handles the multipart boundary, credentials, and error message
   * parsing from the response body.
   */
  uploadImage: async (reportId: number, file: File): Promise<IncidentReport> => {
    const body = new FormData();
    body.append('file', file);
    const res = await apiUpload<ApiResponse<IncidentReport>>(
      `/api/groom-incident-reports/${reportId}/image`,
      body,
    );
    return res.data;
  },

  /**
   * API path of the report's image, or null when it has none. The endpoint checks the viewer,
   * so render it with <ProtectedImage>, never a plain <img>.
   */
  imageSrc: (report: IncidentReport): string | null =>
    report.imageUrl ? `/api/groom-incident-reports/${report.id}/image` : null,
};
