import type { Metadata } from 'next';
import { Suspense } from 'react';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { LoadingScreen } from '@/components/ui';
import { TrainerHorsesView } from '@/features/training/components/TrainerHorsesView';

export const metadata: Metadata = {
  title: 'Horses | Trainer | RTMS',
};

export default function TrainerHorsesPage() {
  return (
    <RoleGuard allowedRoles={['HEAD_TRAINER']}>
      <PageContainer>
        <Suspense fallback={<LoadingScreen />}>
          <TrainerHorsesView />
        </Suspense>
      </PageContainer>
    </RoleGuard>
  );
}
