import { Metadata } from 'next';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { Notice } from '@/components/ui';
import { TrainerRaceDetail } from '@/features/racing/components/TrainerRaceDetail';

export const metadata: Metadata = {
  title: 'Race nomination details | Trainer | RTMS',
};

type RouteParams = Promise<{ id: string }>;

export default async function TrainerRaceDetailPage({
  params,
}: {
  params: RouteParams;
}) {
  const { id } = await params;
  const raceId = Number(id);
  const validId = Number.isSafeInteger(raceId) && raceId > 0;

  return (
    <RoleGuard allowedRoles={['HEAD_TRAINER']}>
      <PageContainer>
        {validId ? (
          <TrainerRaceDetail id={raceId} />
        ) : (
          <Notice tone="error">This race nomination ID is invalid.</Notice>
        )}
      </PageContainer>
    </RoleGuard>
  );
}
