'use client';

import { Button, LinkButton, PageHeader, ScreenLayout } from '@/components/ui';
import { useHorses } from '../hooks/useHorses';
import { HorseTable } from '../shared/components/HorseTable';

export function ManagerHorsesView() {
  const { horses, loading, error, reload } = useHorses();

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Horses"
        actions={
          <Button
            type="button"
            variant="secondary"
            size="sm"
            icon="refresh"
            onClick={reload}
            loading={loading}
          >
            Refresh
          </Button>
        }
      />

      <HorseTable
        horses={horses}
        loading={loading}
        error={error}
        renderAction={(horse) => (
          <LinkButton
            href={`/manager/horses/${horse.id}`}
            size="sm"
            variant="secondary"
          >
            View
          </LinkButton>
        )}
      />
    </ScreenLayout>
  );
}
