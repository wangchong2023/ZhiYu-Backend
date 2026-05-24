import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import SessionTimeoutOverlay from '../../components/SessionTimeoutOverlay';
import { mockT } from '../utils/i18n';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const mockRefresh = vi.fn();
vi.mock('../../api/authApi', () => ({
  default: { refresh: (...args: unknown[]) => mockRefresh(...args) },
}));

function setExpiringToken(expiresInSec: number) {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const exp = Math.floor(Date.now() / 1000) + expiresInSec;
  const payload = btoa(JSON.stringify({ sub: '1', username: 'admin', exp }));
  const token = `${header}.${payload}.sig`;
  localStorage.setItem('accessToken', token);
  localStorage.setItem('refreshToken', 'mock-refresh-token');
}

describe('SessionTimeoutOverlay', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function tick(ms = 1100) {
    act(() => {
      vi.advanceTimersByTime(ms);
    });
  }

  it('shows nothing when token has more than 5 minutes remaining', () => {
    setExpiringToken(600);
    const { container } = render(<SessionTimeoutOverlay />);
    tick();
    expect(container.innerHTML).toBe('');
  });

  it('shows expiry modal when token has less than 5 minutes', () => {
    setExpiringToken(240);
    render(<SessionTimeoutOverlay />);
    tick();
    expect(screen.getByText('会话即将过期')).toBeInTheDocument();
    expect(screen.getByText('继续使用')).toBeInTheDocument();
    expect(screen.getByText('退出登录')).toBeInTheDocument();
  });

  it('clears tokens when token expires', () => {
    setExpiringToken(-1);

    const { location } = window;
    delete (window as { location?: Location }).location;
    let hrefSet = '';
    window.location = {
      ...location,
      set href(v: string) { hrefSet = v; },
      get href() { return hrefSet; },
    } as Location;

    render(<SessionTimeoutOverlay />);
    tick();

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(hrefSet).toBe('/admin/login');

    window.location = location;
  });

  it('calls refresh API when extend is clicked', async () => {
    setExpiringToken(240);
    mockRefresh.mockResolvedValue({
      data: { data: { accessToken: 'new-at', refreshToken: 'new-rt', expiresIn: 900 } },
    });

    render(<SessionTimeoutOverlay />);
    tick();

    const extendBtn = screen.getByText('继续使用');
    await act(async () => {
      extendBtn.click();
    });

    expect(mockRefresh).toHaveBeenCalledWith('mock-refresh-token');
  });

  it('clears tokens on explicit logout', () => {
    setExpiringToken(240);

    render(<SessionTimeoutOverlay />);
    tick();

    const logoutBtn = screen.getByText('退出登录');
    act(() => {
      logoutBtn.click();
    });

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });
});
