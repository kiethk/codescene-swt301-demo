import type { HorseFitnessTrendItem } from '../../types';
import { DateLabels, GridLines, LegendItem, Series, TrendChartCard, type GridTick } from './ChartParts';
import type { WorkoutHover } from './ChartTooltips';

const MIN = 0;
const MAX = 75;
const TICKS: GridTick[] = [0, 15, 30, 45, 60, 75].map((value) => ({ value }));

/** Top and average speed per session (km/h). */
export function SpeedChart({
  trend,
  onHover,
}: {
  trend: HorseFitnessTrendItem[];
  onHover: (hover: WorkoutHover | null) => void;
}) {
  return (
    <TrendChartCard
      title="Speed (km/h)"
      legend={
        <>
          <LegendItem color="primary">Maximum speed</LegendItem>
          <LegendItem color="finance">Average speed</LegendItem>
        </>
      }
    >
      <GridLines ticks={TICKS} min={MIN} max={MAX} />
      <DateLabels trend={trend} />
      <Series trend={trend} value={(item) => item.topSpeedKmh} min={MIN} max={MAX} color="primary" onHover={onHover} />
      <Series trend={trend} value={(item) => item.averageSpeedKmh} min={MIN} max={MAX} color="finance" onHover={onHover} />
    </TrendChartCard>
  );
}
