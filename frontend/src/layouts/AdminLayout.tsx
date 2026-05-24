import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, theme } from 'antd';
import {
  DashboardOutlined,
  UserOutlined,
  AuditOutlined,
  SettingOutlined,
  MonitorOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CreditCardOutlined,
  DollarOutlined,
  WalletOutlined,
  TeamOutlined,
  BellOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import SessionTimeoutOverlay from '../components/SessionTimeoutOverlay';

const { Header, Sider, Content } = Layout;

function AdminLayout() {
  const { t } = useTranslation();
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

  const menuItems = [
    { key: '/admin/dashboard', icon: <DashboardOutlined />, label: t('sidebar.dashboard') },
    {
      key: '/admin/monitor',
      icon: <MonitorOutlined />,
      label: t('sidebar.monitoring'),
      children: [
        { key: '/admin/monitor/overview', label: t('monitor.overview') },
        { key: '/admin/monitor/metrics', label: t('monitor.metrics') },
        { key: '/admin/monitor/logs', label: t('monitor.logs') },
        { key: '/admin/monitor/alerts', label: t('monitor.alerts') },
        { key: '/admin/monitor/settings', label: t('monitor.settings') },
      ],
    },
    { key: '/admin/users', icon: <UserOutlined />, label: t('sidebar.userManagement') },
    { key: '/admin/audit', icon: <AuditOutlined />, label: t('sidebar.auditLog') },
    {
      key: '/admin/biz',
      icon: <CreditCardOutlined />,
      label: t('sidebar.business'),
      children: [
        { key: '/admin/subscriptions', icon: <WalletOutlined />, label: t('sidebar.subscriptions') },
        { key: '/admin/payments', icon: <DollarOutlined />, label: t('sidebar.payments') },
        { key: '/admin/refunds', icon: <AuditOutlined />, label: t('sidebar.refunds') },
      ],
    },
    { key: '/admin/admins', icon: <TeamOutlined />, label: t('sidebar.admins') },
    { key: '/admin/notifications', icon: <BellOutlined />, label: t('sidebar.notifications') },
    { key: '/admin/config', icon: <ToolOutlined />, label: t('sidebar.config') },
    { key: '/admin/settings', icon: <SettingOutlined />, label: t('sidebar.settings') },
  ];

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/admin/login';
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed}>
        <div style={{
          height: 48, margin: 16,
          color: '#fff', fontWeight: 700, fontSize: 18,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          overflow: 'hidden', whiteSpace: 'nowrap',
        }}>
          {collapsed ? 'ZY' : t('app.title')}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['/admin/monitor', '/admin/biz']}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{
          padding: '0 24px', background: colorBgContainer,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>
            {t('app.logout')}
          </Button>
        </Header>
        <Content style={{
          margin: 24, padding: 24,
          background: colorBgContainer,
          borderRadius: borderRadiusLG,
          minHeight: 280,
        }}>
          <SessionTimeoutOverlay />
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default AdminLayout;
