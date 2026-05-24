import { Navigate, useLocation } from 'react-router-dom';

function getToken(): string | null {
  return localStorage.getItem('accessToken');
}

export function isAuthenticated(): boolean {
  return !!getToken();
}

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation();

  if (!getToken()) {
    return <Navigate to="/admin/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
