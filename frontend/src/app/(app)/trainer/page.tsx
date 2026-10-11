import type { Metadata } from 'next';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { TrainerDashboardView } from '@/features/training/components/TrainerDashboardView';

export const metadata: Metadata = {
  title: 'Dashboard | Trainer | RTMS',
};

export default function TrainerPage() {
  return (
    <RoleGuard allowedRoles={['HEAD_TRAINER']}>
      <PageContainer>
        <TrainerDashboardView />
      </PageContainer>
    </RoleGuard>
  );
}
