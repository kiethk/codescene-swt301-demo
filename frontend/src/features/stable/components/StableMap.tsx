'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import {
  Button,
  ConfirmDialog,
  EmptyState,
  FieldLabel,
  ListSkeleton,
  PageHeader,
  Panel,
  ScreenLayout,
} from '@/components/ui';
import { horseService } from '@/features/horses/services/horseService';
import type { HorseSpecialty } from '@/features/horses/types';
import { stableApi } from '../services/stableService';
import type { Area, Horse, StableStall, UserSummary } from '../types';
import { StableFloorPlan } from '../shared/components/StableFloorPlan';
import { StallDetailPanel } from '../shared/components/StallDetailPanel';
import { AssignHorseDialog } from './AssignHorseDialog';

/** The Head Trainer's view of the stable: assign horses to stalls and see which groom looks after each. */
export function StableMap() {
  const { user } = useAuth();
  const [areas, setAreas] = useState<Area[]>([]);
  const [stalls, setStalls] = useState<StableStall[]>([]);
  const [horses, setHorses] = useState<Horse[]>([]);
  /**
   * Horses NOT yet assigned to a stall — a separate list for the assign-horse dialog.
   * It cannot share `horses`: `horses` is fetched with mine=true, so the backend
   * has already dropped every unassigned horse and filtering on the client would always be empty.
   */
  const [unassignedHorses, setUnassignedHorses] = useState<Horse[]>([]);
  /**
   * Used only to show the Groom's NAME on each stall.
   *
   * Trainers no longer assign Grooms (V63 — that belongs to the Club
   * Manager), but they still need to know who cares for which horse to coordinate lots: when two
   * horses with the same Groom land in the same lot it hits BR-09, and the fix is to
   * move a horse to another Groom's stall or pick a separate Groom for the session.
   */
  const [grooms, setGrooms] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedStallId, setSelectedStallId] = useState<number | null>(null);
  const [specialty, setSpecialty] = useState<{ horseId: number; value: HorseSpecialty } | null>(null);
  const [assignHorseStall, setAssignHorseStall] = useState<StableStall | null>(null);
  /** The stall waiting for unassign confirmation. */
  const [unassignStall, setUnassignStall] = useState<StableStall | null>(null);
  const [unassigning, setUnassigning] = useState(false);
  const [unassignError, setUnassignError] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [areasRes, stallsRes, horsesRes, groomsRes, unassignedRes] = await Promise.all([
        stableApi.getAreas(),
        stableApi.getStalls(),
        stableApi.getHorses({ mine: true }),        // assigned to a stall -> drawn on the cells
        stableApi.getGrooms(),
        stableApi.getHorses({ unassigned: true }),  // unassigned -> for the dialog
      ]);
      setAreas(areasRes);
      setStalls(stallsRes);
      setHorses(horsesRes);
      setGrooms(groomsRes);
      setUnassignedHorses(unassignedRes);
    } catch (err) {
      console.error('Failed to load stable data:', err);
      setError('Unable to load the stable map. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadAll();
  }, [loadAll]);

  const horseByStallId = useMemo(() => {
    const m = new Map<number, Horse>();
    horses.forEach((h) => {
      if (h.currentStallId) m.set(h.currentStallId, h);
    });
    return m;
  }, [horses]);

  const groomById = useMemo(
    () => new Map(grooms.map((g) => [g.id, g] as const)),
    [grooms],
  );

  // Show only REGULAR blocks this Trainer is responsible for (if there is a trainerId), otherwise all REGULAR blocks
  const myAreas = useMemo(() => {
    const filtered = areas.filter(
      (a) => a.type === 'REGULAR' && (user?.userId ? a.trainerId === user.userId : true),
    );
    return filtered.length > 0 ? filtered : areas.filter((a) => a.type === 'REGULAR');
  }, [areas, user]);

  const selectedStall = stalls.find((stall) => stall.id === selectedStallId) ?? null;
  const selectedHorse = selectedStall ? horseByStallId.get(selectedStall.id) : undefined;
  const selectedGroom = selectedStall?.groomId ? groomById.get(selectedStall.groomId) : undefined;
  const selectedOccupied = !!selectedStall && (selectedStall.status === 'OCCUPIED' || !!selectedHorse);
  const selectedHorseId = selectedHorse?.id;

  useEffect(() => {
    if (!selectedHorseId) return;
    let active = true;
    horseService.getSpecialty(selectedHorseId)
      .then((value) => { if (active) setSpecialty({ horseId: selectedHorseId, value }); })
      .catch(() => undefined);
    return () => { active = false; };
  }, [selectedHorseId]);

  if (loading) return <ListSkeleton rows={4} />;

  if (error) {
    return (
      <Panel padded>
        <EmptyState icon="alert-triangle" title="Unable to load stable data" description={error} />
      </Panel>
    );
  }

  const groomLabel = (stall: StableStall) =>
    stall.groomId ? groomById.get(stall.groomId)?.fullName ?? `Groom #${stall.groomId}` : null;

  return (
    <ScreenLayout variant="dashboard">
      <PageHeader
        title="Stable map"
        description="Assign horses to stalls and view the groom responsible for each stall."
        actions={<Button variant="secondary" size="sm" icon="refresh" onClick={() => loadAll()}>Refresh</Button>}
      />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_20rem] xl:items-start">
        <StableFloorPlan
          lens="trainer"
          areas={myAreas}
          stalls={stalls}
          horseByStallId={horseByStallId}
          selectedStallId={selectedStallId}
          onSelectStall={(stall) => setSelectedStallId(stall.id)}
          emptyTitle="No training areas assigned"
          emptyDescription="You have not been assigned to manage a regular stable area."
          cellFooter={(stall) => (
            <span className="block truncate text-xs text-[var(--color-text-secondary)]">
              {groomLabel(stall) ?? <span className="italic text-[var(--color-text-muted)]">No groom</span>}
            </span>
          )}
        />

        <StallDetailPanel
          stall={selectedStall}
          area={areas.find((area) => area.id === selectedStall?.areaId)}
          horse={selectedHorse}
          specialty={specialty && specialty.horseId === selectedHorseId ? specialty.value : null}
          hint="Pick a stall to assign a horse to it, remove its horse, or see which groom looks after it."
          actions={
            selectedStall && (
              selectedOccupied ? (
                <Button
                  variant="destructive"
                  disabled={!selectedHorse}
                  onClick={() => {
                    setUnassignError(null);
                    setUnassignStall(selectedStall);
                  }}
                >
                  Remove horse
                </Button>
              ) : (
                <Button variant="primary" icon="plus" onClick={() => setAssignHorseStall(selectedStall)}>
                  Assign horse
                </Button>
              )
            )
          }
        >
          {selectedStall && (
            <div>
              <FieldLabel>Assigned groom</FieldLabel>
              <p className="mt-1 text-sm text-[var(--color-text-primary)]">
                {selectedStall.groomId
                  ? selectedGroom?.fullName ?? `Groom #${selectedStall.groomId}`
                  : <span className="italic text-[var(--color-text-muted)]">Unassigned</span>}
              </p>
            </div>
          )}
        </StallDetailPanel>
      </div>

      {/* Dialogs */}
      <AssignHorseDialog
        open={assignHorseStall !== null}
        stall={assignHorseStall}
        horses={unassignedHorses}
        onClose={() => setAssignHorseStall(null)}
        onAssigned={loadAll}
      />

      <ConfirmDialog
        open={unassignStall !== null}
        tone="danger"
        title={`Remove horse from stall ${unassignStall?.stallCode ?? ''}?`}
        confirmLabel="Remove horse"
        cancelLabel="Cancel"
        loading={unassigning}
        description={
          <div className="space-y-1.5">
            <p>
              The horse{' '}
              <strong>
                {unassignStall ? horseByStallId.get(unassignStall.id)?.name ?? '' : ''}
              </strong>{' '}
              will no longer be assigned to a stall.
            </p>
            <p>
              <strong>Upcoming workouts</strong> will become unassigned from a groom.
              Completed workouts will remain unchanged.
            </p>
            {unassignError && (
              <p className="text-[var(--color-danger)]">{unassignError}</p>
            )}
          </div>
        }
        onCancel={() => setUnassignStall(null)}
        onConfirm={async () => {
          if (!unassignStall) return;
          const horse = horseByStallId.get(unassignStall.id);
          if (!horse) return;
          try {
            setUnassigning(true);
            setUnassignError(null);
            await stableApi.unassignHorseFromStall(horse.id);
            setUnassignStall(null);
            await loadAll();
          } catch (err) {
            setUnassignError(err instanceof Error ? err.message : 'Unable to remove the horse from this stall.');
          } finally {
            setUnassigning(false);
          }
        }}
      />
    </ScreenLayout>
  );
}
