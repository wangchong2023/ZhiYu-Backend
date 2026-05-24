import { useState, useMemo, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Typography } from 'antd';
import type { MenuProps } from 'antd';
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
import adminApi from '../api/adminApi';
import type { VersionDto } from '../api/types';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

// Submenu parent keys — clicking these should toggle, not navigate
const SUBMENU_KEYS = new Set(['/admin/monitor', '/admin/biz']);

function AdminLayout() {
  const { t } = useTranslation();
  const [collapsed, setCollapsed] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>(['/admin/monitor', '/admin/biz']);
  const [backendVersion, setBackendVersion] = useState<VersionDto | null>(null);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    adminApi.getVersion().then((res) => {
      if (res.data?.data) setBackendVersion(res.data.data);
    }).catch(() => {});
  }, []);

  const menuItems = useMemo(() => [
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
  ], [t]);

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    // Only navigate for leaf menu items, not submenu parent toggles
    if (!SUBMENU_KEYS.has(key)) {
      navigate(key);
    }
  };

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
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
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
          openKeys={collapsed ? [] : openKeys}
          onOpenChange={setOpenKeys}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ background: 'transparent', borderInlineEnd: 'none', padding: '0 8px', flex: 1, overflow: 'auto' }}
        />
        <div style={{
          padding: '10px 16px',
          borderTop: '1px solid var(--cosmic-border)',
          flexShrink: 0,
          opacity: collapsed ? 0 : 1,
          transition: 'opacity 0.2s',
          pointerEvents: collapsed ? 'none' : 'auto',
        }}>
          <Text style={{ color: 'var(--cosmic-text-muted)', fontSize: 11, display: 'block', lineHeight: '18px' }}>
            FE: v{__APP_VERSION__}-{__GIT_HASH__} / {__BUILD_TIME__.slice(0, 16).replace('T', ' ')}
          </Text>
          {backendVersion && (
            <Text style={{ color: 'var(--cosmic-text-muted)', fontSize: 11, display: 'block', lineHeight: '18px' }}>
              BE: v{backendVersion.version}-{backendVersion.commitId} / {backendVersion.buildTime}
            </Text>
          )}
        </div>
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
          background: 'var(--cosmic-surface)',
          borderRadius: 'var(--cosmic-radius-lg)',
          border: '1px solid var(--cosmic-border)',
        }}>
          <SessionTimeoutOverlay />
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default AdminLayout;
