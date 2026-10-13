import type { Role } from '@/types/auth';
import type { NotificationItem } from '../types';

/** Where "view details" on a notification should take the current user, if anywhere. */
export function getNotificationHref(item: NotificationItem, role: Role | undefined): string | null {
  const refId = item.referenceId;
  if (!refId) return null;

  if (item.referenceType === 'CARE_SCHEDULE' && role === 'VETERINARIAN') {
    return `/veterinarian/urgent/${refId}`;
  }
  if (item.notificationType === 'ADMISSION_VET_ASSIGNED') return `/veterinarian/admissions?id=${refId}`;
  if (item.notificationType === 'ADMISSION_TRAINER_ASSIGNED') return `/trainer/admissions/${refId}`;
  if (item.referenceType === 'ADMISSION') {
    if (role === 'VETERINARIAN') return `/veterinarian/admissions?id=${refId}`;
    if (role === 'HEAD_TRAINER') return `/trainer/admissions/${refId}`;
  }
  return null;
}
