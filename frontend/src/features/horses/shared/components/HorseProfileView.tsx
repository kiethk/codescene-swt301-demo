'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';

import {
  Button,
  DetailSkeleton,
  EmptyState,
  Notice,
  Panel,
  ScreenLayout,
  SectionTitle,
  StatusBadge,
} from '@/components/ui';
import { horseService } from '@/features/horses/services/horseService';
import { HorseProfileHeader } from '@/features/horses/shared/components/HorseProfileHeader';
import type { HorseSpecialty } from '@/features/horses/types';
import type { Horse } from '@/features/stable/types';
import { displayError, formatDate } from '@/lib/display';

export interface HorseProfileViewProps {
  horseId: number;
  actions?: ReactNode;
}

function HorseProfileContent({ horseId, actions }: HorseProfileViewProps) {
  const [horse, setHorse] = useState<Horse | null>(null);
  const [specialty, setSpecialty] = useState<HorseSpecialty | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [specialtyWarning, setSpecialtyWarning] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => {
    setReloadKey((k) => k + 1);
  }, []);

  useEffect(() => {
    let active = true;

    async function loadData() {
      setLoading(true);
      setError(null);
      setSpecialtyWarning(null);

      try {
        const horsePromise = horseService.getById(horseId);
        const specialtyPromise = horseService.getSpecialty(horseId).catch((err: unknown) => {
          if (active) {
            setSpecialtyWarning(displayError(err, 'Unable to load racing specialty data.'));
          }
          return null;
        });

        const [horseData, specialtyData] = await Promise.all([horsePromise, specialtyPromise]);
        if (active) {
          setHorse(horseData);
          setSpecialty(specialtyData);
        }
      } catch (err: unknown) {
        if (active) {
          setError(displayError(err, 'Unable to load horse profile.'));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadData();

    return () => {
      active = false;
    };
  }, [horseId, reloadKey]);

  if (loading && !horse) {
    return (
      <ScreenLayout variant="detail">
        <Panel padded>
          <DetailSkeleton />
        </Panel>
      </ScreenLayout>
    );
  }

  if (error || !horse) {
    return (
      <ScreenLayout variant="detail">
        <Panel padded>
          <EmptyState
            icon="alert-triangle"
            title="Unable to load horse profile"
            description={error ?? 'Horse profile could not be loaded.'}
            action={
              <div className="flex items-center gap-2">
                <Button size="sm" icon="refresh" onClick={reload}>
                  Retry
                </Button>
                {actions}
              </div>
            }
          />
        </Panel>
      </ScreenLayout>
    );
  }

  return (
    <ScreenLayout variant="detail">
      {specialtyWarning && <Notice tone="warning">{specialtyWarning}</Notice>}

      <HorseProfileHeader
        horse={horse}
        horseId={horseId}
        specialty={specialty}
        actions={actions}
      />

      <Panel padded>
        <SectionTitle>Basic Information</SectionTitle>
        <div className="mt-4 grid grid-cols-1 gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <span className="text-[var(--color-text-secondary)]">Registration Number</span>
            <p className="mt-1 font-medium text-[var(--color-text-primary)]">
              {horse.registrationNumber?.trim() || '—'}
            </p>
          </div>
          <div>
            <span className="text-[var(--color-text-secondary)]">Breed</span>
            <p className="mt-1 font-medium text-[var(--color-text-primary)]">
              {horse.breed?.trim() || '—'}
            </p>
          </div>
          <div>
            <span className="text-[var(--color-text-secondary)]">Date of Birth</span>
            <p className="mt-1 font-medium text-[var(--color-text-primary)]">
              {horse.dateOfBirth ? formatDate(horse.dateOfBirth) : '—'}
            </p>
          </div>
          <div>
            <span className="text-[var(--color-text-secondary)]">Current Status</span>
            <div className="mt-1">
              <StatusBadge status={horse.currentStatus} />
            </div>
          </div>
          <div>
            <span className="text-[var(--color-text-secondary)]">Training Decision</span>
            <p className="mt-1 font-medium text-[var(--color-text-primary)]">
              {horse.trainingDecision || '—'}
            </p>
          </div>
          {horse.trainingDecisionReason && (
            <div>
              <span className="text-[var(--color-text-secondary)]">Block Reason</span>
              <p className="mt-1 font-medium text-[var(--color-text-primary)]">
                {horse.trainingDecisionReason}
              </p>
            </div>
          )}
        </div>
      </Panel>
    </ScreenLayout>
  );
}

export function HorseProfileView({ horseId, actions }: HorseProfileViewProps) {
  return <HorseProfileContent key={horseId} horseId={horseId} actions={actions} />;
}
