import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, TitleComponent, LegendComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

// ── Cosmic chart palette ──
export const CHART_COLORS = {
  blue: '#38bdf8',
  green: '#22c55e',
  amber: '#f59e0b',
  purple: '#818cf8',
  red: '#ef4444',
  text: 'rgba(148,163,184,0.75)',
  axis: 'rgba(56,189,248,0.12)',
  split: 'rgba(56,189,248,0.06)',
};

// ── Shared dark theme defaults ──
export const darkChartBase = {
  textStyle: { color: CHART_COLORS.text },
  legend: { textStyle: { color: CHART_COLORS.text } },
};

export const darkAxis = {
  axisLabel: { color: CHART_COLORS.text },
  axisLine: { lineStyle: { color: CHART_COLORS.axis } },
  splitLine: { lineStyle: { color: CHART_COLORS.split } },
};

const registered = false;

/** Call once at app init to register all echarts modules used across pages. */
export function registerEcharts() {
  if (registered) return;
  echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent,
    TitleComponent, LegendComponent, CanvasRenderer]);
}

/**
 * Build a standard dark-themed line/bar chart option.
 * Pages provide data-specific overrides merged on top.
 */
export function makeLineOption(
  overrides: Record<string, unknown>,
): Record<string, unknown> {
  return {
    ...darkChartBase,
    tooltip: { trigger: 'axis' },
    grid: { top: 40, left: 50, right: 20, bottom: 30 },
    xAxis: darkAxis,
    yAxis: { type: 'value', ...darkAxis },
    ...overrides,
  };
}
