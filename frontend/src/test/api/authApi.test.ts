import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('../../api/client', () => ({
  default: { get: mockGet, post: mockPost },
}));

describe('authApi', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('login POSTs to /admin/login', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.login({ username: 'admin', password: 'test' });
    expect(mockPost).toHaveBeenCalledWith('/admin/login', { username: 'admin', password: 'test' });
  });

  it('refresh POSTs to /auth/refresh', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.refresh('rt');
    expect(mockPost).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'rt' });
  });

  it('logout POSTs to /auth/logout', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.logout('rt');
    expect(mockPost).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'rt' });
  });

  it('webauthn auth begin POSTs to /webauthn/authenticate/begin', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.webauthnAuthBegin('user1');
    expect(mockPost).toHaveBeenCalled();
  });

  it('webauthn auth finish POSTs to /webauthn/authenticate/finish', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.webauthnAuthFinish('challenge-1', 'cred-json');
    expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/webauthn/authenticate/finish', {
      challengeId: 'challenge-1',
      credentialJson: 'cred-json',
    });
  });

  it('webauthn register begin POSTs to /webauthn/register/begin', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.webauthnRegisterBegin();
    expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/webauthn/register/begin');
  });

  it('webauthn register finish POSTs to /webauthn/register/finish', async () => {
    mockPost.mockResolvedValue({ data: { code: 0, data: {} } });
    const { default: authApi } = await import('../../api/authApi');
    await authApi.webauthnRegisterFinish('challenge-2', 'cred-json-2');
    expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/webauthn/register/finish', {
      challengeId: 'challenge-2',
      credentialJson: 'cred-json-2',
    });
  });

  it('exposes oauthUrls with correct paths', async () => {
    const { oauthUrls } = await import('../../api/authApi');
    expect(oauthUrls.wechat).toBe('/api/v1/auth/oauth/wechat');
    expect(oauthUrls.google).toBe('/api/v1/auth/oauth/google');
    expect(oauthUrls.apple).toBe('/api/v1/auth/oauth/apple');
  });
});
