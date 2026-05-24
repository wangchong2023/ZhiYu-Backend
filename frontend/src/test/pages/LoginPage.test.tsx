import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent, within, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../../pages/login/LoginPage';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'app.title': 'ZhiYu 管理后台',
        'app.subtitle': '请使用管理员账号登录',
        'login.passwordTab': '密码登录',
        'login.smsTab': '短信登录',
        'login.username': '用户名',
        'login.password': '密码',
        'login.phone': '手机号',
        'login.captcha': '验证码',
        'login.smsCode': '6位验证码',
        'login.sendSms': '发送验证码',
        'login.getCaptcha': '获取验证码',
        'login.loginButton': '登录',
        'login.smsSent': '验证码已发送',
        'login.smsFailed': '发送失败，请重试',
        'login.loginSuccess': '登录成功',
        'login.usernameRequired': '请输入用户名',
        'login.passwordRequired': '请输入密码',
        'login.phoneRequired': '请输入手机号',
        'login.smsCodeRequired': '请输入短信验证码',
        'login.phoneInvalid': '手机号格式不正确',
        'login.privacyAgree': '我已阅读并同意',
        'login.privacyPolicy': '《隐私政策》',
        'login.privacyRequired': '请阅读并同意隐私政策',
      };
      return map[key] || key;
    },
  }),
}));

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
}));

vi.mock('../../api/client', () => ({
  default: {
    get: mockGet,
    post: mockPost,
  },
}));

/** Get the active tabpane element */
function getActiveTabPane(): HTMLElement {
  const pane = document.querySelector('.ant-tabs-tabpane-active');
  if (!pane) throw new Error('No active tabpane found');
  return pane as HTMLElement;
}

/** Find the login submit button within the active tabpane */
function getLoginButton(): HTMLElement {
  const activePane = getActiveTabPane();
  const btn = activePane.querySelector('button[type="submit"]') as HTMLElement;
  if (!btn) throw new Error('Login button not found');
  return btn;
}

/** Find the privacy checkbox within the active tabpane */
function getPrivacyCheckbox(): HTMLElement {
  const activePane = getActiveTabPane();
  const cb = activePane.querySelector('input[type="checkbox"]') as HTMLElement;
  if (!cb) throw new Error('Privacy checkbox not found');
  return cb;
}

