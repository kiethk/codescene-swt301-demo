'use client';

import { Toaster as SonnerToaster } from 'sonner';

/** Mounted once in app/providers.tsx. Trigger toasts with `toast` from '@/lib/toast'. */
export function Toaster() {
  return (
    <SonnerToaster
      position="top-right"
      // Below the top bar, so a toast never covers the notification bell.
      offset={{ top: "calc(var(--topbar-height) + 0.75rem)", right: "1.5rem" }}
      closeButton
      duration={5000}
      style={{ zIndex: 'var(--z-toast)' }}
      toastOptions={{
        unstyled: true,
        classNames: {
          toast:
            'flex w-[22rem] items-start gap-2.5 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-3 text-sm text-[var(--color-text-primary)] shadow-[var(--shadow-popover)]',
          title: 'font-medium',
          description: 'mt-0.5 text-xs text-[var(--color-text-secondary)]',
          icon: 'mt-0.5 shrink-0',
          success: '[&_[data-icon]]:text-[var(--color-success)]',
          error: '[&_[data-icon]]:text-[var(--color-danger)]',
          warning: '[&_[data-icon]]:text-[var(--color-warning)]',
          info: '[&_[data-icon]]:text-[var(--color-info)]',
          closeButton:
            'order-last ml-auto shrink-0 rounded-[var(--radius-xs)] p-0.5 text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]',
        },
      }}
    />
  );
}
