'use client';

import {useEffect, useMemo, useState} from 'react';
import {Button, LinkButton} from '@/components/ui/Button';
import {Panel} from '@/components/ui/Panel';
import {EmptyState, ListSkeleton} from '@/components/ui/states';
import {Notice} from '@/components/ui/Notice';
import {HorseAvatar} from '@/components/ui/HorseAvatar';
import {ownerAdmissionApi} from '@/features/admissions/services/ownerApi';
import type {AdmissionStatus, AdmissionSummaryResponse} from '../types';
import {AdmissionTable} from '../shared/components/AdmissionTable';
import {AdmissionListLayout} from '../shared/components/AdmissionListLayout';
import {AdmissionFilterBar, FilterDateRange, FilterSelect} from '../shared/components/AdmissionFilterBar';

interface OwnerListFilters {
  search: string;
  status: AdmissionStatus | 'ALL';
  from: string;
  to: string;
}

const EMPTY_FILTERS: OwnerListFilters = { search: '', status: 'ALL', from: '', to: '' };

const STATUS_OPTIONS: ReadonlyArray<{ value: AdmissionStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'GROOM_REVIEW', label: 'Groom Review' },
  { value: 'WAITING_FOR_STALL', label: 'Waiting for Stall' },
  { value: 'WAITING_FOR_ARRIVAL', label: 'Waiting for Arrival' },
  { value: 'ARRIVAL_EXPIRED', label: 'Arrival Expired' },
  { value: 'VET_REVIEW', label: 'Vet Review' },
  { value: 'TRAINER_REVIEW', label: 'Trainer Review' },
  { value: 'MANAGER_REVIEW', label: 'Manager Review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

export function OwnerAdmissionsListView() {
  const [rows, setRows] = useState<AdmissionSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [applied, setApplied] = useState(EMPTY_FILTERS);
  useEffect(() => {
    ownerAdmissionApi.list().then(setRows).catch(e => setError(String(e))).finally(() => setLoading(false));
  }, []);
  const clear = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
  };
  const filteredRows = useMemo(() => {
    const query = applied.search.trim().toLowerCase();
    const fromTime = applied.from ? new Date(applied.from).getTime() : null;
    const toTime = applied.to ? new Date(applied.to).setHours(23, 59, 59, 999) : null;
    return rows.filter((admission) => {
      if (query && !`${admission.candidateName} ${admission.breed ?? ''}`.toLowerCase().includes(query)) return false;
      if (applied.status !== 'ALL' && admission.status !== applied.status) return false;
      const submitted = new Date(admission.submittedAt).getTime();
      if (fromTime !== null && submitted < fromTime) return false;
      if (toTime !== null && submitted > toTime) return false;
      return true;
    });
  }, [rows, applied]);
  return (
    <AdmissionListLayout
        title="My admissions"
        description="Track each application and its review status."
        actions={<LinkButton href="/owner/admissions/new" variant="primary" icon="plus">New admission</LinkButton>}
    >
      {loading ? <ListSkeleton rows={5} /> : error ? <Notice tone="error">{error}</Notice> : (
        <>
          <AdmissionFilterBar
            search={draft.search}
            onSearchChange={(value) => setDraft({ ...draft, search: value })}
            searchLabel="Search admissions"
            searchPlaceholder="Search horse name or breed"
            onApply={() => setApplied(draft)}
            onClear={clear}
          >
            <FilterSelect label="Status" value={draft.status} onChange={(status) => setDraft({ ...draft, status })} options={STATUS_OPTIONS} />
            <FilterDateRange
              from={draft.from}
              to={draft.to}
              onFromChange={(from) => setDraft({ ...draft, from })}
              onToChange={(to) => setDraft({ ...draft, to })}
            />
          </AdmissionFilterBar>
          {rows.length === 0 ? (
            <Panel>
              <EmptyState title="No admission history yet" description="Submit an application to start tracking its review." />
            </Panel>
          ) : (
            <AdmissionTable
              admissions={filteredRows}
              searchable={false}
              emptyTitle="No matching admissions"
              emptyDescription="Change the filters or clear them to see other records."
              emptyAction={<Button size="sm" onClick={clear}>Clear filters</Button>}
              detailHref={(id) => `/owner/admissions/${id}`}
              renderAvatar={(admission) => (
                <HorseAvatar name={admission.candidateName} image={admission.imageUrl} size={32} rounded="md" />
              )}
            />
          )}
        </>
      )}
    </AdmissionListLayout>
  );
}

