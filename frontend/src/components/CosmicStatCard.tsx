import type { ReactNode } from 'react';

interface CosmicStatCardProps {
  title: string;
  value: ReactNode;
  color?: string;
  stagger?: number;
  style?: React.CSSProperties;
}

/**
 * Standardized stat card with cosmic glass-panel styling and staggered animation.
 * Replaces the duplicated glass-panel + cosmic-stat-card + cosmic-enter + cosmic-stagger-N pattern.
 */
export function CosmicStatCard({ title, value, color = 'var(--cosmic-cyan)', stagger = 0, style }: CosmicStatCardProps) {
  return (
    <div
      className={`glass-panel cosmic-stat-card cosmic-enter${stagger > 0 ? ` cosmic-stagger-${stagger}` : ''}`}
      style={{ padding: '16px 24px', ...style }}
    >
      <div className="cosmic-label" style={{ marginBottom: 8 }}>{title}</div>
      <div className="cosmic-number" style={{ color, fontSize: 28, fontWeight: 700 }}>{value}</div>
    </div>
  );
}
