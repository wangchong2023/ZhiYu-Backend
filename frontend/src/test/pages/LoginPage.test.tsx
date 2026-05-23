import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
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

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form with title', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByText('ZhiYu 管理后台')).toBeInTheDocument();
    expect(screen.getByText('请使用管理员账号登录')).toBeInTheDocument();
  });

  it('renders password tab as default', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByText('密码登录')).toBeInTheDocument();
    expect(screen.getByText('短信登录')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('密码')).toBeInTheDocument();
  });

  it('has required validation on username', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByPlaceholderText('用户名')).toBeRequired();
  });

  it('has required validation on password', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByPlaceholderText('密码')).toBeRequired();
  });

  it('renders captcha input on password tab', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByPlaceholderText('验证码')).toBeInTheDocument();
  });

  it('switches to SMS tab', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByPlaceholderText('手机号')).toBeInTheDocument();
  });

  it('does not render third-party login elements', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.queryByText('第三方账号登录')).not.toBeInTheDocument();
    expect(screen.queryByText('微信登录')).not.toBeInTheDocument();
    expect(screen.queryByText('Google 登录')).not.toBeInTheDocument();
    expect(screen.queryByText('Apple 登录')).not.toBeInTheDocument();
    expect(screen.queryByText('通行密钥')).not.toBeInTheDocument();
  });
});
