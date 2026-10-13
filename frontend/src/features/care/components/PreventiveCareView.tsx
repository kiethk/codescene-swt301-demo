'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import {
  Button,
  EmptyState,
  FormField,
  Icon,
  Notice,
  PageHeader,
  Panel,
  Pill,
  ScreenLayout,
  Select,
  Spinner,
} from '@/components/ui';
import { DataTable, type DataTableColumn } from '@/components/ui/DataTable';
import { careService } from '../services/careService';
import { horseService } from '@/features/horses/services/horseService';
import { SchedulePreventiveCareModal } from './SchedulePreventiveCareModal';
import { CareRecordDetailModal } from './CareRecordDetailModal';
import type { CareScheduleResponse, CareScheduleStatus, CareType } from '../types';
import type { Horse } from '@/features/stable/types';
import { displayError, formatDate } from '@/lib/display';

export function PreventiveCareView() {
  const [schedules, setSchedules] = useState<CareScheduleResponse[]>([]);
  const [horses, setHorses] = useState<Horse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');

  // Modals
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [selectedScheduleId, setSelectedScheduleId] = useState<number | null>(null);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const [schedRes, horseRes] = await Promise.all([
        careService.listSchedules({
          status: statusFilter !== 'ALL' ? (statusFilter as CareScheduleStatus) : undefined,
          careType: typeFilter !== 'ALL' ? (typeFilter as CareType) : undefined,
          size: 50,
        }),
        horseService.listAll().catch(() => []),
      ]);

      setSchedules(schedRes.content || []);
      setHorses(horseRes || []);
    } catch (err) {
      setError(displayError(err, 'Failed to load care schedules.'));
    } finally {
      setLoading(false);
    }
  }, [statusFilter, typeFilter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleStartSchedule = async (id: number) => {
    try {
      setActionLoadingId(id);
      await careService.startSchedule(id);
      await loadData();
    } catch (err) {
      setError(displayError(err, 'Failed to start care schedule.'));
    } finally {
      setActionLoadingId(null);
    }
  };

  // Metrics
  const scheduledCount = schedules.filter((s) => s.status === 'SCHEDULED').length;
  const inProgressCount = schedules.filter((s) => s.status === 'IN_PROGRESS').length;
  const completedCount = schedules.filter((s) => s.status === 'COMPLETED').length;

  const getHorseName = (horseId: number) => {
    const horse = horses.find((h) => h.id === horseId);
    return horse ? horse.name : `Horse #${horseId}`;
  };

  const columns: DataTableColumn<CareScheduleResponse>[] = [
    {
      id: 'id',
      header: 'ID',
      align: 'left',
      render: (row) => <span className="font-mono text-[12px]">#{row.id}</span>,
    },
    {
      id: 'horse',
      header: 'Horse',
      render: (row) => (
        <div>
          <span className="font-semibold text-[var(--color-text-primary)]">
            {getHorseName(row.horseId)}
          </span>
          <span className="text-[11px] text-[var(--color-text-muted)] block">
            ID: #{row.horseId}
          </span>
        </div>
      ),
    },
    {
      id: 'careType',
      header: 'Care Type',
      render: (row) => (
        <Pill
          tone={
            row.careType === 'URGENT'
              ? 'danger'
              : row.careType === 'ROUTINE'
              ? 'info'
              : 'neutral'
          }
          size="sm"
        >
          {row.careType}
        </Pill>
      ),
    },
    {
      id: 'scheduledAt',
      header: 'Scheduled Date',
      render: (row) => (
        <span className="whitespace-nowrap text-[13px]">
          {row.scheduledAt ? formatDate(row.scheduledAt) : 'As needed'}
        </span>
      ),
    },
    {
      id: 'description',
      header: 'Clinical Focus',
      render: (row) => (
        <span className="line-clamp-2 text-[12px] text-[var(--color-text-secondary)]">
          {row.description || 'Routine veterinary examination'}
        </span>
      ),
    },
    {
      id: 'status',
      header: 'Status',
      align: 'center',
      render: (row) => (
        <Pill
          tone={
            row.status === 'COMPLETED'
              ? 'success'
              : row.status === 'IN_PROGRESS'
              ? 'warning'
              : row.status === 'CANCELLED'
              ? 'danger'
              : 'info'
          }
          size="sm"
        >
          {row.status}
        </Pill>
      ),
    },
    {
      id: 'actions',
      header: 'Actions',
      align: 'right',
      render: (row) => (
        <div className="flex items-center justify-end gap-1.5">
          {row.status === 'SCHEDULED' && (
            <Button
              variant="primary"
              size="sm"
              loading={actionLoadingId === row.id}
              onClick={() => handleStartSchedule(row.id)}
            >
              Start
            </Button>
          )}

          {row.status === 'IN_PROGRESS' && row.careType === 'URGENT' && (
            <Link href={`/veterinarian/urgent/${row.id}`}>
              <Button variant="destructive" size="sm">
                Open Urgent
              </Button>
            </Link>
          )}

          <Button
            variant="secondary"
            size="sm"
            onClick={() => setSelectedScheduleId(row.id)}
          >
            Details
          </Button>
        </div>
      ),
    },
  ];

  return (
    <ScreenLayout variant="list">
      <PageHeader
        title="Veterinary Care & Preventive Medicine"
        description="Monitor equine preventive care schedules, routine examinations, treatments, and prescriptions."
        actions={
          <Button
            variant="primary"
            icon="plus"
            onClick={() => setShowScheduleModal(true)}
          >
            Schedule Preventive Care
          </Button>
        }
      />

      {error && (
        <Notice tone="error" title="Error" onDismiss={() => setError(null)}>
          {error}
        </Notice>
      )}

      {/* KPI metric cards */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Panel padded className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-info-soft)] text-[var(--color-info)]">
            <Icon name="calendar" size={20} />
          </div>
          <div>
            <span className="text-[12px] font-medium text-[var(--color-text-muted)]">
              Scheduled Care
            </span>
            <div className="text-xl font-bold text-[var(--color-text-primary)]">
              {scheduledCount}
            </div>
          </div>
        </Panel>

        <Panel padded className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-warning-soft)] text-[var(--color-warning)]">
            <Icon name="activity" size={20} />
          </div>
          <div>
            <span className="text-[12px] font-medium text-[var(--color-text-muted)]">
              In Progress
            </span>
            <div className="text-xl font-bold text-[var(--color-text-primary)]">
              {inProgressCount}
            </div>
          </div>
        </Panel>

        <Panel padded className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-success-soft)] text-[var(--color-success)]">
            <Icon name="check" size={20} />
          </div>
          <div>
            <span className="text-[12px] font-medium text-[var(--color-text-muted)]">
              Completed Exams
            </span>
            <div className="text-xl font-bold text-[var(--color-text-primary)]">
              {completedCount}
            </div>
          </div>
        </Panel>
      </div>

      {/* Filter Toolbar */}
      <Panel padded className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
          <FormField label="Care Category" className="w-full sm:w-48">
            <Select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
              <option value="ALL">All Categories</option>
              <option value="ROUTINE">Routine / Preventive</option>
              <option value="INITIAL">Initial Admission</option>
              <option value="URGENT">Urgent Cases</option>
            </Select>
          </FormField>

          <FormField label="Status" className="w-full sm:w-44">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="ALL">All Statuses</option>
              <option value="SCHEDULED">Scheduled</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </Select>
          </FormField>
        </div>

        <Button
          variant="secondary"
          size="sm"
          icon="refresh"
          onClick={loadData}
          disabled={loading}
        >
          Refresh
        </Button>
      </Panel>

      {/* Main Table */}
      {loading ? (
        <div className="py-16 flex justify-center items-center">
          <Spinner size="lg" />
        </div>
      ) : schedules.length === 0 ? (
        <Panel padded>
          <EmptyState
            icon="clipboard"
            title="No Care Schedules Found"
            description="No care appointments match the selected filters. Use 'Schedule Preventive Care' to add a new check-up or vaccination."
            action={
              <Button
                variant="primary"
                size="sm"
                icon="plus"
                onClick={() => setShowScheduleModal(true)}
              >
                Schedule Care
              </Button>
            }
          />
        </Panel>
      ) : (
        <DataTable
          rows={schedules}
          columns={columns}
          getRowKey={(row) => row.id}
          pageSize={15}
        />
      )}

      {/* Modals */}
      <SchedulePreventiveCareModal
        open={showScheduleModal}
        onClose={() => setShowScheduleModal(false)}
        horses={horses}
        onSuccess={loadData}
      />

      <CareRecordDetailModal
        scheduleId={selectedScheduleId}
        open={Boolean(selectedScheduleId)}
        onClose={() => setSelectedScheduleId(null)}
        onScheduleUpdated={loadData}
      />
    </ScreenLayout>
  );
}
