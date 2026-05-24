import apiClient from './client';
import type { ApiResponse, LoginRequest, LoginResponse, WebAuthnStartResult } from './types';

const OAUTH_BASE = '/api/v1/auth/oauth';
const WEBAUTHN_BASE = '/api/v1/auth/webauthn';

const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<LoginResponse>>('/admin/login', data),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<LoginResponse>>('/auth/refresh', { refreshToken }),

  logout: (refreshToken?: string) =>
    apiClient.post<ApiResponse<void>>('/auth/logout', { refreshToken }),

  webauthnAuthBegin: (username: string) =>
    apiClient.post<ApiResponse<WebAuthnStartResult>>(`${WEBAUTHN_BASE}/authenticate/begin`, { username }),

  webauthnAuthFinish: (challengeId: string, credentialJson: string) =>
    apiClient.post<ApiResponse<LoginResponse>>(`${WEBAUTHN_BASE}/authenticate/finish`, {
      challengeId,
      credentialJson,
    }),

  webauthnRegisterBegin: () =>
    apiClient.post<ApiResponse<WebAuthnStartResult>>(`${WEBAUTHN_BASE}/register/begin`),

  webauthnRegisterFinish: (challengeId: string, credentialJson: string) =>
    apiClient.post<ApiResponse<void>>(`${WEBAUTHN_BASE}/register/finish`, {
      challengeId,
      credentialJson,
    }),
};

const oauthUrls = {
  wechat: `${OAUTH_BASE}/wechat`,
  google: `${OAUTH_BASE}/google`,
  apple: `${OAUTH_BASE}/apple`,
};

export { oauthUrls };
export default authApi;
