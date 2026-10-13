'use client';

import { useCallback, useEffect, useState } from 'react';

import { stableApi } from '@/features/stable/services/stableService';
import type { Horse } from '@/features/stable/types';
import { displayError } from '@/lib/display';

type GetHorsesOpts = Parameters<typeof stableApi.getHorses>[0];

/**
 * Fetches horses via stableApi.getHorses.
 * Re-fetches automatically when mine / unassigned / status change.
 * Stale responses from previous fetches or unmounted components are discarded.
 * Backend determines the data scope — no client-side role filtering.
 */
export function useHorses(opts?: GetHorsesOpts) {
  const [horses, setHorses] = useState<Horse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Increment to trigger a manual reload without changing filter deps.
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  // Destructure to primitive deps — avoids fetch loops caused by a new object ref on each render.
  const mine = opts?.mine;
  const unassigned = opts?.unassigned;
  const status = opts?.status;

  useEffect(() => {
    let cancelled = false;

    // Reconstruct opts from stable primitives so the closure is always fresh.
    const currentOpts: GetHorsesOpts = {};
    if (mine !== undefined) currentOpts.mine = mine;
    if (unassigned !== undefined) currentOpts.unassigned = unassigned;
    if (status !== undefined) currentOpts.status = status;

    async function runFetch() {
      setLoading(true);
      setError(null);
      try {
        const list = await stableApi.getHorses(currentOpts);
        if (!cancelled) setHorses(list);
      } catch (cause) {
        if (!cancelled) setError(displayError(cause, 'Unable to load horses.'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    runFetch();

    return () => {
      cancelled = true;
    };
  }, [mine, unassigned, status, reloadKey]);

  return { horses, loading, error, reload };
}
