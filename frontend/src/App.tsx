import { Routes, Route, Navigate } from 'react-router-dom';
import AdminLayout from './layouts/AdminLayout';
import LoginPage from './pages/login/LoginPage';
import DashboardPage from './pages/dashboard/DashboardPage';
import UserListPage from './pages/users/UserListPage';
import AuditLogPage from './pages/audit/AuditLogPage';
import MyAccountPage from './pages/account/MyAccountPage';
import MonitorOverviewPage from './pages/monitor/MonitorOverviewPage';
import MonitorMetricsPage from './pages/monitor/MonitorMetricsPage';
import MonitorLogsPage from './pages/monitor/MonitorLogsPage';
import MonitorAlertsPage from './pages/monitor/MonitorAlertsPage';
import LogLevelSettingsPage from './pages/monitor/LogLevelSettingsPage';

function App() {
  return (
    <Routes>
      <Route path="/admin/login" element={<LoginPage />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="/admin/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="monitor">
          <Route index element={<Navigate to="/admin/monitor/overview" replace />} />
          <Route path="overview" element={<MonitorOverviewPage />} />
          <Route path="metrics" element={<MonitorMetricsPage />} />
          <Route path="logs" element={<MonitorLogsPage />} />
          <Route path="alerts" element={<MonitorAlertsPage />} />
          <Route path="settings" element={<LogLevelSettingsPage />} />
        </Route>
        <Route path="users" element={<UserListPage />} />
        <Route path="audit" element={<AuditLogPage />} />
        <Route path="account" element={<MyAccountPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
    </Routes>
  );
}

export default App;
