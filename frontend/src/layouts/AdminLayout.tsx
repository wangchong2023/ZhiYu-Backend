import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button } from 'antd';
import {
  DashboardOutlined,
  UserOutlined,
  AuditOutlined,
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
  ];

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/admin/login';
  };

  return (
    <Layout style={{ minHeight: '100vh' }} className="cosmic-bg">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        style={{
          background: 'var(--cosmic-deep)',
          borderRight: '1px solid var(--cosmic-border)',
        }}
      >
        <div style={{
          height: 52,
          margin: '12px 16px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          overflow: 'hidden',
          whiteSpace: 'nowrap',
        }}>
          {collapsed ? (
            <span style={{ color: 'var(--cosmic-cyan)', fontWeight: 800, fontSize: 22, letterSpacing: 2 }}>
              ZY
            </span>
          ) : (
            <span className="cosmic-heading" style={{ fontSize: 18, color: 'var(--cosmic-cyan)' }}>
              {t('app.title')}
            </span>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['/admin/monitor', '/admin/biz']}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent', borderInlineEnd: 'none', padding: '0 8px' }}
        />
      </Sider>
      <Layout>
        <Header style={{
          padding: '0 24px',
          background: 'var(--cosmic-deep)',
          borderBottom: '1px solid var(--cosmic-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          height: 56,
        }}>
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            style={{ color: 'var(--cosmic-text-secondary)', fontSize: 16 }}
          />
          <Button
            icon={<LogoutOutlined />}
            onClick={handleLogout}
            style={{
              color: 'var(--cosmic-text-secondary)',
              borderColor: 'var(--cosmic-border)',
            }}
          >
            {t('app.logout')}
          </Button>
        </Header>
        <Content style={{
          margin: 20,
          padding: 24,
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
