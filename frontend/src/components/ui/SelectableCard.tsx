import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface SelectableCardProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  children: ReactNode;
  /** The card whose details are currently shown. */
  selected?: boolean;
  /** Visible but de-emphasised, for items outside the user's own scope. */
  dimmed?: boolean;
  /** Dashed outline for an empty slot. */
  empty?: boolean;
}

/**
 * A card that is picked from a set to show its details elsewhere (a stall on a map, a slot in a grid).
 * It is a real button: keyboard focus, Enter and Space work, and `aria-pressed` reports the selection.
 */
export function SelectableCard({
  children,
  selected = false,
  dimmed = false,
  empty = false,
  className,
  type = 'button',
  ...props
}: SelectableCardProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        'block w-full min-w-0 rounded-[var(--radius-md)] border p-2.5 text-left outline-none',
        'transition-[border-color,box-shadow,opacity,transform] duration-[var(--duration-fast)]',
        'hover:-translate-y-0.5 hover:border-[var(--color-border-strong)] hover:opacity-100 hover:shadow-[var(--shadow-panel)]',
        'focus-visible:ring-2 focus-visible:ring-[var(--color-focus)]',
        empty
          ? 'border-dashed border-[var(--color-border)] bg-[var(--color-surface)]'
          : 'border-[var(--color-border)] bg-[var(--color-surface-subtle)]',
        dimmed && 'opacity-55',
        selected &&
          'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] opacity-100 shadow-[var(--shadow-panel)] ring-1 ring-[var(--color-primary)]',
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
