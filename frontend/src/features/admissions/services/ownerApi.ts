import { apiGet, apiUpload } from '@/services/api';
import type { AdmissionSummaryResponse, AdmissionStatus } from '../types';
import type { CreateOwnerAdmissionRequest, AdmissionDocumentUpload } from '../types/owner';

export type CandidateForm = {
  name: string; breed: string; dateOfBirth: string | null;
  registrationNumber: string | null; registryName: string | null;
  sireName: string | null; sireRegistrationNumber: string | null;
  damName: string | null; damRegistrationNumber: string | null;
  pedigreeNotes: string | null;
};

export type AdmissionDocument = {
  id: number; documentType: string; fileUrl: string; recordDate: string | null;
  note: string | null; uploadedAt: string; medical: boolean;
  originalFileName: string | null;
};

export type OwnerAdmissionDetail = {
  admissionId: number; status: AdmissionStatus; submittedAt: string;
  candidate: CandidateForm; documents: AdmissionDocument[];
  groomFeedback: string | null; vetFeedback: string | null;
  trainerFeedback: string | null; managerFeedback: string | null;
};

type ApiResponse<T> = { success: boolean; data: T; message: string };

export const ownerAdmissionApi = {
  list: async () => (await apiGet<ApiResponse<AdmissionSummaryResponse[]>>('/api/owner/admissions')).data,
  detail: async (id: number) =>
    (await apiGet<ApiResponse<OwnerAdmissionDetail>>(`/api/owner/admissions/${id}`)).data,
  submit: async (candidate: CreateOwnerAdmissionRequest, uploads: AdmissionDocumentUpload[]) => {
    const body = new FormData();
    body.append('candidate', new Blob([JSON.stringify(candidate)], { type: 'application/json' }));
    body.append('documents', new Blob([JSON.stringify(uploads.map(upload => ({
      documentType: upload.documentType,
      recordDate: upload.recordDate || null,
      note: upload.note?.trim() || null,
    })))], { type: 'application/json' }));
    // Metadata at index i describes the file at index i.
    uploads.forEach(upload => body.append('files', upload.file));
    const result = await apiUpload<ApiResponse<OwnerAdmissionDetail>>('/api/owner/admissions', body);
    if (!result?.success) throw new Error(result?.message || 'Unable to submit admission.');
    return result.data;
  },
  upload: async (id: number, file: File, documentType: string, recordDate?: string, note?: string) => {
    const body = new FormData();
    body.append('file', file);
    body.append('documentType', documentType);
    if (recordDate) body.append('recordDate', recordDate);
    if (note) body.append('note', note);
    // Through the shared client: it sends the access token and refreshes it once on 401.
    const json = await apiUpload<ApiResponse<AdmissionDocument>>(`/api/owner/admissions/${id}/documents`, body);
    if (!json?.success) throw new Error(json?.message || 'Document upload failed');
    return json.data;
  },
};


