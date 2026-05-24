import type { AxiosResponse } from 'axios';
import type { ApiResponse } from '../api/types';

/** Extract the typed payload T from an AxiosResponse<ApiResponse<T>>. Default T = any for use with untyped axios calls. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function unwrap<T = any>(res: AxiosResponse<ApiResponse<T>>): T {
  return res.data.data;
}
