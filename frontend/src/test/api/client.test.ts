import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { message } from 'antd';

vi.mock('axios');
vi.mock('antd', () => ({ message: { error: vi.fn() } }));

const mockedAxios = axios as unknown as {
  create: vi.fn;
};

describe('apiClient', () => {
  const mockGet = vi.fn();
  const mockPost = vi.fn();
  const mockInstance = {
    get: mockGet,
    post: mockPost,
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockedAxios.create.mockReturnValue(mockInstance);
    vi.resetModules();
  });

  it('creates axios instance with base config', async () => {
    mockedAxios.create.mockReturnValue(mockInstance);
    await import('../../api/client');
    expect(mockedAxios.create).toHaveBeenCalledWith(
      expect.objectContaining({ baseURL: '/api/v1', timeout: 15000 }),
    );
  });

  it('adds Authorization header when token exists', async () => {
    const store: Record<string, string> = { accessToken: 'test-token' };
    vi.stubGlobal('localStorage', { getItem: (k: string) => store[k] ?? null });

    let capturedInterceptor: ((config: unknown) => unknown) | null = null;
    const mockUse = vi.fn((successFn) => { capturedInterceptor = successFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: mockUse }, response: { use: vi.fn() } },
    });

    await import('../../api/client');
    const config = { headers: {} } as Record<string, unknown>;
    capturedInterceptor!(config);
    expect((config.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
  });

  it('adds lang parameter when lang exists in localStorage', async () => {
    const store: Record<string, string> = { accessToken: 'token', lang: 'zh-CN' };
    vi.stubGlobal('localStorage', { getItem: (k: string) => store[k] ?? null });

    let capturedInterceptor: ((config: unknown) => unknown) | null = null;
    const mockUse = vi.fn((successFn) => { capturedInterceptor = successFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: mockUse }, response: { use: vi.fn() } },
    });

    await import('../../api/client');
    const config = { headers: {}, params: { page: 1 } };
    capturedInterceptor!(config);
    expect((config as Record<string, unknown>).params).toEqual({ page: 1, lang: 'zh-CN' });
  });

  it('response success interceptor returns response when code is 0', async () => {
    let capturedSuccessFn: ((response: unknown) => unknown) | null = null;
    const mockRespUse = vi.fn((successFn, _errorFn) => { capturedSuccessFn = successFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: vi.fn() }, response: { use: mockRespUse } },
    });

    await import('../../api/client');
    const response = { data: { code: 0, message: 'success', data: {} } };
    const result = capturedSuccessFn!(response);
    expect(result).toBe(response);
  });

  it('response success interceptor rejects when code is non-zero', async () => {
    let capturedSuccessFn: ((response: unknown) => unknown) | null = null;
    const mockRespUse = vi.fn((successFn, _errorFn) => { capturedSuccessFn = successFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: vi.fn() }, response: { use: mockRespUse } },
    });

    await import('../../api/client');
    const response = { data: { code: 500, message: 'Server error', data: null } };
    await expect(capturedSuccessFn!(response)).rejects.toThrow('Server error');
    expect(message.error).toHaveBeenCalledWith('Server error');
  });

  it('response success interceptor rejects with fallback message when no message given', async () => {
    let capturedSuccessFn: ((response: unknown) => unknown) | null = null;
    const mockRespUse = vi.fn((successFn, _errorFn) => { capturedSuccessFn = successFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: vi.fn() }, response: { use: mockRespUse } },
    });

    await import('../../api/client');
    const response = { data: { code: 500 } };
    await expect(capturedSuccessFn!(response)).rejects.toThrow('请求失败');
    expect(message.error).toHaveBeenCalledWith('请求失败');
  });

  it('handles 401 by clearing tokens and redirecting', async () => {
    const store: Record<string, string> = {};
    const removeItem = vi.fn((k: string) => { delete store[k]; });

    let capturedErrorFn: ((error: unknown) => unknown) | null = null;
    const mockRespUse = vi.fn((_s, errorFn) => { capturedErrorFn = errorFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: vi.fn() }, response: { use: mockRespUse } },
    });

    await import('../../api/client');
    const mockLocation = { href: '' };
    vi.stubGlobal('localStorage', { getItem: vi.fn(), removeItem });
    vi.stubGlobal('window', { ...window, location: mockLocation });

    await expect(
      capturedErrorFn!({ response: { status: 401 }, message: 'Unauthorized' }),
    ).rejects.toBeDefined();

    expect(removeItem).toHaveBeenCalledWith('accessToken');
    expect(removeItem).toHaveBeenCalledWith('refreshToken');
    expect(message.error).toHaveBeenCalledWith('Unauthorized');
  });

  it('response error interceptor handles non-401 errors', async () => {
    let capturedErrorFn: ((error: unknown) => unknown) | null = null;
    const mockRespUse = vi.fn((_successFn, errorFn) => { capturedErrorFn = errorFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: vi.fn() }, response: { use: mockRespUse } },
    });

    await import('../../api/client');

    await expect(
      capturedErrorFn!({ message: 'Network Error' }),
    ).rejects.toBeDefined();
    expect(message.error).toHaveBeenCalledWith('Network Error');
  });

  it('response error interceptor uses fallback message for empty error message', async () => {
    let capturedErrorFn: ((error: unknown) => unknown) | null = null;
    const mockRespUse = vi.fn((_successFn, errorFn) => { capturedErrorFn = errorFn; });
    mockedAxios.create.mockReturnValue({
      ...mockInstance,
      interceptors: { request: { use: vi.fn() }, response: { use: mockRespUse } },
    });

    await import('../../api/client');

    await expect(
      capturedErrorFn!({}),
    ).rejects.toBeDefined();
    expect(message.error).toHaveBeenCalledWith('网络错误');
  });
});
