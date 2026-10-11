'use client';

import { useCallback, useEffect, useState } from 'react';
import { stableApi } from '@/features/stable/services/stableService';
import type { Horse } from '@/features/stable/types';
import { displayError } from '@/lib/display';

/** The horses in the areas the signed-in trainer is responsible for, sorted by name. */
export function useTrainerHorses() {
  const [horses, setHorses] = useState<Horse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await stableApi.getHorses({ mine: true });
      setHorses([...list].sort((a, b) => a.name.localeCompare(b.name, 'en')));
    } catch (cause) {
      setError(displayError(cause, 'Unable to load your horses.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  return { horses, loading, error, reload: load };
}
