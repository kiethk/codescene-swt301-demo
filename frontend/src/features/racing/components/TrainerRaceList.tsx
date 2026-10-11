'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, EmptyState, LinkButton, ListSkeleton, PageHeader, Panel, ScreenLayout, Tabs } from '@/components/ui';
import { DataTable, type DataTableColumn } from '@/components/ui/DataTable';
import { displayError, formatDate, formatDateTime, formatTime } from '@/lib/display';
import { RACE_STATUS } from '../lib/status';
import { RaceStatusPill } from '../shared/components/RaceStatusPill';
import { racingService } from '../services/racingService';
import type { RaceRegistrationResponse, RaceRegistrationStatus } from '../types';

type FilterTab = 'ALL' | RaceRegistrationStatus;

const TABS: FilterTab[] = ['ALL', 'PENDING', 'APPROVED', 'REJECTED'];

const columns: DataTableColumn<RaceRegistrationResponse>[] = [
  {
    id: 'race',
    header: 'Race and category',
    sortValue: (item) => item.raceName,
    render: (item) => (
      <div>
        <div className="font-semibold">{item.raceName}</div>
        <div className="text-xs text-[var(--color-text-secondary)]">{item.raceCategory}</div>
      </div>
    ),
  },
  {
    id: 'horse',
    header: 'Horse',
    sortValue: (item) => item.horseName,
    render: (item) => (
      <div>
        <div className="font-medium">{item.horseName || `Horse #${item.horseId}`}</div>
        {item.horseRegistrationNumber && (
          <div className="font-metric text-xs text-[var(--color-text-muted)]">{item.horseRegistrationNumber}</div>
        )}
      </div>
    ),
  },
  {
    id: 'event',
    header: 'Event date and location',
    sortValue: (item) => item.eventDate,
    render: (item) => (
      <div>
        <div className="font-medium">
          {formatDate(item.eventDate)}
          {item.eventTime && ` · ${formatTime(item.eventTime)}`}
        </div>
        <div className="max-w-xs truncate text-xs text-[var(--color-text-secondary)]" title={item.location}>{item.location}</div>
      </div>
    ),
  },
  {
    id: 'status',
    header: 'Status',
    sortValue: (item) => RACE_STATUS[item.status].label,
    render: (item) => <RaceStatusPill status={item.status} />,
  },
  {
    id: 'submitted',
    header: 'Submitted',
    sortValue: (item) => item.createdAt,
    render: (item) => <span className="text-xs text-[var(--color-text-muted)]">{formatDateTime(item.createdAt)}</span>,
  },
  {
    id: 'actions',
    header: <span className="sr-only">Actions</span>,
    align: 'right',
    render: (item) => <LinkButton href={`/trainer/racing/${item.id}`} size="sm">View nomination</LinkButton>,
  },
];

export function TrainerRaceList() {
  const [items, setItems] = useState<RaceRegistrationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<FilterTab>('ALL');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await racingService.listMine());
    } catch (cause) {
      setError(displayError(cause, 'Unable to load race nominations.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  const countOf = useMemo(
    () => (status: FilterTab) => (status === 'ALL' ? items.length : items.filter((item) => item.status === status).length),
    [items],
  );
  const visible = tab === 'ALL' ? items : items.filter((item) => item.status === tab);

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Race nominations"
        description="Research races and submit internal nominations for management review."
        actions={<LinkButton href="/trainer/racing/new" variant="primary" icon="plus">Create nomination</LinkButton>}
      />

      <Tabs
        size="sm"
        active={tab}
        onChange={(id) => setTab(id as FilterTab)}
        tabs={TABS.map((status) => ({
          id: status,
          label: status === 'ALL' ? 'All' : RACE_STATUS[status].label,
          count: countOf(status),
        }))}
      />

      {loading && items.length === 0 ? (
        <Panel padded><ListSkeleton rows={5} /></Panel>
      ) : error ? (
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Unable to load nominations"
            description={error}
            action={<Button size="sm" icon="refresh" onClick={load}>Try again</Button>}
          />
        </Panel>
      ) : (
        <DataTable
          ariaLabel="Race nominations"
          rows={visible}
          columns={columns}
          getRowKey={(item) => item.id}
          getSearchText={(item) => `${item.raceName} ${item.raceCategory} ${item.horseName ?? ''} ${item.location}`}
          searchPlaceholder="Search races, horses or locations…"
          emptyTitle={items.length === 0 ? 'You have not submitted any nominations' : 'No matching nominations found'}
          emptyDescription={items.length === 0 ? 'Research a race and submit a nomination for management review.' : 'Try changing the filters or search terms.'}
          emptyAction={
            items.length === 0
              ? <LinkButton href="/trainer/racing/new" variant="primary" size="sm">Create nomination</LinkButton>
              : undefined
          }
          pageSize={10}
        />
      )}
    </ScreenLayout>
  );
}
