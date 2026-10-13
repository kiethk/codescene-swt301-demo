import type { Metadata } from 'next';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { PreventiveCareView } from '@/features/care/components/PreventiveCareView';

export const metadata: Metadata = {
  title: 'Preventive Care & Health Regimens | Racehorse Training System',
  description: 'Proactive veterinary care, vaccination schedules, deworming, treatment plans, and prescriptions',
};

export default function PreventiveCarePage() {
  return (
    <RoleGuard allowedRoles={['VETERINARIAN', 'CLUB_MANAGER']}>
      <PageContainer>
        <PreventiveCareView />
      </PageContainer>
    </RoleGuard>
  );
}
