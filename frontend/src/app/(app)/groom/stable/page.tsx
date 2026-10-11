import { Metadata } from 'next';
import { RoleGuard } from '@/components/auth/RoleGuard';
import { PageContainer } from '@/components/layout/PageContainer';
import { GroomStableView } from '@/features/stable/components/GroomStableView';

export const metadata: Metadata = {
  title: 'Stable | Groom | RTMS',
};

export default function GroomStablePage() {
  return (
    <RoleGuard allowedRoles={['GROOM']}>
      <PageContainer>
        <GroomStableView />
      </PageContainer>
    </RoleGuard>
  );
}
