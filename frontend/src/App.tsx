import { Routes, Route, Navigate } from 'react-router-dom';
import AdminLayout from './layouts/AdminLayout';
import LoginPage from './pages/login/LoginPage';
import { RequireAuth } from './components/RequireAuth';
import DashboardPage from './pages/dashboard/DashboardPage';
import UserListPage from './pages/users/UserListPage';
import AuditLogPage from './pages/audit/AuditLogPage';
import MyAccountPage from './pages/account/MyAccountPage';
import MonitorOverviewPage from './pages/monitor/MonitorOverviewPage';
import MonitorMetricsPage from './pages/monitor/MonitorMetricsPage';
import MonitorLogsPage from './pages/monitor/MonitorLogsPage';
import MonitorAlertsPage from './pages/monitor/MonitorAlertsPage';
import LogLevelSettingsPage from './pages/monitor/LogLevelSettingsPage';
import SubscriptionsPage from './pages/subscriptions/SubscriptionsPage';
import PaymentsPage from './pages/payments/PaymentsPage';
import RefundsPage from './pages/refunds/RefundsPage';
import AdminsPage from './pages/admins/AdminsPage';
import NotificationsPage from './pages/notifications/NotificationsPage';
import ConfigPage from './pages/config/ConfigPage';

function App() {
  return (
    <Routes>
      <Route path="/admin/login" element={<LoginPage />} />
      <Route path="/admin" element={<RequireAuth><AdminLayout /></RequireAuth>}>
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
        <Route path="subscriptions" element={<SubscriptionsPage />} />
        <Route path="payments" element={<PaymentsPage />} />
        <Route path="refunds" element={<RefundsPage />} />
        <Route path="admins" element={<AdminsPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="config" element={<ConfigPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
    </Routes>
  );
}

export default App;