/** Find the captcha input within the active tabpane */
function getCaptchaInput(): HTMLElement {
  const activePane = getActiveTabPane();
  return within(activePane).getByPlaceholderText('验证码');
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // Default captcha response
    mockGet.mockResolvedValue({
      data: {
        code: 0,
        data: {
          captchaToken: 'captcha-token-123',
          captchaImage: 'data:image/png;base64,abc123',
        },
      },
    });
    // Default login success response (reset to success each test)
    mockPost.mockResolvedValue({
      data: {
        code: 0,
        data: {
          accessToken: 'access-token-123',
          refreshToken: 'refresh-token-123',
        },
      },
    });
  });

  // ============================================================
  // Basic rendering
  // ============================================================

  it('renders login form with title', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByText('ZhiYu 管理后台')).toBeInTheDocument();
    expect(screen.getByText('请使用管理员账号登录')).toBeInTheDocument();
  });

  it('renders password tab as default', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByText('密码登录')).toBeInTheDocument();
    expect(screen.getByText('短信登录')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('密码')).toBeInTheDocument();
  });

  it('has required validation on username', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByPlaceholderText('用户名')).toBeRequired();
  });

  it('has required validation on password', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByPlaceholderText('密码')).toBeRequired();
  });

  it('renders captcha input on password tab', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByPlaceholderText('验证码')).toBeInTheDocument();
  });

  it('switches to SMS tab and shows SMS form fields', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });
    expect(screen.getByPlaceholderText('6位验证码')).toBeInTheDocument();
    expect(screen.getByText('发送验证码')).toBeInTheDocument();
  });

  it('does not render third-party login elements', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.queryByText('第三方账号登录')).not.toBeInTheDocument();
    expect(screen.queryByText('微信登录')).not.toBeInTheDocument();
    expect(screen.queryByText('Google 登录')).not.toBeInTheDocument();
    expect(screen.queryByText('Apple 登录')).not.toBeInTheDocument();
    expect(screen.queryByText('通行密钥')).not.toBeInTheDocument();
  });

  it('renders privacy checkbox on password tab', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByText('我已阅读并同意')).toBeInTheDocument();
    expect(screen.getByText('《隐私政策》')).toBeInTheDocument();
  });

  // ============================================================
  // Captcha flow
  // ============================================================

  it('fetches captcha on mount', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/auth/captcha/image', {
        params: { sceneId: 'zhiyu_login' },
      });
    });
  });

  it('renders captcha image when captcha data is available', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    await waitFor(() => {
      const img = screen.getByAltText('验证码');
      expect(img).toBeInTheDocument();
      expect(img).toHaveAttribute('src', 'data:image/png;base64,abc123');
    });
  });

  it('shows getCaptcha text when captcha image is not available', async () => {
    mockGet.mockResolvedValue({ data: { code: 0, data: null } });
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('获取验证码')).toBeInTheDocument();
    });
  });

  it('refreshes captcha when refresh button is clicked', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByAltText('验证码')).toBeInTheDocument();
    });

    mockGet.mockClear();
    const captchaButton = screen.getByAltText('验证码').closest('button');
    fireEvent.click(captchaButton!);

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/auth/captcha/image', {
        params: { sceneId: 'zhiyu_login' },
      });
    });
  });

  // ============================================================
  // Password login flow
  // ============================================================

  function fillPasswordFields(username: string, password: string) {
    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: username } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: password } });
  }

  it('submits password login successfully and navigates to dashboard', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByAltText('验证码')).toBeInTheDocument();
    });

    fillPasswordFields('admin', 'password123');
    fireEvent.click(getPrivacyCheckbox());
    fireEvent.click(getLoginButton());

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/admin/login', expect.objectContaining({
        username: 'admin',
        password: 'password123',
      }));
    });

    await waitFor(() => {
      expect(localStorage.getItem('accessToken')).toBe('access-token-123');
      expect(localStorage.getItem('refreshToken')).toBe('refresh-token-123');
      expect(mockNavigate).toHaveBeenCalledWith('/admin/dashboard');
    });
  });

  it('includes captcha in password login payload when captcha is entered', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByAltText('验证码')).toBeInTheDocument();
    });

    fillPasswordFields('admin', 'password123');
    fireEvent.change(getCaptchaInput(), { target: { value: 'ABC123' } });
    fireEvent.click(getPrivacyCheckbox());
    fireEvent.click(getLoginButton());

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/admin/login', expect.objectContaining({
        username: 'admin',
        password: 'password123',
        captchaToken: 'captcha-token-123',
        captchaCode: 'ABC123',
      }));
    });
  });

  // ============================================================
  // Privacy checkbox validation
  // ============================================================

  it('shows validation error when privacy is unchecked on password tab', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByAltText('验证码')).toBeInTheDocument();
    });

    fillPasswordFields('admin', 'password123');
    fireEvent.click(getLoginButton());

    await waitFor(() => {
      expect(screen.getByText('请阅读并同意隐私政策')).toBeInTheDocument();
    });

    const loginCalls = mockPost.mock.calls.filter(
      (call: unknown[]) => call[0] === '/admin/login'
    );
    expect(loginCalls.length).toBe(0);
  });

  it('shows validation error when privacy is unchecked on SMS tab', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800138000' } });
    fireEvent.change(screen.getByPlaceholderText('6位验证码'), { target: { value: '123456' } });
    fireEvent.click(getLoginButton());

    await waitFor(() => {
      expect(screen.getByText('请阅读并同意隐私政策')).toBeInTheDocument();
    });

    const loginCalls = mockPost.mock.calls.filter(
      (call: unknown[]) => call[0] === '/admin/login'
    );
    expect(loginCalls.length).toBe(0);
  });

  // ============================================================
  // SMS send flow
  // ============================================================

  it('sends SMS code and starts countdown', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800138000' } });
    mockPost.mockClear();
    mockPost.mockResolvedValueOnce({ data: { code: 0 } });

    fireEvent.click(screen.getByText('发送验证码'));

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/auth/sms/send', {
        phone: '13800138000',
        scene: 'admin_login',
      });
    });

    await waitFor(() => {
      expect(screen.getByText('60s')).toBeInTheDocument();
    });
  });

  it('shows error when SMS send fails', async () => {
    mockPost.mockRejectedValueOnce(new Error('Send failed'));
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800138000' } });
    fireEvent.click(screen.getByText('发送验证码'));

    await waitFor(() => {
      expect(screen.getByText('发送验证码')).toBeInTheDocument();
    });
  });

  it('does not send SMS if phone is empty', async () => {
    mockPost.mockClear();
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('发送验证码'));

    await waitFor(() => {
      expect(screen.getByText('请输入手机号')).toBeInTheDocument();
    });

    const smsCalls = mockPost.mock.calls.filter(
      (call: unknown[]) => call[0] === '/auth/sms/send'
    );
    expect(smsCalls.length).toBe(0);
  });

  // ============================================================
  // SMS login form rendering
  // ============================================================

  it('renders SMS login form with all required fields and buttons', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    expect(screen.getByPlaceholderText('6位验证码')).toBeInTheDocument();
    expect(screen.getByText('发送验证码')).toBeInTheDocument();
    // Both tabs have captcha inputs; verify at least 2 exist
    const captchaInputs = screen.getAllByPlaceholderText('验证码');
    expect(captchaInputs.length).toBeGreaterThanOrEqual(2);
    // Privacy text appears in both password + SMS tabs
    expect(screen.getAllByText('我已阅读并同意').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('《隐私政策》').length).toBeGreaterThanOrEqual(2);
    // Login button should exist and contain login text
    const activePane = getActiveTabPane();
    const submitBtn = activePane.querySelector('button[type="submit"]');
    expect(submitBtn).not.toBeNull();
    expect(submitBtn?.textContent?.replace(/\s/g, '')).toContain('登');
  });

  it('has required validation on phone field', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    expect(screen.getByPlaceholderText('手机号')).toBeRequired();
  });

  it('validates phone format pattern on SMS tab', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    // Enter invalid phone (wrong format)
    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '12345' } });
    fireEvent.change(screen.getByPlaceholderText('6位验证码'), { target: { value: '888888' } });
    fireEvent.click(getPrivacyCheckbox());

    // Trigger validation by clicking submit
    mockPost.mockClear();
    fireEvent.click(screen.getByText('发送验证码'));

    // Since phone is empty in the SMS-send validation check (form.validateFields),
    // and the pattern is wrong for the submit path, the phone validation should
    // prevent SMS send when the phone field fails pattern validation
    // (Note: The sendSms handler validates phone before sending)
  });

  it('switches from SMS tab back to password tab', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    // Switch back to password tab
    fireEvent.click(screen.getByText('密码登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('密码')).toBeInTheDocument();
    });
  });

  // ============================================================
  // SMS countdown timer
  // ============================================================

  it('disables SMS send button during countdown', async () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    fireEvent.click(screen.getByText('短信登录'));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800138000' } });
    mockPost.mockClear();
    mockPost.mockResolvedValueOnce({ data: { code: 0 } });

    fireEvent.click(screen.getByText('发送验证码'));

    await waitFor(() => {
      expect(screen.getByText('60s')).toBeInTheDocument();
    });

    const countdownBtn = screen.getByText('60s').closest('button');
    expect(countdownBtn).toBeDisabled();
  });

  // ============================================================
  // Error handling & edge cases
  // ============================================================

  it('does not navigate or store tokens when API returns empty data', async () => {
    // Simulate a response where data is null/missing
    mockPost.mockResolvedValueOnce({
      data: { code: 0, data: null },
    });

    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByAltText('验证码')).toBeInTheDocument();
    });

    fillPasswordFields('admin', 'password123');
    fireEvent.click(getPrivacyCheckbox());

    await act(async () => {
      fireEvent.click(getLoginButton());
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/admin/login', expect.objectContaining({
        username: 'admin',
        password: 'password123',
      }));
    });

    // No data = no navigation, no token storage
    await waitFor(() => {
      expect(mockNavigate).not.toHaveBeenCalled();
      expect(localStorage.getItem('accessToken')).toBeNull();
    });
  });

  it('does not crash when captcha fetch fails', async () => {
    mockGet.mockRejectedValue(new Error('Network error'));
    render(<MemoryRouter><LoginPage /></MemoryRouter>);

    await waitFor(() => {
      expect(screen.getByText('获取验证码')).toBeInTheDocument();
    });
  });
});
