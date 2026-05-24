import { useState, useCallback, useEffect, useRef } from 'react';
import type { AxiosResponse } from 'axios';
import type { ApiResponse, PaginatedData } from '../api/types';
import { unwrap } from '../utils/unwrap';

interface PaginatedState<T> {
  data: T[];
  loading: boolean;
  error: string | null;
  total: number;
  page: number;
  size: number;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
  refetch: () => Promise<void>;
  handleTableChange: (pagination: { current?: number; pageSize?: number }) => void;
}

/**
 * Generic hook for paginated table data.
 * Eliminates the repeated page/size/total/loading/error/pagination-onChange boilerplate.
 *
 * @param fetcher - function receiving (page, size) and returning AxiosResponse<ApiResponse<PaginatedData<T>>>
 * @param extraDeps - additional deps that should trigger a re-fetch (filters, etc.)
 * @param errorMsg - optional fallback error message
 */
export function usePaginatedData<T>(
  fetcher: (page: number, size: number) => Promise<AxiosResponse<ApiResponse<PaginatedData<T>>>>,
  extraDeps: unknown[],
  errorMsg?: string,
): PaginatedState<T> {
  const [data, setData] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const mountedRef = useRef(true);

  const refetch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetcher(page, size);
      if (mountedRef.current) {
        const body = unwrap(res);
        setData(body.records || []);
        setTotal(body.total || 0);
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
  }, [page, size, ...extraDeps]);

  useEffect(() => {
    mountedRef.current = true;
    refetch();
    return () => { mountedRef.current = false; };
  }, [refetch]);

  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    if (pagination.current) setPage(pagination.current);
    if (pagination.pageSize) setSize(pagination.pageSize);
  };

  return { data, loading, error, total, page, size, setPage, setSize, refetch, handleTableChange };
}
