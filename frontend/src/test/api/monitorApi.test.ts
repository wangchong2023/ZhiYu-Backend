import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet, post: mockPost },
}));

describe('monitorApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('health calls GET /admin/monitor/health', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: monitorApi } = await import('../../api/monitorApi');
    await monitorApi.health();
    expect(mockGet).toHaveBeenCalledWith('/admin/monitor/health');
  });

  it('metrics calls GET with range param', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: monitorApi } = await import('../../api/monitorApi');
    await monitorApi.metrics('1h');
    expect(mockGet).toHaveBeenCalledWith('/admin/monitor/metrics', { params: { range: '1h' } });
  });

  it('alerts calls GET with filter params', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: monitorApi } = await import('../../api/monitorApi');
    await monitorApi.alerts({ status: 'FIRING', severity: 'P0' });
    expect(mockGet).toHaveBeenCalledWith('/admin/monitor/alerts', {
      params: { status: 'FIRING', severity: 'P0' },
    });
  });

  it('recentAlerts calls GET /admin/monitor/alerts/recent', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: monitorApi } = await import('../../api/monitorApi');
    await monitorApi.recentAlerts();
    expect(mockGet).toHaveBeenCalledWith('/admin/monitor/alerts/recent');
  });

  it('loggers calls GET /admin/monitor/loggers', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: monitorApi } = await import('../../api/monitorApi');
    await monitorApi.loggers();
    expect(mockGet).toHaveBeenCalledWith('/admin/monitor/loggers');
  });

  it('setLoggerLevel calls POST /admin/monitor/loggers/:name', async () => {
    mockPost.mockResolvedValue({ data: { code: 0 } });
    const { default: monitorApi } = await import('../../api/monitorApi');
    await monitorApi.setLoggerLevel('com.zhiyu', 'DEBUG');
    expect(mockPost).toHaveBeenCalledWith('/admin/monitor/loggers/com.zhiyu', { configuredLevel: 'DEBUG' });
  });
});
