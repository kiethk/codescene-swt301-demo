import type { Metadata } from 'next';
import { Suspense } from 'react';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { LoadingScreen } from '@/components/ui';
import { OwnerHorseDetailView } from '@/features/horses/components/OwnerHorseDetailView';

export const metadata: Metadata = {
  title: 'Horse Details | Owner | RTMS',
  description: 'Detailed overview of horse profile, pedigree, training progress, and health.',
};

export default async function OwnerHorseDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const parsedId = Number(id);

  return (
    <RoleGuard allowedRoles={['HORSE_OWNER']}>
      <PageContainer>
        <Suspense fallback={<LoadingScreen />}>
          <OwnerHorseDetailView horseId={parsedId} />
        </Suspense>
      </PageContainer>
    </RoleGuard>
  );
}
