import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { LinkButton } from '@/components/ui';
import { HorseProfileView } from '@/features/horses/shared/components/HorseProfileView';

export const metadata: Metadata = {
  title: 'Horse Profile | Manager | RTMS',
};

export default async function ManagerHorseDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const horseId = Number(id);

  if (!Number.isInteger(horseId) || horseId <= 0) {
    notFound();
  }

  return (
    <RoleGuard allowedRoles={['CLUB_MANAGER']}>
      <PageContainer>
        <HorseProfileView
          horseId={horseId}
          actions={
            <LinkButton
              href="/manager/horses"
              size="sm"
              variant="secondary"
              icon="arrow-left"
            >
              Back to horses
            </LinkButton>
          }
        />
      </PageContainer>
    </RoleGuard>
  );
}
