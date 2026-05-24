import { Spin, Alert, Button } from 'antd';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface PageLoaderProps {
  loading: boolean;
  error?: string | null;
  onRetry?: () => void;
  /** "fullscreen": centered Spin with no surrounding content. "inline": render children, show error above. */
  variant?: 'fullscreen' | 'inline';
  children?: ReactNode;
}

/**
 * Standardized loading/error wrapper for page content.
 * - variant="fullscreen": shows centered Spin or error Alert, nothing else (replaces early-return pattern)
 * - variant="inline": renders children inside a container, shows error Alert above if present, passes loading to children
 */
export function PageLoader({ loading, error, onRetry, variant = 'fullscreen', children }: PageLoaderProps) {
  const { t } = useTranslation();

  if (variant === 'fullscreen') {
    if (loading) {
      return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
    }
    if (error) {
      return (
        <Alert
          type="error"
          message={error}
          style={{ background: 'var(--cosmic-elevated)' }}
          action={onRetry ? <Button onClick={onRetry}>{t('common.retry')}</Button> : undefined}
        />
      );
    }
    return <>{children}</>;
  }

  return (
    <>
      {error && (
        <Alert
          type="error"
          message={error}
          action={onRetry ? <Button onClick={onRetry}>{t('common.retry')}</Button> : undefined}
          style={{ marginBottom: 16, background: 'var(--cosmic-elevated)' }}
        />
      )}
      {children}
    </>
  );
}
