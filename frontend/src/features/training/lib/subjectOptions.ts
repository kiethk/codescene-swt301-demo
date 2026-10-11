import type { IntensityLevel, SubjectCategory, SurfaceType } from '../types';

export const CATEGORY_OPTIONS: { value: SubjectCategory; label: string }[] = [
  { value: 'ENDURANCE', label: 'Fitness & endurance' },
  { value: 'SPEED', label: 'Speed & sprint' },
  { value: 'GATE_START', label: 'Starting-gate technique' },
  { value: 'RECOVERY', label: 'Recovery & cool-down' },
];

export const SURFACE_OPTIONS: { value: SurfaceType; label: string }[] = [
  { value: 'TURF', label: 'Turf' },
  { value: 'DIRT', label: 'Dirt' },
  { value: 'SYNTHETIC', label: 'Synthetic' },
];

export const INTENSITY_OPTIONS: { value: IntensityLevel; label: string; tone: 'info' | 'warning' | 'danger' }[] = [
  { value: 'LOW', label: 'Low', tone: 'info' },
  { value: 'MEDIUM', label: 'Medium', tone: 'warning' },
  { value: 'HIGH', label: 'High', tone: 'danger' },
];

function labelOf<T extends string>(options: { value: T; label: string }[], value: T): string {
  return options.find((option) => option.value === value)?.label ?? value;
}

export const categoryLabel = (value: SubjectCategory) => labelOf(CATEGORY_OPTIONS, value);
export const surfaceLabel = (value: SurfaceType) => labelOf(SURFACE_OPTIONS, value);
export const intensityOption = (value: IntensityLevel) =>
  INTENSITY_OPTIONS.find((option) => option.value === value) ?? INTENSITY_OPTIONS[0];
