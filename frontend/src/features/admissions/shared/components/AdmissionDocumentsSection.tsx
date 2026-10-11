'use client';

import { useState } from 'react';
import { Button, IconButton } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { displayError, formatDate } from '@/lib/display';
import { openProtectedFile } from '@/lib/protectedFile';
import { toast } from '@/lib/toast';
import { Panel, SectionTitle } from '@/components/ui/Panel';
import type { AdmissionDocument } from '../../types';
import { AdmissionDocumentPreview } from './AdmissionDocumentPreview';

const documentNames: Record<string, string> = {
  HORSE_PHOTO: 'Horse photo',
  REGISTRATION_DOCUMENT: 'Registration document',
  PEDIGREE_CERTIFICATE: 'Pedigree certificate',
  VACCINATION_RECORD: 'Vaccination record',
  DEWORMING_RECORD: 'Deworming record',
  HEALTH_CERTIFICATE: 'Health certificate',
  PREVIOUS_MEDICAL_RECORD: 'Previous medical record',
  PREVIOUS_INJURY_RECORD: 'Previous injury record',
};

function date(value: string | null) {
  return value ? formatDate(value) : 'Not recorded';
}

export function AdmissionDocumentsSection({ documents, assetUrl }: { documents: AdmissionDocument[]; assetUrl: (url: string) => string }) {
  const [selectedDocument, setSelectedDocument] = useState<AdmissionDocument | null>(null);
  return (
    <>
      <Panel padded>
        <div className="flex items-center justify-between gap-3">
          <SectionTitle>Documents</SectionTitle>
          <span className="text-xs text-[var(--color-text-muted)]">{documents.length} files</span>
        </div>
        {documents.length ? (
          <ul className="mt-4 space-y-2 text-sm">
            {documents.map((document) => (
              <li key={document.id} className="flex items-start gap-2.5 rounded-[var(--radius-md)] border border-[var(--color-border)] p-2.5 transition-colors hover:bg-[var(--color-surface-muted)]">
                <Icon name={document.documentType === 'HORSE_PHOTO' ? 'image' : 'file-text'} className="mt-0.5 shrink-0" />
                <div className="min-w-0 flex-1">
                  <Button
                    variant="link"
                    aria-haspopup="dialog"
                    onClick={() => setSelectedDocument(document)}
                    className="block max-w-full truncate text-left"
                  >
                    {document.originalFileName || documentNames[document.documentType] || document.documentType}
                  </Button>
                  <p className="mt-0.5 truncate text-xs text-[var(--color-text-muted)]">
                    {documentNames[document.documentType] || document.documentType} · Recorded {date(document.recordDate)} · Uploaded {date(document.uploadedAt)}
                  </p>
                </div>
                <IconButton
                  icon="external-link"
                  size="sm"
                  label={`Open ${document.originalFileName || documentNames[document.documentType] || document.documentType} in new tab`}
                  onClick={() => {
                    openProtectedFile(document.fileUrl).catch((error) => toast.error(displayError(error, 'Unable to open the file.')));
                  }}
                  className="shrink-0"
                />
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-4 text-sm italic text-[var(--color-text-muted)]">No documents attached.</p>
        )}
      </Panel>
      {selectedDocument && (
        <AdmissionDocumentPreview
          key={selectedDocument.id}
          document={selectedDocument}
          label={documentNames[selectedDocument.documentType] || selectedDocument.documentType}
          fileUrl={assetUrl(selectedDocument.fileUrl)}
          onClose={() => setSelectedDocument(null)}
        />
      )}
    </>
  );
}
