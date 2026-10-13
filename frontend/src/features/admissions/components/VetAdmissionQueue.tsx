'use client';

import { TBody, THead, Table, Td, Th, Tr } from '@/components/ui/Table';
import { useEffect, useMemo, useState, useCallback, type ReactNode } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { ApiError } from '@/services/api';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { HorseAvatar } from '@/components/ui/HorseAvatar';
import { Icon } from '@/components/ui/Icon';
import { Pill } from '@/components/ui/StatusBadge';
import { Tabs, type TabItem } from '@/components/ui/Tabs';
import { DetailSkeleton, EmptyState } from '@/components/ui/states';
import { FilterBar } from '@/components/ui/FilterBar';
import { DataTable, type DataTableColumn } from '@/components/ui/DataTable';
import { FormField } from '@/components/ui/FormField';
import { SearchInput, Select } from '@/components/ui/Input';
import { Notice } from '@/components/ui/Notice';
import { FilterChips } from '@/components/ui/SegmentedControl';
import { AdmissionListLayout } from '../shared/components/AdmissionListLayout';
import { AdmissionDetailLayout } from '../shared/components/AdmissionDetailLayout';
import { AdmissionDetailHeader } from '../shared/components/AdmissionDetailHeader';
import { AdmissionPipeline } from '../shared/components/AdmissionPipeline';
import { AdmissionSideCard, InfoRow } from '../shared/components/AdmissionInfoSection';
import { AdmissionStatusBadge } from '../shared/components/AdmissionStatusBadge';
import { admissionsApi } from '../services/api';
import type {
  AdmissionDetailResponse,
  AdmissionDocument,
  CareSchedule,
  CareScheduleStatus,
  CareType,
  HorseHealthMetricResponse,
  VetAdmissionQueueItem,
  VetQueueSummary,
  VetReviewResponse,
} from '../types';
import { VetReviewForm } from './VetReviewForm';

type QueuePillFilter = 'ALL' | 'AWAITING' | 'IN_PROGRESS';

const examStatusOptions: Array<{ value: CareScheduleStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'All Exam States' },
  { value: 'REQUESTED', label: 'Requested' },
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED', label: 'Completed' },
];

const examTypeOptions: Array<{ value: CareType | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'All Exam Types' },
  { value: 'INITIAL', label: 'Initial Exam' },
  { value: 'ROUTINE', label: 'Routine Exam' },
  { value: 'URGENT', label: 'Urgent Exam' },
];

function errorText(error: unknown) {
  if (error instanceof ApiError) {
    return `${error.status === 403 ? 'Permission denied' : error.status === 409 ? 'State conflict' : 'Request error'} (${error.status}${error.errorCode ? ` · ${error.errorCode}` : ''}): ${error.message}`;
  }
  return error instanceof Error ? error.message : 'Operation failed. Please try again.';
}

function formatLabel(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
}

function formatDate(value: string | null | undefined, includeTime = false) {
  if (!value) return 'Not scheduled';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, includeTime
    ? { dateStyle: 'medium', timeStyle: 'short' }
    : { dateStyle: 'medium' }).format(date);
}

function calculateAge(dateOfBirth: string | null | undefined): string {
  if (!dateOfBirth) return 'Age unavailable';
  const dob = new Date(dateOfBirth);
  if (Number.isNaN(dob.getTime())) return 'Age unavailable';
  const now = new Date();
  let years = now.getFullYear() - dob.getFullYear();
  const m = now.getMonth() - dob.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) {
    years--;
  }
  if (years < 1) {
    const months = Math.max(0, (now.getFullYear() - dob.getFullYear()) * 12 + now.getMonth() - dob.getMonth());
    return `${months} mo`;
  }
  return `${years} yo`;
}

function examTone(status: CareScheduleStatus | undefined) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'IN_PROGRESS') return 'primary' as const;
  if (status === 'SCHEDULED') return 'info' as const;
  if (status === 'REQUESTED') return 'warning' as const;
  return 'neutral' as const;
}

function examIcon(status: CareScheduleStatus | undefined) {
  if (status === 'COMPLETED') return 'check' as const;
  if (status === 'IN_PROGRESS') return 'activity' as const;
  if (status === 'SCHEDULED') return 'calendar' as const;
  if (status === 'REQUESTED') return 'clock' as const;
  return 'circle' as const;
}

function getPriorityBadge(careType: CareType | undefined) {
  if (careType === 'URGENT') {
    return { label: 'P1 · Urgent', tone: 'danger' as const, icon: 'alert-triangle' as const };
  }
  if (careType === 'INITIAL') {
    return { label: 'P2 · Initial', tone: 'info' as const, icon: 'stethoscope' as const };
  }
  return { label: 'P4 · Routine', tone: 'neutral' as const, icon: 'clock' as const };
}

function MedicalNoteBlock({
  label,
  value,
}: {
  label: string;
  value: string | null | undefined;
}) {
  if (!value) return null;
  return (
    <div className="rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface-subtle)] p-3">
      <span className="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-muted)]">
        {label}
      </span>
      <p className="mt-1 whitespace-pre-wrap text-xs leading-relaxed text-[var(--color-text-primary)]">
        {value}
      </p>
    </div>
  );
}

