import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminLayout from '../../layouts/AdminLayout';
import { mockT } from '../utils/i18n';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT }),
}));

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({ pathname: '/admin/dashboard' }),
  };
});

describe('AdminLayout', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    // Suppress Ant Design matchMedia warnings in jsdom
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  it('renders sidebar with menu items', () => {
    render(
      <MemoryRouter>
        <AdminLayout />
      </MemoryRouter>
    );

    expect(screen.getByText('仪表盘')).toBeInTheDocument();
    expect(screen.getByText('用户管理')).toBeInTheDocument();
    expect(screen.getByText('审计日志')).toBeInTheDocument();
    expect(screen.getByText('退出登录')).toBeInTheDocument();
  });

  it('shows brand name when expanded', () => {
    render(
      <MemoryRouter>
        <AdminLayout />
      </MemoryRouter>
    );

    expect(screen.getByText('ZhiYu 管理后台')).toBeInTheDocument();
  });

  it('removes tokens and redirects on logout', () => {
    localStorage.setItem('accessToken', 'fake-access');
    localStorage.setItem('refreshToken', 'fake-refresh');

    render(
      <MemoryRouter>
        <AdminLayout />
      </MemoryRouter>
    );

    const logoutButton = screen.getByText('退出登录');
    fireEvent.click(logoutButton);

    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });
});
