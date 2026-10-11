'use client';

import { useEffect, useState } from 'react';
import { trainingService } from '../services/trainingService';
import type { TrainingRules } from '../types';

/**
 * Loads the training rules the backend enforces (golden hours, heart-rate limits, limits for a subject).
 * Returns null until they arrive or when they cannot be loaded; screens then simply leave out the hints
 * that depend on them. The backend still validates every request.
 */
export function useTrainingRules(): TrainingRules | null {
  const [rules, setRules] = useState<TrainingRules | null>(null);

  useEffect(() => {
    let active = true;
    trainingService
      .getRules()
      .then((data) => {
        if (active) setRules(data);
      })
      .catch(() => {
        if (active) setRules(null);
      });
    return () => {
      active = false;
    };
  }, []);

  return rules;
}