export function VetAdmissionQueue() {
  const { user } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  // Server-side queue state
  const [items, setItems] = useState<VetAdmissionQueueItem[]>([]);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [summary, setSummary] = useState<VetQueueSummary>({
    total: 0,
    awaiting: 0,
    inProgress: 0,
  });

  // Full-page examination workspace & selection state
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [targetScheduleId, setTargetScheduleId] = useState<number | null>(null);
  const [deepLinkedSchedule, setDeepLinkedSchedule] = useState<CareSchedule | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [isDrawerDirty, setIsDrawerDirty] = useState(false);
  const [showCloseConfirm, setShowCloseConfirm] = useState(false);

  // Detail data for opened admission
  const [detail, setDetail] = useState<AdmissionDetailResponse | null>(null);
  const [documents, setDocuments] = useState<AdmissionDocument[]>([]);
  const [metrics, setMetrics] = useState<HorseHealthMetricResponse[]>([]);
  const [careSchedules, setCareSchedules] = useState<CareSchedule[]>([]);
  const [stallCodes, setStallCodes] = useState<Record<number, string>>({});
  const [activeTab, setActiveTab] = useState<string>('exam');

  // Filters state
  const [searchQuery, setSearchQuery] = useState('');
  const [queuePillFilter, setQueuePillFilter] = useState<QueuePillFilter>('ALL');
  const [admissionFilter, setAdmissionFilter] = useState<'ALL' | 'VET_REVIEW'>('ALL');
  const [examStatusFilter, setExamStatusFilter] = useState<CareScheduleStatus | 'ALL'>('ALL');
  const [examTypeFilter, setExamTypeFilter] = useState<CareType | 'ALL'>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<'ALL' | 'URGENT' | 'NORMAL'>('ALL');

  // Request & operation state
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [startingExam, setStartingExam] = useState(false);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [success, setSuccess] = useState('');
  const [scheduleWarning, setScheduleWarning] = useState('');
  const [reload, setReload] = useState(0);

  // Deep link support via ?id= query param
  useEffect(() => {
    const idParam = searchParams.get('id');
    const scheduleIdParam = searchParams.get('scheduleId');
    if (scheduleIdParam) {
      const parsedScheduleId = Number(scheduleIdParam);
      if (!Number.isNaN(parsedScheduleId) && parsedScheduleId > 0) {
        let active = true;
        Promise.resolve().then(() => {
          if (!active) return;
          setTargetScheduleId(parsedScheduleId);
          setDeepLinkedSchedule(null);
          setSelectedId(null);
          setDetail(null);
          setDrawerOpen(true);
          setLoadingDetail(true);
          setDetailError('');
        });
        admissionsApi.getCareScheduleDetail(parsedScheduleId)
          .then(({ schedule }) => {
            if (!active) return;
            if (!schedule.admissionId) {
              setDetailError('This care schedule is not linked to an Admission record.');
              return;
            }
            setDeepLinkedSchedule(schedule);
            setSelectedId(schedule.admissionId);
            setActiveTab('exam');
          })
          .catch((cause) => {
            if (active) setDetailError(errorText(cause));
          })
          .finally(() => {
            if (active) setLoadingDetail(false);
          });
        return () => {
          active = false;
        };
      }
    } else if (idParam) {
      const parsed = Number(idParam);
      if (!Number.isNaN(parsed) && parsed > 0) {
        Promise.resolve().then(() => {
          setTargetScheduleId(null);
          setDeepLinkedSchedule(null);
          setSelectedId(parsed);
          setActiveTab('exam');
          setDrawerOpen(true);
        });
      }
    }
  }, [searchParams]);


  // Server-side filtered queue loading
  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return;
      setLoading(true);
      setError('');
    });

    Promise.all([
      admissionsApi.getVetQueue({
        search: searchQuery.trim() || undefined,
        pill: queuePillFilter,
        admissionStatus: admissionFilter !== 'ALL' ? admissionFilter : undefined,
        scheduleStatus: examStatusFilter !== 'ALL' ? examStatusFilter : undefined,
        careType: examTypeFilter !== 'ALL' ? examTypeFilter : undefined,
        priority: priorityFilter !== 'ALL' ? priorityFilter : undefined,
        page,
        size: pageSize,
      }),
      admissionsApi.getVetQueueSummary(),
    ])
      .then(([queuePage, summaryData]) => {
        if (!active) return;
        setItems(queuePage.content);
        setTotalElements(queuePage.totalElements);
        setSummary(summaryData);
      })
      .catch((cause) => {
        if (active) setError(errorText(cause));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [
    searchQuery,
    queuePillFilter,
    admissionFilter,
    examStatusFilter,
    examTypeFilter,
    priorityFilter,
    page,
    pageSize,
    reload,
  ]);

  const hasActiveFilters =
    searchQuery !== '' ||
    queuePillFilter !== 'ALL' ||
    admissionFilter !== 'ALL' ||
    examStatusFilter !== 'ALL' ||
    examTypeFilter !== 'ALL' ||
    priorityFilter !== 'ALL';

  const resetFilters = () => {
    setSearchQuery('');
    setQueuePillFilter('ALL');
    setAdmissionFilter('ALL');
    setExamStatusFilter('ALL');
    setExamTypeFilter('ALL');
    setPriorityFilter('ALL');
    setPage(0);
  };

  // Fetch admission details via vet-enforced endpoint when selected
  useEffect(() => {
    if (selectedId === null) return;
    let active = true;

    Promise.resolve().then(() => {
      if (active) {
        setLoadingDetail(true);
        setDetailError('');
        setDetail(null);
        setDocuments([]);
        setMetrics([]);
        setCareSchedules([]);
      }
    });

    Promise.all([
      admissionsApi.getVetAdmissionDetail(selectedId),
      admissionsApi.getDocuments(selectedId),
      admissionsApi.getCareSchedules({ admissionId: selectedId, size: 50 }),
    ])
      .then(async ([detailData, docs, schedulesPage]) => {
        if (!active) return;
        setDetail(detailData);
        setDocuments(docs);
        setCareSchedules(schedulesPage.content);
        if (detailData.quarantineStallCode) {
          setStallCodes((prev) => ({
            ...prev,
            [detailData.admissionId]: detailData.quarantineStallCode!,
          }));
        }
        if (detailData.horseId) {
          try {
            const metricData = await admissionsApi.getHorseHealthMetrics(detailData.horseId);
            if (active) setMetrics(metricData);
          } catch {
            if (active) setMetrics([]);
          }
        }
      })
      .catch((cause) => {
        if (active) setDetailError(errorText(cause));
      })
      .finally(() => {
        if (active) setLoadingDetail(false);
      });

    return () => {
      active = false;
    };
  }, [selectedId, reload]);

  const selectedRow = items.find((row) =>
    targetScheduleId
      ? row.careSchedule?.id === targetScheduleId
      : row.admissionId === selectedId,
  ) ?? null;

  const detailScheduleFallback: CareSchedule | null = detail?.initialExamSchedule
    ? {
        id: detail.initialExamSchedule.scheduleId,
        horseId: detail.horseId ?? 0,
        admissionId: detail.admissionId,
        careType: (detail.initialExamSchedule.careType as CareType) || 'INITIAL',
        status: (detail.initialExamSchedule.status as CareScheduleStatus) || 'REQUESTED',
        veterinarianId: detail.initialExamSchedule.veterinarianId,
        assignedVetId: detail.initialExamSchedule.veterinarianId,
        scheduledAt: detail.initialExamSchedule.scheduledAt,
        scheduledDate: detail.initialExamSchedule.scheduledDate,
      }
    : null;

  const explicitlySelectedSchedule = targetScheduleId
    ? careSchedules.find((schedule) => schedule.id === targetScheduleId) ??
      (deepLinkedSchedule?.id === targetScheduleId ? deepLinkedSchedule : null)
    : null;
  const assignedActiveSchedule = careSchedules
    .filter((schedule) =>
      ['SCHEDULED', 'IN_PROGRESS'].includes(schedule.status) &&
      (schedule.veterinarianId ?? schedule.assignedVetId) === user?.userId,
    )
    .sort((a, b) => {
      if (a.status !== b.status) return a.status === 'IN_PROGRESS' ? -1 : 1;
      return new Date(a.scheduledAt ?? a.scheduledDate ?? 0).getTime()
        - new Date(b.scheduledAt ?? b.scheduledDate ?? 0).getTime();
    })[0] ?? null;
  const currentActiveSchedule =
    explicitlySelectedSchedule ??
    assignedActiveSchedule ??
    selectedRow?.careSchedule ??
    detailScheduleFallback;
  const currentScheduleRecord = detail?.healthRecords?.find(
    (record) => record.careScheduleId === currentActiveSchedule?.id,
  );
  const displayedTrainingDecision =
    currentScheduleRecord?.trainingDecision ??
    (currentActiveSchedule?.careType === 'INITIAL' ? detail?.vetTrainingDecision : null);

  const assignedVetId = currentActiveSchedule?.veterinarianId ?? currentActiveSchedule?.assignedVetId ?? null;
  const assignedElsewhere = Boolean(assignedVetId && assignedVetId !== user?.userId);

  // Requirement: Disable Start for REQUESTED schedules; only allow starting when SCHEDULED
  const canStartExam = Boolean(
    currentActiveSchedule &&
      currentActiveSchedule.status === 'SCHEDULED' &&
      assignedVetId === user?.userId,
  );

  const canCompleteExam = Boolean(
    currentActiveSchedule &&
      ['SCHEDULED', 'IN_PROGRESS'].includes(currentActiveSchedule.status) &&
      assignedVetId === user?.userId,
  );

  const latestMetrics = metrics[0] ?? null;

  const selectedHistory = useMemo(() => {
    const list: Array<{
      id: number;
      label: string;
      status: CareScheduleStatus;
      description: string;
      scheduledAt: string | null;
      assignedVetId: number | null;
      createdAt?: string;
    }> = [];

    careSchedules
      .filter((cs) => cs.admissionId === selectedId)
      .forEach((cs) => {
        list.push({
          id: cs.id,
          label: `${formatLabel(cs.careType)} Care Schedule #${cs.id}`,
          status: cs.status,
          description: cs.description || `${formatLabel(cs.careType)} care schedule`,
          scheduledAt: cs.scheduledAt || cs.scheduledDate || null,
          assignedVetId: cs.veterinarianId ?? cs.assignedVetId ?? null,
          createdAt: cs.createdAt,
        });
      });

    return list.sort((a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime());
  }, [careSchedules, selectedId, detail]);

  const handleOpenAdmission = (id: number, schedule: CareSchedule | null) => {
    setTargetScheduleId(schedule?.id ?? null);
    setDeepLinkedSchedule(null);
    setSelectedId(id);
    setActiveTab('exam');
    setScheduleWarning('');
    setIsDrawerDirty(false);
    setDrawerOpen(true);
  };

  const handleDirtyChange = useCallback((dirty: boolean) => {
    setIsDrawerDirty(dirty);
  }, []);

  const handleRequestCloseDrawer = useCallback(() => {
    if (isDrawerDirty) {
      setShowCloseConfirm(true);
    } else {
      setDrawerOpen(false);
      setSelectedId(null);
      setTargetScheduleId(null);
      setDeepLinkedSchedule(null);
      router.replace('/veterinarian/admissions', { scroll: false });
    }
  }, [isDrawerDirty, router]);

  // Keep the full-page examination workspace aligned with the Trainer detail flow.
  useEffect(() => {
    if (!drawerOpen) return;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [drawerOpen]);

  function reloadQueue() {
    setLoading(true);
    setReload((curr) => curr + 1);
  }

  function handleRefresh() {
    setSuccess('');
    setError('');
    setScheduleWarning('');
    reloadQueue();
  }

  async function handleStartExam() {
    if (!currentActiveSchedule || startingExam || !canStartExam) return;
    try {
      setStartingExam(true);
      setError('');
      await admissionsApi.startCareSchedule(currentActiveSchedule.id);
      setSuccess(`Examination #${currentActiveSchedule.id} started. You can now record clinical findings.`);
      setReload((curr) => curr + 1);
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setStartingExam(false);
    }
  }

  function handleReviewSuccess(result: VetReviewResponse | CareSchedule, completionKind: 'INITIAL' | 'CARE_SCHEDULE') {
    if (completionKind === 'INITIAL' && 'admissionId' in result) {
      setSuccess(`Admission #${result.admissionId} (${detail?.candidate?.name ?? selectedRow?.candidateName ?? 'Horse'}): clinical examination completed and forwarded to Trainer assessment.`);
    } else {
      setSuccess(`Care schedule #${currentActiveSchedule?.id ?? '—'} (${detail?.candidate?.name ?? selectedRow?.candidateName ?? 'Horse'}): examination completed without changing the Admission workflow.`);
    }
    setScheduleWarning('');
    setIsDrawerDirty(false);
    setDrawerOpen(false);
    reloadQueue();
  }

  const workspaceTabs: TabItem[] = [
    { id: 'exam', label: 'Clinical Examination', icon: 'stethoscope' },
    {
      id: 'history',
      label: 'Medical Records',
      icon: 'clipboard',
      count: (detail?.healthRecords?.length ?? 0) > 0 ? detail?.healthRecords?.length : undefined,
    },
    {
      id: 'vitals',
      label: 'Vitals History',
      icon: 'heart-pulse',
      count: metrics.length > 0 ? metrics.length : undefined,
    },
    {
      id: 'documents',
      label: 'Intake & Pedigree',
      icon: 'file-text',
      count: documents.length > 0 ? documents.length : undefined,
    },
  ];

  const queueColumns: DataTableColumn<(typeof items)[number]>[] = [
    {
      id: 'horse',
      header: 'Horse',
      render: (row) => (
        <div className="flex min-w-0 items-center gap-3">
          <HorseAvatar name={row.candidateName} image={row.imageUrl} size={32} rounded="md" />
          <div className="min-w-0">
            <span className="block truncate font-semibold">{row.candidateName}</span>
            <span className="block truncate text-xs text-[var(--color-text-muted)]">
              #{row.admissionId} · {row.breed || 'Breed not provided'} · {calculateAge(row.dateOfBirth)}
            </span>
          </div>
        </div>
      ),
    },
    {
      id: 'priority',
      header: 'Priority',
      render: (row) => {
        const priority = getPriorityBadge(row.careSchedule?.careType);
        return <Pill tone={priority.tone} size="sm" icon={priority.icon}>{priority.label}</Pill>;
      },
    },
    {
      id: 'status',
      header: 'Status',
      render: (row) => <AdmissionStatusBadge status={row.admissionStatus} size="sm" />,
    },
    {
      id: 'exam',
      header: 'Exam',
      render: (row) => row.careSchedule ? (
        <Pill tone={examTone(row.careSchedule.status)} size="sm" icon={examIcon(row.careSchedule.status)}>
          {formatLabel(row.careSchedule.status)}
        </Pill>
      ) : (
        <Pill tone="neutral" size="sm">No schedule</Pill>
      ),
    },
    {
      id: 'stall',
      header: 'Stall',
      render: (row) => stallCodes[row.admissionId] || row.quarantineStallCode || (row.quarantineStallId ? `Q-Stall #${row.quarantineStallId}` : 'Quarantine'),
    },
    {
      id: 'scheduled',
      header: 'Scheduled',
      className: 'whitespace-nowrap',
      render: (row) => row.careSchedule?.scheduledAt ? (
        <span className="font-metric">{formatDate(row.careSchedule.scheduledAt, true)}</span>
      ) : row.careSchedule?.scheduledDate ? (
        <span className="font-metric text-[var(--color-text-muted)]">Target: {formatDate(row.careSchedule.scheduledDate)}</span>
      ) : (
        <span className="italic text-[var(--color-text-muted)]">Awaiting slot</span>
      ),
    },
    {
      id: 'vet',
      header: 'Veterinarian',
      className: 'whitespace-nowrap',
      render: (row) => {
        const rowVetId = row.careSchedule?.veterinarianId ?? row.careSchedule?.assignedVetId ?? null;
        if (rowVetId && user?.userId && rowVetId === user.userId) {
          return <span className="font-semibold text-[var(--color-primary)]">Assigned to you</span>;
        }
        return rowVetId ? `Vet #${rowVetId}` : <span className="text-[var(--color-text-muted)]">Unassigned</span>;
      },
    },
    {
      id: 'action',
      header: 'Action',
      align: 'right',
      render: (row) => {
        const status = row.careSchedule?.status;
        const action =
          status === 'IN_PROGRESS' ? ({ label: 'Continue', icon: 'activity', primary: true } as const)
          : status === 'SCHEDULED' ? ({ label: 'Examine', icon: 'stethoscope', primary: true } as const)
          : status === 'COMPLETED' ? ({ label: 'View results', icon: 'check', primary: false } as const)
          : ({ label: 'Details', icon: 'chevron-right', primary: false } as const);
        return (
          <Button
            size="sm"
            variant={action.primary ? 'primary' : 'secondary'}
            icon={action.icon}
            className="min-w-[6.5rem]"
            onClick={() => handleOpenAdmission(row.admissionId, row.careSchedule)}
          >
            {action.label}
          </Button>
        );
      },
    },
  ];

  const examDetailsCard = detail ? (
    <AdmissionSideCard title="Examination details">
      <InfoRow label="Priority" value={selectedRow ? getPriorityBadge(selectedRow.careSchedule?.careType).label : 'Standard'} />
      <InfoRow label="Schedule" value={currentActiveSchedule ? formatLabel(currentActiveSchedule.status) : 'Not scheduled'} />
      <InfoRow label="Quarantine stall" value={detail.quarantineStallCode || 'Not assigned'} />
      <InfoRow label="Trainer" value={detail.trainerName || (detail.trainerId ? `Trainer #${detail.trainerId}` : 'Pending assignment')} />
      {currentActiveSchedule?.scheduledAt && <InfoRow label="Scheduled" value={formatDate(currentActiveSchedule.scheduledAt, true)} />}
      {canStartExam && (
        <Button type="button" className="w-full" variant="primary" loading={startingExam} onClick={handleStartExam} icon="activity">
          Start examination
        </Button>
      )}
    </AdmissionSideCard>
  ) : null;

  return (
    <QueueShell showHeader={!drawerOpen}>
      {success && <Notice tone="success" onDismiss={() => setSuccess('')}>{success}</Notice>}
      {error && <Notice tone="error" onDismiss={() => setError('')}>{error}</Notice>}
      {scheduleWarning && <Notice tone="warning" onDismiss={() => setScheduleWarning('')}>{scheduleWarning}</Notice>}

      {!drawerOpen && (
        <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <FilterChips
          label="Queue"
          value={queuePillFilter}
          onChange={(value) => {
            setQueuePillFilter(value);
            setPage(0);
          }}
          options={[
            { value: 'ALL', label: 'Active', count: summary.total },
            { value: 'AWAITING', label: 'Awaiting', count: summary.awaiting },
            { value: 'IN_PROGRESS', label: 'In exam', count: summary.inProgress },
          ]}
        />
        <Button type="button" onClick={handleRefresh} disabled={loading} icon="refresh" variant="secondary" size="sm">
          Refresh
        </Button>
      </div>

      <FilterBar className="[&_input]:min-h-9 [&_select]:min-h-9" onSubmit={(event) => event.preventDefault()}>
        <FormField label="Search admissions" className="min-w-[14rem] flex-[1.4]">
          <SearchInput
            label="Search admissions"
            placeholder="Horse name, admission number, or breed"
            value={searchQuery}
            onChange={(value) => {
              setSearchQuery(value);
              setPage(0);
            }}
          />
        </FormField>
        <FormField label="Stage" className="min-w-[9rem] flex-1">
          <Select
            value={admissionFilter}
            onChange={(e) => {
              setAdmissionFilter(e.target.value as typeof admissionFilter);
              setPage(0);
            }}
          >
            <option value="ALL">All stages</option>
            <option value="VET_REVIEW">Initial review</option>
          </Select>
        </FormField>
        <FormField label="Exam status" className="min-w-[9rem] flex-1">
          <Select
            value={examStatusFilter}
            onChange={(e) => {
              setExamStatusFilter(e.target.value as CareScheduleStatus | 'ALL');
              setPage(0);
            }}
          >
            {examStatusOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </FormField>
        <FormField label="Exam type" className="min-w-[9rem] flex-1">
          <Select
            value={examTypeFilter}
            onChange={(e) => {
              setExamTypeFilter(e.target.value as CareType | 'ALL');
              setPage(0);
            }}
          >
            {examTypeOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </FormField>
        <FormField label="Priority" className="min-w-[9rem] flex-1">
          <Select
            value={priorityFilter}
            onChange={(e) => {
              setPriorityFilter(e.target.value as typeof priorityFilter);
              setPage(0);
            }}
          >
            <option value="ALL">All priorities</option>
            <option value="URGENT">Urgent (P1)</option>
            <option value="NORMAL">Standard (P2-P4)</option>
          </Select>
        </FormField>
        <Button type="button" variant="secondary" onClick={resetFilters} disabled={!hasActiveFilters}>
          Clear
        </Button>
      </FilterBar>

      <DataTable
        rows={items}
        columns={queueColumns}
        getRowKey={(row) => (row.careSchedule?.id ? `schedule-${row.careSchedule.id}` : `admission-${row.admissionId}`)}
        ariaLabel="Veterinary admission queue"
        loading={loading}
        emptyTitle="No matching admissions found"
        emptyDescription="No candidates match your current search query or active filter settings."
        emptyAction={<Button size="sm" variant="secondary" onClick={resetFilters}>Clear filters</Button>}
        pagination={{ page, pageSize, total: totalElements, onPageChange: setPage, disabled: loading }}
      />
        </>
      )}

      {/* Full-page clinical examination workspace */}
      {drawerOpen && (selectedId !== null || targetScheduleId !== null) && (
        <AdmissionDetailLayout
          onBack={handleRequestCloseDrawer}
          header={detail ? (
            <AdmissionDetailHeader
              detail={detail}
              horsePhotoUrl={detail.documents.find((doc) => doc.documentType === 'HORSE_PHOTO')
                ? admissionsApi.assetUrl(detail.documents.find((doc) => doc.documentType === 'HORSE_PHOTO')!.fileUrl)
                : undefined}
            />
          ) : (
            <div className="border-b border-[var(--color-border)] bg-[var(--color-surface)] p-4 text-sm font-semibold text-[var(--color-text-primary)]">
              {selectedRow?.candidateName ?? 'Loading admission'}
            </div>
          )}
          pipeline={detail ? <AdmissionPipeline detail={detail} /> : <DetailSkeleton />}
          content={(
            <div className="min-w-0 space-y-5">
              <Tabs tabs={workspaceTabs} active={activeTab} onChange={setActiveTab} />
              {loadingDetail || (!detail && !detailError) ? (
                <DetailSkeleton />
              ) : detailError ? (
                <EmptyState
                  icon="alert-triangle"
                  title="Could not load examination record"
                  description={detailError}
                  action={
                    selectedId === null
                      ? <Button onClick={handleRequestCloseDrawer}>Close</Button>
                      : <Button onClick={handleRefresh}>Retry</Button>
                  }
                />
              ) : detail ? (
                <div>
                  {/* Tab 1: Clinical Examination & VetReviewForm */}
                  {/* Tab 1: Clinical Examination & VetReviewForm (kept mounted to preserve state across tab switches) */}
                  <div className={activeTab === 'exam' ? 'block' : 'hidden'}>
                    {canCompleteExam ? (
                      <VetReviewForm
                        key={`${detail.admissionId}-${currentActiveSchedule?.id}`}
                        admissionId={detail.admissionId}
                        horseId={detail.horseId}
                        candidateName={detail.candidate?.name ?? selectedRow?.candidateName ?? 'Candidate'}
                        quarantineStallCode={detail.quarantineStallCode}
                        careScheduleId={currentActiveSchedule?.id}
                        careType={currentActiveSchedule?.careType}
                        scheduleStatus={currentActiveSchedule?.status}
                        onStartExam={handleStartExam}
                        asideTop={examDetailsCard}
                        onSuccess={handleReviewSuccess}
                        onDirtyChange={handleDirtyChange}
                      />
                    ) : currentActiveSchedule?.status === 'COMPLETED' ? (
                      <div className="rounded-[var(--radius-md)] border border-[var(--color-success)] bg-[var(--color-success-soft)] p-5 text-center">
                        <Icon name="check" size={28} className="mx-auto text-[var(--color-success)]" />
                        <h3 className="mt-2 text-lg font-bold text-[var(--color-success)]">
                          Examination Completed
                        </h3>
                        <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
                          {currentActiveSchedule.careType === 'INITIAL'
                            ? 'The veterinary review for this intake stage has already been submitted and finalized.'
                            : 'This care schedule has already been completed. The Admission workflow was not changed.'}
                        </p>
                        {displayedTrainingDecision && (
                          <div className="mt-3">
                            <Pill
                              tone={displayedTrainingDecision === 'ALLOWED' ? 'success' : 'danger'}
                            >
                              Training Decision: {formatLabel(displayedTrainingDecision)}
                            </Pill>
                          </div>
                        )}
                      </div>
                    ) : (
                      <div className="p-8 text-center text-[var(--color-text-muted)]">
                        <Icon name="stethoscope" size={32} className="mx-auto mb-2 opacity-40" />
                        <p className="text-base font-semibold text-[var(--color-text-secondary)]">
                          Clinical review locked
                        </p>
                        <p className="text-xs mt-1">
                          {currentActiveSchedule?.status === 'REQUESTED'
                            ? 'The care schedule is waiting for automatic assignment.'
                            : assignedElsewhere
                              ? 'This case is assigned to another veterinarian.'
                              : 'No active examination found for this admission.'}
                        </p>
                      </div>
                    )}
                  </div>

                  {/* Tab 2: Medical Records History */}
                  {activeTab === 'history' && (
                    <div className="space-y-5">
                      <div>
                        <h3 className="text-sm font-bold text-[var(--color-text-primary)]">
                          Completed Health Records
                        </h3>
                        {detail.healthRecords && detail.healthRecords.length > 0 ? (
                          <ul className="mt-3 space-y-3">
                            {detail.healthRecords.map((record) => (
                              <li
                                key={record.id}
                                className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
                              >
                                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border)] pb-2.5">
                                  <div className="flex items-center gap-2">
                                    <span className="text-sm font-bold text-[var(--color-text-primary)]">
                                      {formatLabel(record.recordType)} Exam
                                    </span>
                                    <span className="font-metric text-xs text-[var(--color-text-muted)]">
                                      Record #{record.id}
                                    </span>
                                  </div>
                                  <div className="flex items-center gap-2">
                                    <span className="font-metric text-xs text-[var(--color-text-secondary)]">
                                      {formatDate(record.examinedAt, true)}
                                    </span>
                                  </div>
                                </div>

                                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                  <MedicalNoteBlock label="Symptoms" value={record.symptoms} />
                                  <MedicalNoteBlock label="Findings" value={record.findings} />
                                  <MedicalNoteBlock label="Diagnosis" value={record.diagnosis} />
                                  <MedicalNoteBlock label="Treatment Plan" value={record.treatment} />
                                  <MedicalNoteBlock label="Notes & Context" value={record.notes} />
                                </div>

                                {record.trainingDecision && (
                                  <div className="mt-2.5 flex items-center gap-2">
                                    <span
                                      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                                        record.trainingDecision === 'ALLOWED'
                                          ? 'bg-[var(--color-success-soft)] text-[var(--color-success)]'
                                          : 'bg-[var(--color-danger-soft)] text-[var(--color-danger)]'
                                      }`}
                                    >
                                      Training Decision: {record.trainingDecision}
                                    </span>
                                  </div>
                                )}

                                {record.restrictionDetails && (
                                  <div className="mt-2 rounded bg-[var(--color-warning-soft)] p-2 text-xs text-[var(--color-warning)]">
                                    <strong>Restriction Protocol:</strong> {record.restrictionDetails}
                                  </div>
                                )}
                              </li>
                            ))}
                          </ul>
                        ) : (
                          <p className="mt-2 text-xs text-[var(--color-text-muted)] italic">
                            No health records filed for this candidate yet.
                          </p>
                        )}
                      </div>

                      <div className="border-t border-[var(--color-border)] pt-4">
                        <h3 className="text-sm font-bold text-[var(--color-text-primary)]">
                          Examination Schedule Log
                        </h3>
                        {selectedHistory.length > 0 ? (
                          <ol className="mt-3 space-y-2.5">
                            {selectedHistory.map((item, idx) => (
                              <li
                                key={`${item.id}-${idx}`}
                                className="flex items-start gap-3 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface-subtle)] p-3 text-xs"
                              >
                                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[var(--color-primary-soft)] text-xs font-bold text-[var(--color-primary)]">
                                  {idx + 1}
                                </span>
                                <div className="min-w-0 flex-1">
                                  <div className="flex flex-wrap items-center justify-between gap-2">
                                    <span className="font-bold text-[var(--color-text-primary)]">
                                      {item.label}
                                    </span>
                                    <Pill tone={examTone(item.status)} size="sm">
                                      {formatLabel(item.status)}
                                    </Pill>
                                  </div>
                                  <p className="mt-0.5 text-xs text-[var(--color-text-secondary)]">
                                    {item.description}
                                  </p>
                                  <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-[var(--color-text-muted)]">
                                    <span>Scheduled: {item.scheduledAt ? formatDate(item.scheduledAt, true) : 'Pending'}</span>
                                    <span>·</span>
                                    <span>Assigned Vet: {item.assignedVetId ? `#${item.assignedVetId}` : 'Unassigned'}</span>
                                  </div>
                                </div>
                              </li>
                            ))}
                          </ol>
                        ) : (
                          <p className="mt-2 text-xs text-[var(--color-text-muted)] italic">
                            No care schedule logs recorded.
                          </p>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Tab 3: Vitals & Health Metrics History */}
                  {activeTab === 'vitals' && (
                    <div className="space-y-4">
                      <div className="flex items-center justify-between">
                        <h3 className="text-sm font-bold text-[var(--color-text-primary)]">
                          Recorded Vitals &amp; Telemetry
                        </h3>
                        <span className="text-xs text-[var(--color-text-muted)]">
                          {metrics.length} readings on record
                        </span>
                      </div>

                      {metrics.length > 0 ? (
                        <div className="overflow-x-auto rounded-[var(--radius-md)] border border-[var(--color-border)]">
                          <Table bare>
                            <THead>
                              <Tr>
                                <Th className="px-3 py-2">Recorded At</Th>
                                <Th className="px-3 py-2">Temp</Th>
                                <Th className="px-3 py-2">Heart Rate</Th>
                                <Th className="px-3 py-2">Resp Rate</Th>
                                <Th className="px-3 py-2">Weight</Th>
                                <Th className="px-3 py-2">BCS</Th>
                                <Th className="px-3 py-2">Hydration</Th>
                                <Th className="px-3 py-2">Notes</Th>
                              </Tr>
                            </THead>
                            <TBody>
                              {metrics.map((m) => (
                                <Tr key={m.id} className="hover:bg-[var(--color-surface-subtle)]">
                                  <Td className="px-3 py-2 text-[var(--color-text-primary)]">
                                    {formatDate(m.recordedAt, true)}
                                  </Td>
                                  <Td className="px-3 py-2">
                                    {m.temperature != null ? `${m.temperature} °C` : '—'}
                                  </Td>
                                  <Td className="px-3 py-2">
                                    {m.heartRate != null ? `${m.heartRate} bpm` : '—'}
                                  </Td>
                                  <Td className="px-3 py-2">
                                    {m.respiratoryRate != null ? `${m.respiratoryRate} rpm` : '—'}
                                  </Td>
                                  <Td className="px-3 py-2">
                                    {m.weight != null ? `${m.weight} kg` : '—'}
                                  </Td>
                                  <Td className="px-3 py-2">
                                    {m.bodyConditionScore != null ? `${m.bodyConditionScore} / 9` : '—'}
                                  </Td>
                                  <Td className="px-3 py-2 font-sans">
                                    {m.hydrationStatus || '—'}
                                  </Td>
                                  <Td className="px-3 py-2 font-sans text-[var(--color-text-secondary)] max-w-xs truncate">
                                    {m.notes || '—'}
                                  </Td>
                                </Tr>
                              ))}
                            </TBody>
                          </Table>
                        </div>
                      ) : (
                        <div className="rounded-[var(--radius-md)] border border-dashed border-[var(--color-border)] p-6 text-center text-[var(--color-text-muted)]">
                          <Icon name="heart-pulse" size={24} className="mx-auto mb-1.5 opacity-50" />
                          <p className="text-xs font-medium text-[var(--color-text-secondary)]">
                            No telemetry metrics recorded yet
                          </p>
                        </div>
                      )}

                      {latestMetrics && (
                        <div className="rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] p-3 border border-[var(--color-border)]">
                          <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-text-muted)]">
                            Latest Telemetry Baseline ({formatDate(latestMetrics.recordedAt, true)})
                          </span>
                          <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-6 font-metric text-xs">
                            <div>
                              <span className="text-xs text-[var(--color-text-muted)] block font-sans">TEMP</span>
                              <strong>{latestMetrics.temperature ?? '—'} °C</strong>
                            </div>
                            <div>
                              <span className="text-xs text-[var(--color-text-muted)] block font-sans">PULSE</span>
                              <strong>{latestMetrics.heartRate ?? '—'} bpm</strong>
                            </div>
                            <div>
                              <span className="text-xs text-[var(--color-text-muted)] block font-sans">RESP</span>
                              <strong>{latestMetrics.respiratoryRate ?? '—'} rpm</strong>
                            </div>
                            <div>
                              <span className="text-xs text-[var(--color-text-muted)] block font-sans">WEIGHT</span>
                              <strong>{latestMetrics.weight ?? '—'} kg</strong>
                            </div>
                            <div>
                              <span className="text-xs text-[var(--color-text-muted)] block font-sans">BCS</span>
                              <strong>{latestMetrics.bodyConditionScore ?? '—'} / 9</strong>
                            </div>
                            <div>
                              <span className="text-xs text-[var(--color-text-muted)] block font-sans">HYDRATION</span>
                              <strong className="font-sans text-xs">{latestMetrics.hydrationStatus ?? 'Normal'}</strong>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Tab 4: Intake Documents & Pedigree */}
                  {activeTab === 'documents' && (
                    <div className="grid gap-5 xl:grid-cols-2">
                      <div>
                        <h3 className="text-sm font-bold text-[var(--color-text-primary)]">
                          Attached Admission Documents
                        </h3>
                        {documents.length > 0 ? (
                          <ul className="mt-3 space-y-2">
                            {documents.map((doc) => (
                              <li
                                key={doc.id}
                                className="flex items-start gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-3 text-xs"
                              >
                                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded bg-[var(--color-primary-soft)] text-[var(--color-primary)]">
                                  <Icon name="file-text" size={14} />
                                </span>
                                <div className="min-w-0 flex-1">
                                  <a
                                    href={doc.fileUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="font-semibold text-[var(--color-primary)] hover:underline truncate block"
                                  >
                                    {formatLabel(doc.documentType)}
                                  </a>
                                  <div className="mt-0.5 flex items-center gap-2 text-xs text-[var(--color-text-muted)] font-metric">
                                    {doc.recordDate && <span>Dated: {formatDate(doc.recordDate)}</span>}
                                    <span>Uploaded: {formatDate(doc.uploadedAt)}</span>
                                  </div>
                                </div>
                              </li>
                            ))}
                          </ul>
                        ) : (
                          <p className="mt-2 text-xs text-[var(--color-text-muted)] italic">
                            No documents attached.
                          </p>
                        )}
                      </div>

                      <div className="space-y-4">
                        <div className="rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] p-3.5 border border-[var(--color-border)]">
                          <h4 className="text-xs font-bold text-[var(--color-text-primary)]">
                            Pedigree &amp; Registry Information
                          </h4>
                          <dl className="mt-2 space-y-1.5 text-xs">
                            <div className="flex justify-between border-b border-[var(--color-border)] pb-1">
                              <span className="text-[var(--color-text-muted)]">Registry:</span>
                              <span className="font-semibold">{detail.candidate?.registryName || 'Not recorded'}</span>
                            </div>
                            <div className="flex justify-between border-b border-[var(--color-border)] pb-1">
                              <span className="text-[var(--color-text-muted)]">Registration No:</span>
                              <span className="font-semibold font-metric">{detail.candidate?.registrationNumber || 'Not recorded'}</span>
                            </div>
                            <div className="flex justify-between border-b border-[var(--color-border)] pb-1">
                              <span className="text-[var(--color-text-muted)]">Sire:</span>
                              <span className="font-semibold">{detail.candidate?.sireName || 'Not recorded'}</span>
                            </div>
                            <div className="flex justify-between">
                              <span className="text-[var(--color-text-muted)]">Dam:</span>
                              <span className="font-semibold">{detail.candidate?.damName || 'Not recorded'}</span>
                            </div>
                          </dl>
                        </div>

                        <div className="rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] p-3.5 border border-[var(--color-border)]">
                          <h4 className="text-xs font-bold text-[var(--color-text-primary)]">
                            Groom Physical Screening
                          </h4>
                          <dl className="mt-2 space-y-1 text-xs">
                            <div className="flex justify-between">
                              <span className="text-[var(--color-text-muted)]">Outcome:</span>
                              <span className="font-semibold">{detail.groomDecision ? formatLabel(detail.groomDecision) : 'Pending'}</span>
                            </div>
                            <div className="flex justify-between">
                              <span className="text-[var(--color-text-muted)]">Reviewed At:</span>
                              <span className="font-metric">{formatDate(detail.groomReviewedAt, true)}</span>
                            </div>
                          </dl>
                          {detail.groomFeedback && (
                            <p className="mt-2 rounded bg-[var(--color-surface)] p-2 text-xs text-[var(--color-text-secondary)] border border-[var(--color-border)]">
                              {detail.groomFeedback}
                            </p>
                          )}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          )}
          sidebar={examDetailsCard}
          asideHidden={activeTab === 'exam' && canCompleteExam}
        />
      )}

      {/* Discard Changes Confirmation Dialog */}
      <ConfirmDialog
        open={showCloseConfirm}
        title="Discard Unsaved Clinical Changes?"
        description="You have unsaved changes in this examination form. Returning to Admissions now will lose any unrecorded clinical documentation."
        confirmLabel="Discard & Go Back"
        cancelLabel="Keep Editing"
        tone="danger"
        onConfirm={() => {
          setIsDrawerDirty(false);
          setShowCloseConfirm(false);
          setDrawerOpen(false);
          setSelectedId(null);
          setTargetScheduleId(null);
          setDeepLinkedSchedule(null);
          router.replace('/veterinarian/admissions', { scroll: false });
        }}
        onCancel={() => setShowCloseConfirm(false)}
      />
    </QueueShell>
  );
}

/** The list keeps its page header; an open admission brings its own header instead. */
function QueueShell({ showHeader, children }: { showHeader: boolean; children: ReactNode }) {
  if (!showHeader) return <div className="w-full min-w-0 space-y-5">{children}</div>;
  return (
    <AdmissionListLayout title="Veterinary admissions" description="Review initial veterinary examinations for horse admissions.">
      {children}
    </AdmissionListLayout>
  );
}
