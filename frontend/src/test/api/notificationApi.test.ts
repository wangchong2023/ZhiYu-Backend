import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPut = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet, put: mockPut },
}));

describe('notificationApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('listTemplates calls GET /admin/notifications', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: [] } });
    const { default: notificationApi } = await import('../../api/notificationApi');
    await notificationApi.listTemplates();
    expect(mockGet).toHaveBeenCalledWith('/admin/notifications');
  });

  it('getTemplate calls GET /admin/notifications/:id', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: { id: 1, templateKey: 'welcome' } } });
    const { default: notificationApi } = await import('../../api/notificationApi');
    await notificationApi.getTemplate(5);
    expect(mockGet).toHaveBeenCalledWith('/admin/notifications/5');
  });

  it('updateTemplate calls PUT /admin/notifications/:id', async () => {
    mockPut.mockResolvedValue({ data: { code: 0 } });
    const { default: notificationApi } = await import('../../api/notificationApi');
    await notificationApi.updateTemplate(3, { subject: 'New Subject', body: 'New Body' });
    expect(mockPut).toHaveBeenCalledWith('/admin/notifications/3', {
      subject: 'New Subject',
      body: 'New Body',
    });
  });
});
