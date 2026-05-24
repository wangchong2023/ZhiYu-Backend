import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import NotificationsPage from '../../pages/notifications/NotificationsPage';

const mockListTemplates = vi.fn().mockResolvedValue({
  data: {
    code: 0,
    data: [
      { id: 1, templateKey: 'welcome_email', type: 'EMAIL', subject: '欢迎注册', isActive: true, description: '注册欢迎邮件', body: '欢迎 {{username}}', variablesJson: '{}' },
      { id: 2, templateKey: 'sms_code', type: 'SMS', subject: '验证码', isActive: false, description: '短信验证码', body: '您的验证码：{{code}}', variablesJson: '{}' },
    ],
  },
});
const mockUpdateTemplate = vi.fn().mockResolvedValue({ data: { code: 0 } });

vi.mock('../../api/notificationApi', () => ({
  default: {
    listTemplates: (...args: unknown[]) => mockListTemplates(...args),
    updateTemplate: (...args: unknown[]) => mockUpdateTemplate(...args),
  },
}));

describe('NotificationsPage', () => {
  it('renders page with refresh button', async () => {
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('刷新')).toBeInTheDocument();
    });
  });

  it('renders table column headers', async () => {
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('模板Key').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('类型').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('主题').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('displays template data', async () => {
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('welcome_email')).toBeInTheDocument();
      expect(screen.getByText('sms_code')).toBeInTheDocument();
    });
  });

  it('shows type tags', async () => {
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('EMAIL')).toBeInTheDocument();
      expect(screen.getByText('SMS')).toBeInTheDocument();
    });
  });

  it('shows active status', async () => {
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getAllByText('是').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('否').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('opens drawer when clicking a template row', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('welcome_email')).toBeInTheDocument();
    });

    await user.click(screen.getByText('welcome_email'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('支持 {{variable}} 变量替换')).toBeInTheDocument();
    });
  });

  it('saves template when drawer save button is clicked', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('welcome_email')).toBeInTheDocument();
    });

    await user.click(screen.getByText('welcome_email'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('支持 {{variable}} 变量替换')).toBeInTheDocument();
    });

    const saveBtn = document.querySelector('.ant-drawer-extra .ant-btn-primary') as HTMLElement;
    await user.click(saveBtn);

    await waitFor(() => {
      expect(mockUpdateTemplate).toHaveBeenCalled();
    });
  });

  it('shows error alert when fetch fails', async () => {
    mockListTemplates.mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('加载通知模板失败')).toBeInTheDocument();
    });
  });
});
