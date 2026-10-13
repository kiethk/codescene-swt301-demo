import type { ReactNode } from 'react';

import { DataTable, HorseAvatar, StatusBadge } from '@/components/ui';
import type { DataTableColumn } from '@/components/ui/DataTable';
import type { Horse } from '@/features/stable/types';

/**
 * Shared horse list table.
 * Columns: Horse (avatar + name), Registration number, Breed, Status, and an optional Actions column.
 * Built-in search (name / registration / breed) and pagination via DataTable; no API calls here.
 */
export function HorseTable({
  horses,
  loading,
  error,
  renderAction,
}: {
  horses: Horse[];
  loading?: boolean;
  error?: ReactNode;
  renderAction?: (horse: Horse) => ReactNode;
}) {
  const baseColumns: DataTableColumn<Horse>[] = [
    {
      id: 'horse',
      header: 'Horse',
      sortValue: (h) => h.name,
      render: (h) => (
        <div className="flex items-center gap-2.5">
          <HorseAvatar name={h.name} size={32} rounded="md" />
          <span className="font-medium text-[var(--color-text-primary)]">{h.name}</span>
        </div>
      ),
    },
    {
      id: 'registrationNumber',
      header: 'Registration number',
      sortValue: (h) => h.registrationNumber ?? '',
      render: (h) => (
        <span className="text-[var(--color-text-secondary)]">
          {h.registrationNumber?.trim() || '—'}
        </span>
      ),
    },
    {
      id: 'breed',
      header: 'Breed',
      sortValue: (h) => h.breed ?? '',
      render: (h) => (
        <span className="text-[var(--color-text-secondary)]">
          {h.breed?.trim() || '—'}
        </span>
      ),
    },
    {
      id: 'status',
      header: 'Status',
      render: (h) => <StatusBadge status={h.currentStatus} />,
    },
  ];

  const columns: DataTableColumn<Horse>[] = renderAction
    ? [
        ...baseColumns,
        {
          id: 'actions',
          header: 'Actions',
          align: 'right' as const,
          render: (h) => renderAction(h),
        },
      ]
    : baseColumns;

  return (
    <DataTable<Horse>
      rows={horses}
      columns={columns}
      getRowKey={(h) => h.id}
      loading={loading}
      error={error}
      pageSize={10}
      ariaLabel="Horses"
      emptyTitle="No horses found"
      emptyDescription="No horses match the current search."
      getSearchText={(h) =>
        [h.name, h.registrationNumber, h.breed].filter(Boolean).join(' ')
      }
      searchPlaceholder="Search by name, registration or breed…"
    />
  );
}
