import { Metadata } from 'next';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { IncidentForm } from '@/features/groom/components/IncidentForm';

export const metadata: Metadata = {
  title: 'New Incident Report | Groom',
};

export default async function NewIncidentPage({
  searchParams,
}: {
  searchParams: Promise<{ horseId?: string }>;
}) {
  // Set when the form is opened from a stall on the Stable screen.
  const horseId = Number((await searchParams).horseId);

  return (
    <RoleGuard allowedRoles={['GROOM']}>
      <PageContainer>
        <IncidentForm initialHorseId={Number.isInteger(horseId) && horseId > 0 ? horseId : undefined} />
      </PageContainer>
    </RoleGuard>
  );
}
