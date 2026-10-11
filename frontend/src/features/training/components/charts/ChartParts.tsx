import type { ReactNode } from 'react';
import { Panel } from '@/components/ui';
import { cn } from '@/lib/cn';
import {
  CHART_HEIGHT,
  CHART_PADDING,
  CHART_WIDTH,
  PLOT_HEIGHT,
  PLOT_WIDTH,
  labelStride,
  linePath,
  seriesPoints,
  xAt,
  yAt,
} from '../../lib/chartGeometry';
import type { HorseFitnessTrendItem } from '../../types';

export type SeriesColor = 'success' | 'primary' | 'danger' | 'info' | 'warning' | 'finance' | 'isolated';

/** The same token drives the SVG stroke and the legend swatch, so a series and its legend can never disagree. */
const SERIES: Record<SeriesColor, { stroke: string; swatch: string }> = {
  success: { stroke: 'var(--color-success)', swatch: 'bg-[var(--color-success)]' },
  primary: { stroke: 'var(--color-primary)', swatch: 'bg-[var(--color-primary)]' },
  danger: { stroke: 'var(--color-danger)', swatch: 'bg-[var(--color-danger)]' },
  info: { stroke: 'var(--color-info)', swatch: 'bg-[var(--color-info)]' },
  warning: { stroke: 'var(--color-warning)', swatch: 'bg-[var(--color-warning)]' },
  finance: { stroke: 'var(--color-finance)', swatch: 'bg-[var(--color-finance)]' },
  isolated: { stroke: 'var(--color-isolated)', swatch: 'bg-[var(--color-isolated)]' },
};

export function LegendItem({
  color,
  shape = 'bar',
  children,
}: {
  color: SeriesColor;
  shape?: 'bar' | 'dot' | 'diamond';
  children: ReactNode;
}) {
  return (
    <span className="flex items-center gap-1.5 text-xs text-[var(--color-text-secondary)]">
      <span
        aria-hidden="true"
        className={cn(
          SERIES[color].swatch,
          shape === 'bar' && 'h-2 w-4 rounded-sm',
          shape === 'dot' && 'h-2.5 w-2.5 rounded-full',
          shape === 'diamond' && 'h-2.5 w-2.5 rotate-45',
        )}
      />
      {children}
    </span>
  );
}

/** Card with a title, a legend and an SVG drawing area that scrolls sideways when it gets narrow. */
export function TrendChartCard({
  title,
  legend,
  children,
}: {
  title: string;
  legend: ReactNode;
  children: ReactNode;
}) {
  return (
    <Panel padded>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-secondary)]">{title}</span>
        <div className="flex flex-wrap items-center gap-4">{legend}</div>
      </div>
      <div className="scroll-slim w-full overflow-x-auto">
        <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} role="img" aria-label={title} className="max-h-64 w-full min-w-[650px] overflow-visible">
          {children}
        </svg>
      </div>
    </Panel>
  );
}

export interface GridTick {
  value: number;
  /** Colour of a tick that marks a limit the backend enforces; other ticks are neutral. */
  emphasis?: 'danger' | 'warning';
}

export function GridLines({ ticks, min, max }: { ticks: GridTick[]; min: number; max: number }) {
  return (
    <>
      {ticks.map(({ value, emphasis }) => {
        const y = yAt(value, min, max) ?? 0;
        const color = emphasis ? `var(--color-${emphasis})` : 'var(--color-border)';
        return (
          <g key={value}>
            <line
              x1={CHART_PADDING.left}
              y1={y}
              x2={CHART_PADDING.left + PLOT_WIDTH}
              y2={y}
              stroke={color}
              strokeWidth={emphasis ? 1.5 : 1}
              strokeDasharray={emphasis ? '4 4' : '3 3'}
            />
            <text
              x={CHART_PADDING.left - 8}
              y={y + 3}
              textAnchor="end"
              fontSize="12"
              fill={emphasis ? color : 'var(--color-text-muted)'}
              fontWeight={emphasis ? 600 : 400}
            >
              {value}
            </text>
          </g>
        );
      })}
    </>
  );
}

export function DateLabels({ trend }: { trend: HorseFitnessTrendItem[] }) {
  const stride = labelStride(trend.length);
  return (
    <>
      {trend.map((item, index) =>
        index % stride !== 0 ? null : (
          <text
            key={item.workoutId}
            x={xAt(index, trend.length)}
            y={CHART_PADDING.top + PLOT_HEIGHT + 18}
            textAnchor="middle"
            fontSize="12"
            fill="var(--color-text-secondary)"
          >
            {item.date.slice(5)}
          </text>
        ),
      )}
    </>
  );
}

/** One metric over time: a line and a hoverable dot per session that has a value. */
export function Series({
  trend,
  value,
  min,
  max,
  color,
  strokeWidth = 2,
  radius = 3.5,
  onHover,
}: {
  trend: HorseFitnessTrendItem[];
  value: (item: HorseFitnessTrendItem) => number | null | undefined;
  min: number;
  max: number;
  color: SeriesColor;
  strokeWidth?: number;
  radius?: number;
  onHover: (hover: { x: number; y: number; item: HorseFitnessTrendItem } | null) => void;
}) {
  const stroke = SERIES[color].stroke;
  return (
    <>
      <path
        d={linePath(seriesPoints(trend.map(value), min, max))}
        fill="none"
        stroke={stroke}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {trend.map((item, index) => {
        const y = yAt(value(item), min, max);
        if (y === null) return null;
        return (
          <circle
            key={item.workoutId}
            cx={xAt(index, trend.length)}
            cy={y}
            r={radius}
            fill={stroke}
            stroke="var(--color-surface)"
            strokeWidth="1.5"
            className="cursor-pointer transition-transform hover:scale-150"
            onMouseEnter={(event) => {
              const box = event.currentTarget.getBoundingClientRect();
              onHover({ x: box.left, y: box.top, item });
            }}
            onMouseLeave={() => onHover(null)}
          />
        );
      })}
    </>
  );
}
