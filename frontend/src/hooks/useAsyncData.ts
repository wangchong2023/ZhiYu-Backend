import { useState, useCallback, useEffect, useRef } from 'react';
import type { AxiosResponse } from 'axios';
import type { ApiResponse } from '../api/types';
import { unwrap } from '../utils/unwrap';

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

/**
 * Generic hook for async data fetching.
 * Eliminates the repeated loading/error/fetchData boilerplate in every page.
 *
 * @param fetcher - async function returning AxiosResponse<ApiResponse<T>>
 * @param deps - dependency array; fetcher is re-created when these change
 * @param errorMsg - optional fallback error message
 */
export function useAsyncData<T>(
  fetcher: () => Promise<AxiosResponse<ApiResponse<T>>>,
  deps: unknown[],
  errorMsg?: string,
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);

  const refetch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetcher();
      if (mountedRef.current) {
        setData(unwrap(res));
      }
    } catch {
      if (mountedRef.current) {
        setError(errorMsg || '加载失败');
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    mountedRef.current = true;
    refetch();
    return () => { mountedRef.current = false; };
  }, [refetch]);

  return { data, loading, error, refetch };
}
