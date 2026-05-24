import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Radio, Row, Col } from 'antd';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import monitorApi from '../../api/monitorApi';
import type { MetricsDto, MetricPoint } from '../../api/types';
import { useAsyncData } from '../../hooks/useAsyncData';
import { PageLoader } from '../../components/PageLoader';
import { registerEcharts, CHART_COLORS, darkAxis } from '../../utils/chartTheme';

registerEcharts();

function makeLineOption(data: MetricPoint[], title: string, color: string) {
  if (!data || data.length === 0) return null;
  return {
    textStyle: { color: CHART_COLORS.text },
    tooltip: { trigger: 'axis' },
    title: { text: title, left: 'center', top: 4, textStyle: { fontSize: 13, color: CHART_COLORS.text } },
    grid: { top: 40, left: 50, right: 20, bottom: 30 },
    xAxis: {
      type: 'category', data: data.map((d) => new Date(d.timestamp * 1000).toLocaleTimeString()),
      ...darkAxis,
    },
    yAxis: { type: 'value', ...darkAxis },
    series: [{
      type: 'line', data: data.map((d) => d.value), smooth: true, showSymbol: false,
      itemStyle: { color }, lineStyle: { width: 2 }, areaStyle: { color: color + '18' },
    }],
  };
}

function MonitorMetricsPage() {
  const { t } = useTranslation();
  const [range, setRange] = useState('24h');

  const RANGES = [
    { label: t('metrics.last1h'), value: '1h' },
    { label: t('metrics.last6h'), value: '6h' },
    { label: t('metrics.last24h'), value: '24h' },
    { label: t('metrics.last7d'), value: '7d' },
  ];

  const { data: metrics, loading, error, refetch } = useAsyncData<MetricsDto>(
    () => monitorApi.metrics(range),
    [range],
    t('metrics.loadFailed'),
  );

  const emptyBox = (msg: string) => (
    <div style={{ height: 280, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--cosmic-text-muted)', fontSize: 13 }}>{msg}</div>
  );

  return (
    <PageLoader loading={loading} error={error} onRetry={refetch}>
      <div style={{ marginBottom: 16 }}>
        <Radio.Group value={range} onChange={(e) => setRange(e.target.value)}
          optionType="button" buttonStyle="solid" options={RANGES} />
      </div>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <div className="glass-panel" style={{ padding: 20 }}>
            {metrics?.qps && metrics.qps.length > 0
              ? <ReactEChartsCore echarts={echarts} option={makeLineOption(metrics.qps, t('metrics.qps'), CHART_COLORS.blue)!} style={{ height: 280 }} />
              : emptyBox(t('metrics.noQpsData'))}
          </div>
        </Col>
        <Col span={24}>
          <div className="glass-panel" style={{ padding: 20 }}>
            {metrics?.latencyP50 && metrics.latencyP50.length > 0 ? (
              <ReactEChartsCore echarts={echarts} option={{
                ...makeLineOption(metrics.latencyP50, t('metrics.latency'), CHART_COLORS.green)!,
                series: [
                  { type: 'line', data: metrics.latencyP50.map((d) => d.value), smooth: true, showSymbol: false, name: t('metrics.p50'), itemStyle: { color: CHART_COLORS.green }, lineStyle: { width: 2 } },
                  { type: 'line', data: metrics.latencyP95.map((d) => d.value), smooth: true, showSymbol: false, name: t('metrics.p95'), itemStyle: { color: CHART_COLORS.amber }, lineStyle: { width: 2 } },
                  { type: 'line', data: metrics.latencyP99.map((d) => d.value), smooth: true, showSymbol: false, name: t('metrics.p99'), itemStyle: { color: CHART_COLORS.red }, lineStyle: { width: 2 } },
                ],
              }} style={{ height: 280 }} />
            ) : emptyBox(t('metrics.noLatencyData'))}
          </div>
        </Col>
        <Col span={24}>
          <div className="glass-panel" style={{ padding: 20 }}>
            {metrics?.errorRate && metrics.errorRate.length > 0
              ? <ReactEChartsCore echarts={echarts} option={makeLineOption(metrics.errorRate, t('metrics.errorRate'), CHART_COLORS.red)!} style={{ height: 280 }} />
              : emptyBox(t('metrics.noErrorRateData'))}
          </div>
        </Col>
      </Row>
    </PageLoader>
  );
}

export default MonitorMetricsPage;
