'use client';

import { cn } from '@/lib/cn';
import { openProtectedFile, useProtectedFileUrl } from '@/lib/protectedFile';
import { Skeleton } from './Skeleton';

interface ProtectedImageProps {
  /** API path (or URL) of a stored image. It is loaded with the access token. */
  src: string;
  alt: string;
  className?: string;
  /** Wraps the image in a control that opens it full size in a new tab. */
  openOnClick?: boolean;
}

/** An image stored by the backend. Use this instead of <img> for any file served by a protected endpoint. */
export function ProtectedImage({ src, alt, className, openOnClick = false }: ProtectedImageProps) {
  const { url, failed } = useProtectedFileUrl(src);

  if (failed) {
    return <p className="text-xs italic text-[var(--color-text-muted)]">The image could not be loaded.</p>;
  }
  if (!url) {
    return <Skeleton className={cn('h-40 w-full max-w-sm', className)} />;
  }

  // eslint-disable-next-line @next/next/no-img-element -- blob URLs cannot go through the image optimizer
  const image = <img src={url} alt={alt} className={className} />;
  if (!openOnClick) return image;

  return (
    <button
      type="button"
      onClick={() => { openProtectedFile(src).catch(() => undefined); }}
      aria-label={`Open full size: ${alt}`}
      className="block w-full cursor-zoom-in overflow-hidden rounded-[var(--radius-md)] border border-[var(--color-border)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus)]"
    >
      {image}
    </button>
  );
}
