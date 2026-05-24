import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within, fireEvent, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
}));

vi.mock('../../api/client', () => ({
  default: { get: mockGet, post: mockPost },
}));

// Mock navigator.credentials for WebAuthn
const mockCredentialsCreate = vi.fn();

vi.mock('../../api/authApi', () => {
  return {
    default: {
      login: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
      webauthnAuthBegin: vi.fn(),
      webauthnAuthFinish: vi.fn(),
      webauthnRegisterBegin: vi.fn().mockResolvedValue({
        data: { code: 0, data: { challengeId: 'ch1', optionsJson: '{"challenge":"test"}' } },
      }),
      webauthnRegisterFinish: vi.fn().mockResolvedValue({ data: { code: 0 } }),
    },
    oauthUrls: { wechat: '/oauth/wechat', google: '/oauth/google', apple: '/oauth/apple' },
  };
});

import MyAccountPage from '../../pages/account/MyAccountPage';

const mockProfile = {
  userId: 1, username: 'testuser', email: 'test@example.com',
  mobile: '13800138000', scope: 'FULL', status: '正常', createdAt: '2024-01-01T00:00:00Z',
};

const mockIdentities = [
  { identityId: 1, provider: 'PASSWORD', openid: 'u1', nickname: 'test', createdAt: '2024-01-01T00:00:00Z', enabled: 1 },
  { identityId: 2, provider: 'WECHAT', openid: 'wx_openid_12345', nickname: '微信用户', createdAt: '2024-02-01T00:00:00Z', enabled: 1 },
  { identityId: 3, provider: 'GOOGLE', openid: 'google_openid_67890', nickname: 'Google用户', createdAt: '2024-03-01T00:00:00Z', enabled: 1 },
];

const mockCredentials = [
  { credentialId: 'cred1', deviceName: 'MacBook Pro', createdAt: '2024-03-01T00:00:00Z', lastUsedTime: '2024-05-01T00:00:00Z' },
  { credentialId: 'cred2', deviceName: 'iPhone 15', createdAt: '2024-04-01T00:00:00Z', lastUsedTime: '2024-05-15T00:00:00Z' },
];

const mockLoginHistory = [
  { id: 1, username: 'testuser', action: 'LOGIN', type: 'PASSWORD', result: 'SUCCESS', ip: '127.0.0.1', device: 'Chrome', location: '-', time: '2024-01-01T12:00:00Z' },
  { id: 2, username: 'testuser', action: 'LOGIN', type: 'WECHAT', result: 'FAILURE', ip: '192.168.1.1', device: 'Safari', location: '-', time: '2024-01-02T14:00:00Z' },
];

/**
 * Ant Design Popconfirm defaults to English locale in tests, so the
 * confirm button text is "OK", not "确定".
 */
function confirmPopconfirm() {
  // Try "OK" first (default antd locale), then "确定"
  const okBtn = document.querySelector('.ant-popconfirm .ant-btn-primary') as HTMLElement;
  if (okBtn) {
    fireEvent.click(okBtn);
  } else {
    throw new Error('Popconfirm OK button not found');
  }
}

