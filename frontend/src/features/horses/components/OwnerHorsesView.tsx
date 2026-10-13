'use client';

import { useEffect, useState, useMemo } from 'react';
import Link from 'next/link';
import {
  Button,
  EmptyState,
  FormField,
  HorseAvatar,
  Icon,
  Input,
  PageHeader,
  Panel,
  Pill,
  ScreenLayout,
  Select,
  Skeleton,
  StatusBadge,
} from '@/components/ui';
import { horseService } from '../services/horseService';
import { HorseSpecialtyBadges } from '../shared/components/HorseSpecialtyBadges';
import type { Horse } from '@/features/stable/types';
import type { HorseSpecialty } from '../types';
import { displayError, formatDate } from '@/lib/display';

interface HorseWithSpecialty {
  horse: Horse;
  specialty?: HorseSpecialty | null;
}

export function OwnerHorsesView() {
  const [items, setItems] = useState<HorseWithSpecialty[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const horses = await horseService.listMine();
      // Fetch specialties in parallel
      const withSpecialty = await Promise.all(
        horses.map(async (horse) => {
          try {
            const specialty = await horseService.getSpecialty(horse.id);
            return { horse, specialty };
          } catch {
            return { horse, specialty: null };
          }
        })
      );
      setItems(withSpecialty);
    } catch (err) {
      setError(displayError(err, 'Unable to load your horses.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const filteredItems = useMemo(() => {
    return items.filter(({ horse }) => {
      const matchesSearch =
        search.trim() === '' ||
        horse.name.toLowerCase().includes(search.toLowerCase()) ||
        (horse.breed && horse.breed.toLowerCase().includes(search.toLowerCase()));

      const matchesStatus =
        statusFilter === 'ALL' || horse.currentStatus === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [items, search, statusFilter]);

  return (
    <ScreenLayout variant="detail">
      <PageHeader
        title="My Horses"
        description="Comprehensive dashboard of all horses registered under your ownership."
      />

      {/* Filter and Search Bar */}
      <Panel padded className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
          <FormField label="Search Horse" className="w-full sm:max-w-xs">
            <Input
              placeholder="Search by name or breed..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </FormField>

          <FormField label="Status" className="w-full sm:w-44">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
              <option value="ALL">All Statuses</option>
              <option value="ELIGIBLE">Eligible</option>
              <option value="CANDIDATE">Candidate</option>
              <option value="REJECTED">Rejected</option>
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

      {/* Content Area */}
      {loading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3, 4, 5, 6].map((idx) => (
            <Panel key={idx} padded className="flex flex-col gap-3">
              <div className="flex items-center gap-3">
                <Skeleton className="h-12 w-12 rounded-lg" />
                <div className="space-y-1.5 flex-1">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
              <Skeleton className="h-6 w-full rounded" />
              <Skeleton className="h-10 w-full rounded" />
            </Panel>
          ))}
        </div>
      ) : error ? (
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Failed to load horses"
            description={error}
            action={<Button size="sm" icon="refresh" onClick={loadData}>Try again</Button>}
          />
        </Panel>
      ) : filteredItems.length === 0 ? (
        <Panel padded>
          <EmptyState
            icon="horse"
            title={items.length === 0 ? "You do not have any registered horses" : "No matching horses found"}
            description={
              items.length === 0
                ? "Once horses are accepted into the academy under your ownership, they will appear here."
                : "Try adjusting your search query or status filter."
            }
          />
        </Panel>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredItems.map(({ horse, specialty }) => (
            <Link
              key={horse.id}
              href={`/owner/horses/${horse.id}`}
              className="group block outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus)] rounded-lg"
            >
              <Panel
                padded
                className="flex h-full flex-col justify-between transition-all duration-150 hover:border-[var(--color-primary)] hover:shadow-sm"
              >
                <div>
                  {/* Header info */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <HorseAvatar name={horse.name} size={48} rounded="md" />
                      <div>
                        <h3 className="text-[15px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-primary)] transition-colors">
                          {horse.name}
                        </h3>
                        <p className="text-[12px] text-[var(--color-text-muted)]">
                          {horse.breed || 'Unknown breed'}
                        </p>
                      </div>
                    </div>
                    <StatusBadge status={horse.currentStatus} size="sm" />
                  </div>

                  {/* Specialty Badges (Reused component) */}
                  <div className="mt-3.5 pt-3 border-t border-[var(--color-border-subtle)]">
                    <div className="text-[11px] font-medium text-[var(--color-text-muted)] mb-1.5 flex items-center gap-1">
                      <Icon name="star" size={12} />
                      Racing Specialty:
                    </div>
                    <HorseSpecialtyBadges specialty={specialty} size="sm" />
                  </div>

                  {/* Health or training warning if blocked */}
                  {horse.trainingDecision === 'BLOCKED' && (
                    <div className="mt-2.5">
                      <Pill tone="danger" icon="alert-triangle" size="sm">
                        Training paused: {horse.trainingDecisionReason || 'Veterinary hold'}
                      </Pill>
                    </div>
                  )}

                  {/* Details metadata */}
                  <div className="mt-3 space-y-1.5 text-[12px] text-[var(--color-text-secondary)]">
                    <div className="flex justify-between items-center">
                      <span className="text-[var(--color-text-muted)]">Date of Birth:</span>
                      <span>{formatDate(horse.dateOfBirth)}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-[var(--color-text-muted)]">Stall Location:</span>
                      <span>
                        {horse.currentStallId ? `Stall #${horse.currentStallId}` : 'Unassigned'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Footer action */}
                <div className="mt-4 pt-2.5 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[12px] font-medium text-[var(--color-primary)]">
                  <span>View Details</span>
                  <Icon name="chevron-right" size={14} className="transition-transform group-hover:translate-x-0.5" />
                </div>
              </Panel>
            </Link>
          ))}
        </div>
      )}
    </ScreenLayout>
  );
}
