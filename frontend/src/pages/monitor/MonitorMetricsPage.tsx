import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Radio, Spin, Alert, Button, Row, Col, Card } from 'antd';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, TitleComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import monitorApi from '../../api/monitorApi';
import type { MetricsDto, MetricPoint } from '../../api/types';

echarts.use([LineChart, GridComponent, TooltipComponent, TitleComponent, LegendComponent, CanvasRenderer]);

function makeLineOption(data: MetricPoint[], title: string, color: string) {
  if (!data || data.length === 0) return null;
  return {
    tooltip: { trigger: 'axis' },
    title: { text: title, left: 'center', textStyle: { fontSize: 13 } },
    grid: { top: 40, left: 50, right: 20, bottom: 30 },
    xAxis: { type: 'category', data: data.map((d) => new Date(d.timestamp * 1000).toLocaleTimeString()) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', data: data.map((d) => d.value), smooth: true, showSymbol: false, itemStyle: { color }, areaStyle: { color: color + '20' } }],
  };
}

function MonitorMetricsPage() {
  const { t } = useTranslation();
  const [range, setRange] = useState('24h');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<MetricsDto | null>(null);

  const RANGES = [
    { label: t('metrics.last1h'), value: '1h' },
    { label: t('metrics.last6h'), value: '6h' },
    { label: t('metrics.last24h'), value: '24h' },
    { label: t('metrics.last7d'), value: '7d' },
  ];

  const fetchMetrics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await monitorApi.metrics(range);
      setMetrics(resp.data?.data || null);
    } catch {
      setError(t('metrics.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [range, t]);

  useEffect(() => { fetchMetrics(); }, [fetchMetrics]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchMetrics}>{t('common.retry')}</Button>} />;

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Radio.Group value={range} onChange={(e) => setRange(e.target.value)} optionType="button" buttonStyle="solid" options={RANGES} />
      </div>
      <Row gutter={16}>
        <Col span={24} style={{ marginBottom: 16 }}>
          <Card>
            {metrics?.qps && metrics.qps.length > 0 ? (
              <ReactEChartsCore option={makeLineOption(metrics.qps, t('metrics.qps'), '#1677ff')!} style={{ height: 250 }} />
            ) : <div style={{ height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>{t('metrics.noQpsData')}</div>}
          </Card>
        </Col>
        <Col span={24} style={{ marginBottom: 16 }}>
          <Card>
            {metrics?.latencyP50 && metrics.latencyP50.length > 0 ? (
              <ReactEChartsCore option={{
                ...makeLineOption(metrics.latencyP50, t('metrics.latency'), '#52c41a')!,
                series: [
                  { type: 'line', data: metrics.latencyP50.map((d) => d.value), smooth: true, showSymbol: false, name: t('metrics.p50'), itemStyle: { color: '#52c41a' } },
                  { type: 'line', data: metrics.latencyP95.map((d) => d.value), smooth: true, showSymbol: false, name: t('metrics.p95'), itemStyle: { color: '#faad14' } },
                  { type: 'line', data: metrics.latencyP99.map((d) => d.value), smooth: true, showSymbol: false, name: t('metrics.p99'), itemStyle: { color: '#ff4d4f' } },
                ],
              }} style={{ height: 250 }} />
            ) : <div style={{ height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>{t('metrics.noLatencyData')}</div>}
          </Card>
        </Col>
        <Col span={24}>
          <Card>
            {metrics?.errorRate && metrics.errorRate.length > 0 ? (
              <ReactEChartsCore option={makeLineOption(metrics.errorRate, t('metrics.errorRate'), '#ff4d4f')!} style={{ height: 250 }} />
            ) : <div style={{ height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>{t('metrics.noErrorRateData')}</div>}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default MonitorMetricsPage;
