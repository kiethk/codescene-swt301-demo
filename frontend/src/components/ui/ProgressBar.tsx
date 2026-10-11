import { cn } from '@/lib/cn';

type Tone = 'primary' | 'success' | 'info' | 'warning' | 'danger' | 'neutral';

const toneFill: Record<Tone, string> = {
  primary: 'bg-[var(--color-primary)]',
  success: 'bg-[var(--color-success)]',
  info: 'bg-[var(--color-info)]',
  warning: 'bg-[var(--color-warning)]',
  danger: 'bg-[var(--color-danger)]',
  neutral: 'bg-[var(--color-border-strong)]',
};

const sizeHeight = { sm: 'h-1.5', md: 'h-2.5' } as const;

export interface ProgressSegment {
  value: number;
  tone: Tone;
  /** Accessible name and hover text of the segment, e.g. "Excellent: 4". */
  label: string;
}

interface ProgressBarProps {
  /** Current amount. Ignored when `segments` is given. */
  value?: number;
  max?: number;
  tone?: Tone;
  size?: keyof typeof sizeHeight;
  /** Accessible name, e.g. "Course progress". */
  label: string;
  /** Stacked parts that share the bar (for a distribution). Their widths are proportional to `max`. */
  segments?: ProgressSegment[];
  className?: string;
}

/** Horizontal progress or distribution bar. The fill animates with transform only. */
export function ProgressBar({
  value = 0,
  max = 100,
  tone = 'primary',
  size = 'md',
  label,
  segments,
  className,
}: ProgressBarProps) {
  const track = cn(
    'flex w-full overflow-hidden rounded-full bg-[var(--color-surface-muted)]',
    sizeHeight[size],
    className,
  );

  if (segments) {
    return (
      <div role="img" aria-label={label} className={track}>
        {segments
          .filter((segment) => segment.value > 0)
          .map((segment) => (
            <div
              key={segment.label}
              title={segment.label}
              className={toneFill[segment.tone]}
              style={{ width: `${Math.min(100, (segment.value / (max || 1)) * 100)}%` }}
            />
          ))}
      </div>
    );
  }

  const ratio = Math.max(0, Math.min(1, value / (max || 1)));
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={Math.min(value, max)}
      className={track}
    >
      <div
        className={cn(
          'h-full w-full origin-left rounded-full transition-transform duration-[var(--duration-slow)] ease-[var(--ease-out)]',
          toneFill[tone],
        )}
        style={{ transform: `scaleX(${ratio})` }}
      />
    </div>
  );
}
