import type { Metadata } from 'next';
import { Suspense } from 'react';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { LoadingScreen } from '@/components/ui';
import { OwnerHorsesView } from '@/features/horses/components/OwnerHorsesView';

export const metadata: Metadata = {
  title: 'My Horses | Owner | RTMS',
  description: 'Manage and review your owned horses, racing specialty, and performance.',
};

export default function OwnerHorsesPage() {
  return (
    <RoleGuard allowedRoles={['HORSE_OWNER']}>
      <PageContainer>
        <Suspense fallback={<LoadingScreen />}>
          <OwnerHorsesView />
        </Suspense>
      </PageContainer>
    </RoleGuard>
  );
}
