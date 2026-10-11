'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import {
  Button,
  EmptyState,
  LinkButton,
  ListSkeleton,
  PageHeader,
  Panel,
  Pill,
  ProgressBar,
  ScreenLayout,
  SectionTitle,
} from '@/components/ui';
import { groomApi } from '@/features/groom/services/groomService';
import { incidentApi } from '@/features/groom/services/incidentService';
import type { IncidentReport, TodayTaskItem } from '@/features/groom/types';
import { horseService } from '@/features/horses/services/horseService';
import type { HorseSpecialty } from '@/features/horses/types';
import { displayError } from '@/lib/display';
import { stableApi } from '../services/stableService';
import type { Area, Horse, StableStall } from '../types';
import { StableFloorPlan } from '../shared/components/StableFloorPlan';
import { StallDetailPanel } from '../shared/components/StallDetailPanel';

const OPEN_INCIDENT_STATUSES: ReadonlyArray<IncidentReport['status']> = ['REPORTED', 'IN_REVIEW'];

function isDone(task: TodayTaskItem) {
  return task.status === 'COMPLETED';
}

/** The Groom's view of the stable: where their horses are and what each still needs today. */
export function GroomStableView() {
  const { user } = useAuth();
  const [areas, setAreas] = useState<Area[]>([]);
  const [stalls, setStalls] = useState<StableStall[]>([]);
  const [horses, setHorses] = useState<Horse[]>([]);
  const [tasks, setTasks] = useState<TodayTaskItem[]>([]);
  const [incidents, setIncidents] = useState<IncidentReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedStallId, setSelectedStallId] = useState<number | null>(null);
  const [specialty, setSpecialty] = useState<{ horseId: number; value: HorseSpecialty } | null>(null);

  const loadAll = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [areaList, stallList, horseList, taskList, incidentList] = await Promise.all([
        stableApi.getAreas(),
        stableApi.getStalls(),
        stableApi.getHorses(),
        groomApi.getTodayTasks(),
        incidentApi.list(),
      ]);
      setAreas(areaList);
      setStalls(stallList);
      setHorses(horseList);
      setTasks(taskList);
      setIncidents(incidentList);
    } catch (err) {
      setError(displayError(err, 'Unable to load the stable. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadAll();
  }, [loadAll]);

  const horseByStallId = useMemo(() => {
    const map = new Map<number, Horse>();
    horses.forEach((horse) => {
      if (horse.currentStallId) map.set(horse.currentStallId, horse);
    });
    return map;
  }, [horses]);

  const ownStallIds = useMemo(
    () => new Set(stalls.filter((stall) => stall.groomId === user?.userId).map((stall) => stall.id)),
    [stalls, user],
  );

  const tasksByHorseId = useMemo(() => {
    const map = new Map<number, TodayTaskItem[]>();
    tasks.forEach((task) => map.set(task.horseId, [...(map.get(task.horseId) ?? []), task]));
    return map;
  }, [tasks]);

  const selectedStall = stalls.find((stall) => stall.id === selectedStallId) ?? null;
  const selectedHorse = selectedStall ? horseByStallId.get(selectedStall.id) : undefined;
  const selectedIsOwn = selectedStall ? ownStallIds.has(selectedStall.id) : false;
  const selectedHorseId = selectedHorse?.id;

  useEffect(() => {
    if (!selectedHorseId) return;
    let active = true;
    // Not every horse has enough rated workouts, and a failure here must not hide the stall details.
    horseService.getSpecialty(selectedHorseId)
      .then((value) => { if (active) setSpecialty({ horseId: selectedHorseId, value }); })
      .catch(() => undefined);
    return () => { active = false; };
  }, [selectedHorseId]);

  if (loading) return <ListSkeleton rows={6} />;

  if (error) {
    return (
      <Panel padded>
        <EmptyState
          icon="alert-triangle"
          title="Unable to load the stable"
          description={error}
          action={<Button variant="secondary" size="sm" icon="refresh" onClick={() => loadAll()}>Try again</Button>}
        />
      </Panel>
    );
  }

  const ownHorseTasks = selectedHorse && selectedIsOwn ? tasksByHorseId.get(selectedHorse.id) ?? [] : [];
  const openIncidents = selectedHorse
    ? incidents.filter((report) => report.horseId === selectedHorse.id && OPEN_INCIDENT_STATUSES.includes(report.status))
    : [];
  const ownHorseCount = [...ownStallIds].filter((id) => horseByStallId.has(id)).length;

  return (
    <ScreenLayout variant="dashboard">
      <PageHeader
        title="Stable"
        description={
          ownStallIds.size === 0
            ? 'You are not assigned to any stall yet. The Club Manager assigns stalls to grooms.'
            : `You look after ${ownStallIds.size} ${ownStallIds.size === 1 ? 'stall' : 'stalls'} and ${ownHorseCount} ${ownHorseCount === 1 ? 'horse' : 'horses'}. Select a stall to see what it needs today.`
        }
        actions={<Button variant="secondary" size="sm" icon="refresh" onClick={() => loadAll()}>Refresh</Button>}
      />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_20rem] xl:items-start">
        <StableFloorPlan
          lens="groom"
          areas={areas}
          stalls={stalls}
          horseByStallId={horseByStallId}
          ownStallIds={ownStallIds}
          selectedStallId={selectedStallId}
          onSelectStall={(stall) => setSelectedStallId(stall.id)}
          cellFooter={(stall, horse) => {
            if (!horse || !ownStallIds.has(stall.id)) return null;
            const horseTasks = tasksByHorseId.get(horse.id) ?? [];
            if (horseTasks.length === 0) {
              return <span className="block truncate text-xs text-[var(--color-text-muted)]">No tasks today</span>;
            }
            const done = horseTasks.filter(isDone).length;
            return (
              <span className="block space-y-1">
                <ProgressBar
                  value={done}
                  max={horseTasks.length}
                  size="sm"
                  tone={done === horseTasks.length ? 'success' : 'primary'}
                  label={`${done} of ${horseTasks.length} tasks done today`}
                />
                <span className="block text-xs text-[var(--color-text-secondary)]">{done}/{horseTasks.length} tasks</span>
              </span>
            );
          }}
        />

        <StallDetailPanel
          stall={selectedStall}
          area={areas.find((area) => area.id === selectedStall?.areaId)}
          horse={selectedHorse}
          specialty={specialty && specialty.horseId === selectedHorseId ? specialty.value : null}
          hint="Your stalls are highlighted on the map. Pick one to see today's tasks and open incidents."
          actions={
            selectedHorse && selectedIsOwn ? (
              <>
                <LinkButton href={`/groom/incidents/new?horseId=${selectedHorse.id}`} variant="warning" icon="alert-triangle">
                  Report incident
                </LinkButton>
                <LinkButton href="/groom/tasks" variant="secondary" icon="list">
                  Open daily tasks
                </LinkButton>
              </>
            ) : undefined
          }
        >
          {selectedStall && !selectedIsOwn && (
            <p className="text-xs text-[var(--color-text-muted)]">
              This stall is looked after by another groom. Tasks and incidents are shown only for your own stalls.
            </p>
          )}

          {selectedHorse && selectedIsOwn && (
            <>
              <section className="space-y-2">
                <SectionTitle>Today&apos;s tasks</SectionTitle>
                {ownHorseTasks.length === 0 ? (
                  <p className="text-xs italic text-[var(--color-text-muted)]">Nothing is scheduled for this horse today.</p>
                ) : (
                  <ul className="space-y-1.5">
                    {ownHorseTasks.map((task) => (
                      <li key={`${task.source}-${task.refId}`} className="flex items-start gap-2 text-xs">
                        <span className="font-metric w-10 shrink-0 font-semibold text-[var(--color-text-primary)]">
                          {task.startTime.substring(0, 5)}
                        </span>
                        <span className="min-w-0 flex-1 text-[var(--color-text-secondary)]">{task.title}</span>
                        <Pill size="sm" tone={isDone(task) ? 'success' : 'neutral'}>{isDone(task) ? 'Done' : 'To do'}</Pill>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              <section className="space-y-2">
                <SectionTitle>Open incidents</SectionTitle>
                {openIncidents.length === 0 ? (
                  <p className="text-xs italic text-[var(--color-text-muted)]">No open incident for this horse.</p>
                ) : (
                  <ul className="space-y-1.5">
                    {openIncidents.map((report) => (
                      <li key={report.id} className="flex items-start gap-2 text-xs">
                        <span className="min-w-0 flex-1 text-[var(--color-text-secondary)]">{report.title}</span>
                        <Pill size="sm" tone={report.status === 'IN_REVIEW' ? 'info' : 'warning'}>
                          {report.status === 'IN_REVIEW' ? 'With the vet' : 'Reported'}
                        </Pill>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </StallDetailPanel>
      </div>
    </ScreenLayout>
  );
}
