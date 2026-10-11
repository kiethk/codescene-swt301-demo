'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { Button, EmptyState, FormField, ListSkeleton, Notice, PageHeader, Panel, ScreenLayout, Select } from '@/components/ui';
import { useTrainerHorses } from '../hooks/useTrainerHorses';
import { HorseFitnessTrendView } from './HorseFitnessTrendView';

/**
 * The Horses page: pick one of the horses in the trainer's areas and read everything about it. The chosen horse
 * lives in the URL (?horseId=), so a link from another screen can open the page on a given horse, and the first
 * horse is shown when none is asked for.
 */
export function TrainerHorsesView() {
  const router = useRouter();
  const requestedId = Number(useSearchParams().get('horseId')) || null;
  const { horses, loading, error, reload } = useTrainerHorses();

  const selectedId = horses.find((horse) => horse.id === requestedId)?.id ?? horses[0]?.id ?? null;

  return (
    <ScreenLayout variant="detail">
      <PageHeader
        title="Horses"
        description="Pick a horse to review its profile, racing specialty, fitness trend and health alerts."
      />

      {loading ? (
        <Panel padded><ListSkeleton rows={3} /></Panel>
      ) : error ? (
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Unable to load your horses"
            description={error}
            action={<Button size="sm" icon="refresh" onClick={reload}>Try again</Button>}
          />
        </Panel>
      ) : selectedId === null ? (
        <Panel padded>
          <EmptyState
            icon="horse"
            title="No horses assigned yet"
            description="Horses appear here once they are assigned to a stall in your area."
          />
        </Panel>
      ) : (
        <>
          <Panel padded>
            <FormField label="Horse" hint={`${horses.length} horses in your areas`} className="max-w-sm">
              <Select value={selectedId} onChange={(e) => router.replace(`/trainer/horses?horseId=${e.target.value}`)}>
                {horses.map((horse) => (
                  <option key={horse.id} value={horse.id}>
                    {horse.name}{horse.breed ? ` · ${horse.breed}` : ''}
                  </option>
                ))}
              </Select>
            </FormField>
          </Panel>

          {requestedId !== null && requestedId !== selectedId && (
            <Notice tone="warning">The requested horse is not in your areas, so the first horse is shown instead.</Notice>
          )}

          <HorseFitnessTrendView key={selectedId} horseId={selectedId} />
        </>
      )}
    </ScreenLayout>
  );
}
