import axios from 'axios';
import { message } from 'antd';
import i18n from '../i18n';

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// ── Silent refresh state ──
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: Error) => void;
}> = [];

function processQueue(error: Error | null, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error);
    } else {
      resolve(token!);
    }
  });
  failedQueue = [];
}

function redirectToLogin() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  window.location.href = '/admin/login';
}

async function refreshTokens(refreshToken: string): Promise<{ accessToken: string; refreshToken: string }> {
  const response = await axios.post('/api/v1/auth/refresh', { refreshToken });
  const body = response.data;
  if (body && body.code !== undefined && body.code !== 0) {
    throw new Error(body.message || 'Refresh failed');
  }
  return body.data;
}

// ── Request interceptor ──
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const lang = localStorage.getItem('lang') || 'zh-CN';
  config.params = { ...config.params, lang };
  return config;
});

// ── Response interceptor ──
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && body.code !== undefined && body.code !== 0) {
      const reason = body.message || i18n.t('error.unknown');
      message.error(reason);
      return Promise.reject(new Error(reason));
    }
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      const storedRefreshToken = localStorage.getItem('refreshToken');

      if (!storedRefreshToken) {
        redirectToLogin();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(apiClient(originalRequest));
            },
            reject,
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const data = await refreshTokens(storedRefreshToken);
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        processQueue(null, data.accessToken);

        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError as Error, null);
        redirectToLogin();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    const body = error.response?.data;
    const reason =
      (typeof body?.message === 'string' && body.message) ||
      (typeof body?.error === 'string' && body.error) ||
      i18n.t(`error.http.${error.response?.status}`, error.message || i18n.t('error.network'));
    message.error(reason);
    return Promise.reject(error);
  },
);

export default apiClient;
