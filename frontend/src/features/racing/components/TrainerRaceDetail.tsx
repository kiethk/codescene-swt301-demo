'use client';

import { useEffect, useState } from 'react';
import { DetailSkeleton, EmptyState, Icon, LinkButton, Notice, PageHeader, Panel, ScreenLayout } from '@/components/ui';
import { HorseSpecialtyBadges } from '@/features/horses/shared/components/HorseSpecialtyBadges';
import { horseService } from '@/features/horses/services/horseService';
import type { HorseSpecialty } from '@/features/horses/types';
import { displayError, formatDate, formatDateTime, formatNumber, formatTime } from '@/lib/display';
import { racingService } from '../services/racingService';
import { RaceField, RaceNote, RaceSection } from '../shared/components/RaceDetailParts';
import { RaceStatusPill } from '../shared/components/RaceStatusPill';
import type { RaceRegistrationResponse } from '../types';

export function TrainerRaceDetail({ id }: { id: number }) {
  const [data, setData] = useState<RaceRegistrationResponse | null>(null);
  const [specialty, setSpecialty] = useState<HorseSpecialty | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function fetchDetail() {
      setLoading(true);
      setError(null);
      try {
        const nomination = await racingService.getMine(id);
        if (!active) return;
        setData(nomination);
        // The specialty is extra context; the nomination is still readable without it.
        horseService.getSpecialty(nomination.horseId).then((s) => active && setSpecialty(s)).catch(() => undefined);
      } catch (cause) {
        if (active) setError(displayError(cause, 'Nomination not found or you do not have permission to view it.'));
      } finally {
        if (active) setLoading(false);
      }
    }
    fetchDetail();
    return () => {
      active = false;
    };
  }, [id]);

  if (loading) return <Panel padded><DetailSkeleton /></Panel>;

  if (error || !data) {
    return (
      <Panel padded>
        <EmptyState
          icon="alert-triangle"
          title="Unable to display nomination"
          description={error ?? 'This nomination does not exist or has been removed.'}
          action={<LinkButton href="/trainer/racing" size="sm" icon="arrow-left">Back to list</LinkButton>}
        />
      </Panel>
    );
  }

  return (
    <ScreenLayout variant="detail">
      <PageHeader
        title={data.raceName}
        description={<>Category: <strong>{data.raceCategory}</strong> · Submitted {formatDateTime(data.createdAt)}</>}
        breadcrumbs={[{ label: 'Race nominations', href: '/trainer/racing' }, { label: data.raceName }]}
        actions={
          <>
            <RaceStatusPill status={data.status} size="md" />
            <LinkButton href="/trainer/racing" icon="arrow-left">Back to list</LinkButton>
          </>
        }
      />

      <Notice tone="info">
        <strong>Note:</strong> The trainer provided the race information. Management will verify it before approval.
      </Notice>

      {data.status !== 'PENDING' && (
        <Notice
          tone={data.status === 'APPROVED' ? 'success' : 'error'}
          title={`Management feedback (${data.status === 'APPROVED' ? 'approved' : 'nomination rejected'})`}
        >
          {data.reviewedAt && <p className="mb-1 text-xs opacity-80">Reviewed {formatDateTime(data.reviewedAt)}</p>}
          <p className="text-[var(--color-text-primary)]">{data.managerFeedback || 'No additional comments.'}</p>
        </Notice>
      )}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <div className="space-y-5">
          <RaceSection title="Nominated horse">
            <RaceField label="Horse">
              <span className="text-base">{data.horseName || `Horse #${data.horseId}`}</span>
            </RaceField>
            {data.horseRegistrationNumber && (
              <RaceField label="UELN / registration number">
                <span className="font-metric">{data.horseRegistrationNumber}</span>
              </RaceField>
            )}
            {specialty && (
              <RaceField label="Racing specialty"><HorseSpecialtyBadges specialty={specialty} /></RaceField>
            )}
            <RaceNote label="Trainer assessment / nomination reason">{data.selectionReason}</RaceNote>
          </RaceSection>

          <RaceSection title="Technical details and notes">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <RaceField label="Distance">{data.distanceMeters != null && `${formatNumber(data.distanceMeters)} m`}</RaceField>
              <RaceField label="Track surface">{data.trackType}</RaceField>
            </div>
            {data.prizeDetails && <RaceNote label="Prize structure">{data.prizeDetails}</RaceNote>}
            {data.trainerNotes && <RaceNote label="Notes, entry requirements and logistics">{data.trainerNotes}</RaceNote>}
          </RaceSection>
        </div>

        <RaceSection title="Race and event details">
          <RaceField label="Event">{data.raceName}</RaceField>
          <RaceField label="Category / stage">{data.raceCategory}</RaceField>
          <RaceField label="Racecourse and location">{data.location}</RaceField>
          <div className="grid grid-cols-1 gap-4 border-t border-[var(--color-border)] pt-3 sm:grid-cols-2">
            <RaceField label="Event date">{formatDate(data.eventDate)}</RaceField>
            <RaceField label="Start time">{data.eventTime ? formatTime(data.eventTime) : 'Not announced'}</RaceField>
          </div>
          <RaceField label="Organizer">{data.organizer}</RaceField>
          <RaceField label="Official nomination deadline">
            {data.nominationDeadline && formatDate(data.nominationDeadline)}
          </RaceField>
          {data.sourceUrl && (
            <RaceField label="Rules / official URL">
              <a
                href={data.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 break-all text-xs text-[var(--color-primary)] hover:underline"
              >
                {data.sourceUrl}
                <Icon name="external-link" size={12} />
              </a>
            </RaceField>
          )}
        </RaceSection>
      </div>
    </ScreenLayout>
  );
}