describe('MyAccountPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem('accessToken', 'mock-token');

    // Mock navigator.credentials
    Object.defineProperty(globalThis.navigator, 'credentials', {
      value: { create: mockCredentialsCreate },
      writable: true,
      configurable: true,
    });

    // Default mock for all API calls
    mockGet.mockImplementation((url: string) => {
      if (url === '/user/profile') {
        return Promise.resolve({ data: { code: 0, data: mockProfile } });
      }
      if (url === '/user/identities') {
        return Promise.resolve({ data: { code: 0, data: { identities: mockIdentities } } });
      }
      if (url === '/user/webauthn/credentials') {
        return Promise.resolve({ data: { code: 0, data: mockCredentials } });
      }
      if (url === '/user/login-history') {
        return Promise.resolve({ data: { code: 0, data: { records: mockLoginHistory } } });
      }
      return Promise.resolve({ data: { code: 0, data: { records: [], total: 0 } } });
    });
    mockPost.mockResolvedValue({ data: { code: 0 } });
  });

  // ============================================================
  // Profile tab
  // ============================================================

  it('renders profile tab with user info', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('testuser')).toBeInTheDocument(); });
    expect(screen.getByText('test@example.com')).toBeInTheDocument();
    expect(screen.getByText('13800138000')).toBeInTheDocument();
    expect(screen.getByText('FULL')).toBeInTheDocument();
  });

  it('shows loading spinner for profile initially', async () => {
    mockGet.mockImplementation(() => new Promise(() => {}));
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(document.querySelector('.ant-spin')).toBeTruthy(); });
  });

  it('shows empty state when profile fails', async () => {
    mockGet.mockRejectedValue(new Error('Network error'));
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('无法加载个人信息')).toBeInTheDocument(); });
  });

  // ============================================================
  // Tab rendering
  // ============================================================

  it('renders all 4 tabs', () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    expect(screen.getByText('个人信息')).toBeInTheDocument();
    expect(screen.getByText('认证身份')).toBeInTheDocument();
    expect(screen.getByText('通行密钥')).toBeInTheDocument();
    expect(screen.getByText('登录历史')).toBeInTheDocument();
  });

  // ============================================================
  // Identity tab
  // ============================================================

  it('renders identity management tab with provider data', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();
    await waitFor(() => {
      expect(screen.getByText('微信用户')).toBeInTheDocument();
      expect(screen.getByText('Google用户')).toBeInTheDocument();
    });
  });

  it('renders OAuth binding buttons on identity tab', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();

    await waitFor(() => {
      expect(screen.getByText('绑定微信')).toBeInTheDocument();
      expect(screen.getByText('绑定 Google')).toBeInTheDocument();
      expect(screen.getByText('绑定 Apple')).toBeInTheDocument();
    });
  });

  it('mask function hides middle of long identifiers', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();
    await waitFor(() => {
      expect(screen.getByText('wx_o****2345')).toBeInTheDocument();
    });
  });

  it('shows unbind button for non-password providers', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();

    await waitFor(() => {
      const unbindButtons = screen.getAllByText('解绑');
      expect(unbindButtons.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('calls unbind API when unbind is confirmed', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();

    await waitFor(() => {
      expect(screen.getAllByText('解绑').length).toBeGreaterThanOrEqual(2);
    });

    // Click the first unbind button (for WECHAT, identityId=2)
    const unbindButton = screen.getAllByText('解绑')[0];
    fireEvent.click(unbindButton);

    // Popconfirm should appear; confirm by clicking primary button
    await waitFor(() => {
      const popconfirmBtns = document.querySelectorAll('.ant-popconfirm .ant-btn');
      expect(popconfirmBtns.length).toBeGreaterThanOrEqual(1);
    });

    mockPost.mockClear();
    confirmPopconfirm();

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/user/unbind/2');
    });
  });

  it('shows identity table with provider type tags', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();

    await waitFor(() => {
      expect(screen.getByText('微信')).toBeInTheDocument();
      expect(screen.getByText('Google')).toBeInTheDocument();
    });
  });

  it('does not show unbind button for PASSWORD provider', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('认证身份')).toBeInTheDocument(); });
    screen.getByText('认证身份').click();

    await waitFor(() => {
      const passwordRow = screen.getByText('test').closest('tr');
      expect(passwordRow).not.toBeNull();
      if (passwordRow) {
        const unbindInRow = within(passwordRow).queryByText('解绑');
        expect(unbindInRow).toBeNull();
      }
    });
  });

  // ============================================================
  // WebAuthn tab
  // ============================================================

  it('renders WebAuthn tab with credentials', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('通行密钥')).toBeInTheDocument(); });
    screen.getByText('通行密钥').click();
    await waitFor(() => {
      expect(screen.getByText('MacBook Pro')).toBeInTheDocument();
      expect(screen.getByText('iPhone 15')).toBeInTheDocument();
    });
  });

  it('renders register new key button on WebAuthn tab', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('通行密钥')).toBeInTheDocument(); });
    screen.getByText('通行密钥').click();

    await waitFor(() => {
      expect(screen.getByText('注册新密钥')).toBeInTheDocument();
    });
  });

  it('register button exists and is clickable on WebAuthn tab', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('通行密钥')).toBeInTheDocument(); });
    screen.getByText('通行密钥').click();

    await waitFor(() => {
      const registerBtn = screen.getByText('注册新密钥');
      expect(registerBtn).toBeInTheDocument();
      expect(registerBtn.closest('button')).not.toBeNull();
    });
  });

  it('shows delete button for each credential', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('通行密钥')).toBeInTheDocument(); });
    screen.getByText('通行密钥').click();

    await waitFor(() => {
      const deleteButtons = screen.getAllByText('删除');
      expect(deleteButtons.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('calls delete API when credential deletion is confirmed', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('通行密钥')).toBeInTheDocument(); });
    screen.getByText('通行密钥').click();

    await waitFor(() => {
      expect(screen.getAllByText('删除').length).toBeGreaterThanOrEqual(1);
    });

    // Click the first delete button
    const deleteButton = screen.getAllByText('删除')[0];
    fireEvent.click(deleteButton);

    // Popconfirm should appear
    await waitFor(() => {
      const popconfirm = document.querySelector('.ant-popconfirm');
      expect(popconfirm).not.toBeNull();
    });

    mockPost.mockClear();
    confirmPopconfirm();

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/user/webauthn/delete/cred1');
    });
  });

  it('calls register API when confirming registration', async () => {
    mockCredentialsCreate.mockResolvedValue({ id: 'new-cred', type: 'public-key' });

    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('通行密钥')).toBeInTheDocument(); });
    screen.getByText('通行密钥').click();

    await waitFor(() => { expect(screen.getByText('注册新密钥')).toBeInTheDocument(); });
    fireEvent.click(screen.getByText('注册新密钥'));

    // Wait for modal
    await waitFor(() => {
      expect(document.querySelector('.ant-modal')).not.toBeNull();
    });

    // Click the primary button in modal (开始注册)
    const modalEl = document.querySelector('.ant-modal')!;
    const primaryBtn = modalEl.querySelector('.ant-btn-primary') as HTMLElement;
    expect(primaryBtn).not.toBeNull();

    const authApi = await import('../../api/authApi');

    await act(async () => {
      fireEvent.click(primaryBtn);
    });

    await waitFor(() => {
      expect(authApi.default.webauthnRegisterBegin).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(mockCredentialsCreate).toHaveBeenCalledWith({
        publicKey: { challenge: 'test' },
      });
    });

    await waitFor(() => {
      expect(authApi.default.webauthnRegisterFinish).toHaveBeenCalledWith(
        'ch1',
        JSON.stringify({ id: 'new-cred', type: 'public-key' })
      );
    });
  });

  // ============================================================
  // Login history tab
  // ============================================================

  it('renders login history tab with data', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('登录历史')).toBeInTheDocument(); });
    screen.getByText('登录历史').click();

    await waitFor(() => {
      expect(screen.getByText('SUCCESS')).toBeInTheDocument();
      expect(screen.getByText('FAILURE')).toBeInTheDocument();
      expect(screen.getByText('127.0.0.1')).toBeInTheDocument();
    });
  });

  it('renders login history with method type tags', async () => {
    render(<MemoryRouter><MyAccountPage /></MemoryRouter>);
    await waitFor(() => { expect(screen.getByText('登录历史')).toBeInTheDocument(); });
    screen.getByText('登录历史').click();

    await waitFor(() => {
      expect(screen.getByText('密码')).toBeInTheDocument();
      expect(screen.getByText('微信')).toBeInTheDocument();
    });
  });
});
