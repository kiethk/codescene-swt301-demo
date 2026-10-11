import { xAtTime, yAt } from '../../lib/chartGeometry';
import type { HorseFitnessTrendItem, ReadinessAssessment } from '../../types';
import { DateLabels, GridLines, LegendItem, Series, TrendChartCard, type GridTick } from './ChartParts';
import type { WorkoutHover } from './ChartTooltips';

const MIN = 0;
const MAX = 10;
const TICKS: GridTick[] = [0, 2, 4, 6, 8, 10].map((value) => ({ value }));

export interface ReadinessHover {
  x: number;
  y: number;
  assessment: ReadinessAssessment;
}

/** Session performance rating (1-10) with the periodic readiness assessments as separate diamond markers. */
export function PerformanceChart({
  trend,
  readiness,
  onHoverWorkout,
  onHoverReadiness,
}: {
  trend: HorseFitnessTrendItem[];
  readiness: ReadinessAssessment[];
  onHoverWorkout: (hover: WorkoutHover | null) => void;
  onHoverReadiness: (hover: ReadinessHover | null) => void;
}) {
  const first = new Date(trend[0].date).getTime();
  const last = new Date(trend[trend.length - 1].date).getTime();

  return (
    <TrendChartCard
      title="Performance rating (1–10) and readiness score"
      legend={
        <>
          <LegendItem color="success" shape="dot">Session performance rating</LegendItem>
          <LegendItem color="isolated" shape="diamond">Periodic readiness score</LegendItem>
        </>
      }
    >
      <GridLines ticks={TICKS} min={MIN} max={MAX} />
      <DateLabels trend={trend} />
      <Series trend={trend} value={(item) => item.performanceRating} min={MIN} max={MAX} color="success" strokeWidth={2.5} radius={4.5} onHover={onHoverWorkout} />

      {readiness.map((assessment) => {
        if (assessment.fitnessScore == null) return null;
        const x = xAtTime(new Date(assessment.assessmentDate).getTime(), first, last);
        const y = yAt(Number(assessment.fitnessScore), MIN, MAX);
        if (y === null) return null;
        return (
          <polygon
            key={assessment.id}
            points={`${x},${y - 6} ${x + 6},${y} ${x},${y + 6} ${x - 6},${y}`}
            fill="var(--color-isolated)"
            stroke="var(--color-surface)"
            strokeWidth="1.5"
            className="cursor-pointer transition-transform hover:scale-150"
            onMouseEnter={(event) => {
              const box = event.currentTarget.getBoundingClientRect();
              onHoverReadiness({ x: box.left, y: box.top, assessment });
            }}
            onMouseLeave={() => onHoverReadiness(null)}
          />
        );
      })}
    </TrendChartCard>
  );
}
