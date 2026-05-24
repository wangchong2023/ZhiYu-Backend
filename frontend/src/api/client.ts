import axios from 'axios';
import { message } from 'antd';
import i18n from '../i18n';

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const lang = localStorage.getItem('lang') || 'zh-CN';
  config.params = { ...config.params, lang };
  return config;
});

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
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/admin/login';
      return Promise.reject(error);
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
