import type { Metadata } from 'next';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { ManagerHorsesView } from '@/features/horses/components/ManagerHorsesView';

export const metadata: Metadata = {
  title: 'Horses | Manager | RTMS',
};

export default function ManagerHorsesPage() {
  return (
    <RoleGuard allowedRoles={['CLUB_MANAGER']}>
      <PageContainer>
        <ManagerHorsesView />
      </PageContainer>
    </RoleGuard>
  );
}
