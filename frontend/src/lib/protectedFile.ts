'use client';

import { useEffect, useState } from 'react';
import { apiGetBlob } from '@/services/api';

/**
 * Stored files are served by API endpoints that check the caller's permission, and an <img> or <a>
 * cannot send the access token. This loads the file with the token and returns a temporary blob URL
 * to display instead. Returns null while loading, when there is no source, and when loading fails.
 */
export function useProtectedFileUrl(source: string | null | undefined): { url: string | null; failed: boolean } {
  const [loaded, setLoaded] = useState<{ source: string; url: string | null } | null>(null);
  const direct = source && /^(blob:|data:)/.test(source) ? source : null;

  useEffect(() => {
    if (!source || direct) return;

    const controller = new AbortController();
    let objectUrl: string | undefined;
    apiGetBlob(source, controller.signal).then((blob) => {
      if (controller.signal.aborted) return;
      objectUrl = URL.createObjectURL(blob);
      setLoaded({ source, url: objectUrl });
    }).catch(() => {
      if (!controller.signal.aborted) setLoaded({ source, url: null });
    });

    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [source, direct]);

  if (direct) return { url: direct, failed: false };
  const current = loaded && loaded.source === source ? loaded : null;
  return { url: current?.url ?? null, failed: current !== null && current.url === null };
}

/**
 * Opens a stored file in a new browser tab. The tab is opened first, inside the click, so the
 * browser does not treat it as a pop-up; the file is then loaded with the access token.
 */
export async function openProtectedFile(source: string): Promise<void> {
  const tab = window.open('', '_blank');
  try {
    const objectUrl = URL.createObjectURL(await apiGetBlob(source));
    if (tab) tab.location.href = objectUrl;
    else window.open(objectUrl, '_blank');
    // Long enough for the new tab to read it; the URL is only valid in this session anyway.
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (error) {
    tab?.close();
    throw error;
  }
}
