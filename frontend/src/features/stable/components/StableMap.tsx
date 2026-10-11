'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Panel } from '@/components/ui/Panel';
import { Pill } from '@/components/ui/StatusBadge';
import { EmptyState, ListSkeleton } from '@/components/ui/states';
import { PageHeader } from '@/components/ui/PageHeader';
import { ScreenLayout } from '@/components/ui/ScreenLayout';
import { stableApi } from '../services/stableService';
import type { Area, Horse, StableStall, UserSummary } from '../types';
import { AssignHorseDialog } from './AssignHorseDialog';

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
   * Used only to show the Groom's NAME on each stall cell.
   *
   * Trainers no longer assign Grooms (V63 — that belongs to the Club
   * Manager), but they still need to know who cares for which horse to coordinate lots: when two
   * horses with the same Groom land in the same lot it hits BR-09, and the fix is to
   * move a horse to another Groom's stall or pick a separate Groom for the session.
   */
  const [grooms, setGrooms] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
    const shown = filtered.length > 0 ? filtered : areas.filter((a) => a.type === 'REGULAR');
    // The API returns areas in no particular order, so sort them by code: A before B.
    return [...shown].sort((a, b) => a.code.localeCompare(b.code, 'en', { numeric: true }));
  }, [areas, user]);

  const stallsByAreaId = useMemo(() => {
    const m = new Map<number, StableStall[]>();
    stalls.forEach((s) => {
      const list = m.get(s.areaId) ?? [];
      list.push(s);
      m.set(s.areaId, list);
    });
    return m;
  }, [stalls]);

  if (loading) return <ListSkeleton rows={4} />;

  if (error) {
    return (
      <Panel padded>
        <EmptyState icon="alert-triangle" title="Unable to load stable data" description={error} />
      </Panel>
    );
  }

  return (
    <ScreenLayout variant="dashboard">
      <PageHeader
        title="Stable map"
        description="Assign horses to stalls and view the groom responsible for each stall."
        actions={<Button variant="secondary" size="sm" icon="refresh" onClick={() => loadAll()}>Refresh</Button>}
      />

      {myAreas.length === 0 ? (
        <Panel padded>
          <EmptyState
            icon="activity"
            title="No training areas assigned"
            description="You have not been assigned to manage a regular stable area."
          />
        </Panel>
      ) : (
        myAreas.map((area) => {
          const areaStalls = [...(stallsByAreaId.get(area.id) ?? [])].sort((a, b) => a.stallNumber - b.stallNumber);
          return (
            <Panel key={area.id} padded>
              <div className="mb-4 flex items-center justify-between border-b border-[var(--color-border)] pb-3">
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">
                    Khu {area.code} — {area.name}
                  </h2>
                  <Pill tone="primary" size="sm">
                    {areaStalls.length} stalls
                  </Pill>
                </div>
              </div>

              {areaStalls.length === 0 ? (
                <p className="text-xs text-[var(--color-text-muted)]">This area has no stalls yet.</p>
              ) : (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
                  {areaStalls.map((stall) => {
                    const currentHorse = horseByStallId.get(stall.id);
                    const currentGroom = stall.groomId ? groomById.get(stall.groomId) : null;
                    const isOccupied = stall.status === 'OCCUPIED' || !!currentHorse;

                    return (
                      <div
                        key={stall.id}
                        className={`flex flex-col justify-between rounded-[var(--radius-md)] border p-3 transition ${
                          isOccupied
                            ? 'border-[var(--color-border-strong)] bg-[var(--color-surface-muted)]'
                            : 'border-dashed border-[var(--color-border)] bg-[var(--color-surface)]'
                        }`}
                      >
                        <div>
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-bold text-[var(--color-text-primary)]">
                              Stall {stall.stallCode}
                            </span>
                            <span
                              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                                isOccupied
                                  ? 'bg-[var(--color-success-soft)] text-[var(--color-success)]'
                                  : 'bg-[var(--color-surface-muted)] text-[var(--color-text-secondary)]'
                              }`}
                            >
                              {isOccupied ? 'Occupied' : 'Available'}
                            </span>
                          </div>

                          <div className="mt-2 text-xs">
                            <div className="text-xs text-[var(--color-text-muted)]">Horse:</div>
                            <div className="font-semibold text-[var(--color-text-primary)] truncate">
                              {currentHorse ? currentHorse.name : 'No horse assigned'}
                            </div>
                            {currentHorse?.breed && (
                              <div className="text-xs text-[var(--color-text-secondary)]">
                                {currentHorse.breed}
                              </div>
                            )}
                          </div>

                          <div className="mt-2">
                            <div className="text-xs text-[var(--color-text-muted)]">Assigned groom:</div>
                            {stall.groomId ? (
                              <Pill tone="info" size="sm">
                                {currentGroom ? currentGroom.fullName : `#${stall.groomId}`}
                              </Pill>
                            ) : (
                              <span className="text-xs text-[var(--color-text-muted)] italic">
                                Unassigned
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="mt-4 flex gap-1.5 pt-2 border-t border-[var(--color-border)]">
                          <Button
                            size="sm"
                            variant="secondary"
                            className="flex-1"
                            onClick={() => setAssignHorseStall(stall)}
                            disabled={isOccupied}
                          >
                            {isOccupied ? 'Occupied' : 'Assign horse'}
                          </Button>
                          {isOccupied && (
                            <Button
                              size="sm"
                              variant="secondary"
                              className="flex-1 text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]"
                              onClick={() => {
                                setUnassignError(null);
                                setUnassignStall(stall);
                              }}
                            >
                              Remove horse
                            </Button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </Panel>
          );
        })
      )}

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
