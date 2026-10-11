/**
 * Geometry shared by the fitness charts: every chart is drawn in the same 800x240 box with the same margins,
 * so the three charts line up and their dates match.
 */
export const CHART_WIDTH = 800;
export const CHART_HEIGHT = 240;
export const CHART_PADDING = { top: 25, right: 30, bottom: 40, left: 45 };
export const PLOT_WIDTH = CHART_WIDTH - CHART_PADDING.left - CHART_PADDING.right;
export const PLOT_HEIGHT = CHART_HEIGHT - CHART_PADDING.top - CHART_PADDING.bottom;

export type Point = [number, number];

/** X position of the index-th of `count` evenly spaced points. */
export function xAt(index: number, count: number): number {
  if (count <= 1) return CHART_PADDING.left + PLOT_WIDTH / 2;
  return CHART_PADDING.left + (index / (count - 1)) * PLOT_WIDTH;
}

/** Y position of a value on a [min, max] axis, or null when there is no value. Values outside are clamped. */
export function yAt(value: number | null | undefined, min: number, max: number): number | null {
  if (value === null || value === undefined) return null;
  const clamped = Math.max(min, Math.min(max, value));
  const ratio = (clamped - min) / (max - min || 1);
  return CHART_PADDING.top + PLOT_HEIGHT - ratio * PLOT_HEIGHT;
}

/** X position of a point in time on the axis that runs from `first` to `last` (both timestamps). */
export function xAtTime(time: number, first: number, last: number): number {
  if (first === last) return CHART_PADDING.left + PLOT_WIDTH / 2;
  const ratio = Math.max(0, Math.min(1, (time - first) / (last - first)));
  return CHART_PADDING.left + ratio * PLOT_WIDTH;
}

/** The points of one series; sessions without a value are skipped. */
export function seriesPoints(values: Array<number | null | undefined>, min: number, max: number): Point[] {
  const points: Point[] = [];
  values.forEach((value, index) => {
    const y = yAt(value, min, max);
    if (y !== null) points.push([xAt(index, values.length), y]);
  });
  return points;
}

/** SVG path through the points. A single point becomes a tiny segment so the round cap still draws a dot. */
export function linePath(points: Point[]): string {
  if (points.length === 0) return '';
  if (points.length === 1) return `M ${points[0][0]} ${points[0][1]} L ${points[0][0] + 0.1} ${points[0][1]}`;
  return points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x} ${y}`).join(' ');
}

/** With many sessions only every n-th date label is drawn so they do not overlap. */
export function labelStride(count: number): number {
  return count > 12 ? Math.ceil(count / 10) : 1;
}
