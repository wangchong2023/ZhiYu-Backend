import { useState, useMemo, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Typography, Space, Avatar, Dropdown } from 'antd';
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
  GlobalOutlined,
  KeyOutlined,
  SkinOutlined,
  CheckOutlined,
  IdcardOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import SessionTimeoutOverlay from '../components/SessionTimeoutOverlay';
import ErrorBoundary from '../components/ErrorBoundary';
import { formatBuildTime } from '../utils/formatTime';
import adminApi from '../api/adminApi';
import type { VersionDto } from '../api/types';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

// Submenu parent keys — clicking these should toggle, not navigate
const SUBMENU_KEYS = new Set(['/admin/monitor', '/admin/biz']);

function AdminLayout() {
  const { t, i18n } = useTranslation();
  const [collapsed, setCollapsed] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>(['/admin/monitor', '/admin/biz']);
  const [backendVersion, setBackendVersion] = useState<VersionDto | null>(null);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const saved = localStorage.getItem('cosmicTheme') || 'deep-blue';
    document.body.setAttribute('data-theme', saved);
  }, []);

  useEffect(() => {
    adminApi.getVersion().then((res) => {
      if (res.data?.data) setBackendVersion(res.data.data);
    }).catch(() => {});
  }, []);

  const toggleLang = () => {
    i18n.changeLanguage(i18n.language.startsWith('zh') ? 'en-US' : 'zh-CN');
  };

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

  const username = localStorage.getItem('username') || 'Admin';
  const currentTheme = localStorage.getItem('cosmicTheme') || 'deep-blue';

  const applyTheme = (theme: string) => {
    localStorage.setItem('cosmicTheme', theme);
    document.body.setAttribute('data-theme', theme);
    // Force re-render by updating state proxy
    setThemeKey((k) => k + 1);
  };

  // Theme key forces menu re-render when theme changes
  const [themeKey, setThemeKey] = useState(0);

  const themeMenuItems: MenuProps['items'] = [
    {
      key: 'theme-deep-blue',
      icon: currentTheme === 'deep-blue' ? <CheckOutlined /> : <span style={{ width: 14, display: 'inline-block' }} />,
      label: t('theme.deepBlue'),
      onClick: () => applyTheme('deep-blue'),
    },
    {
      key: 'theme-night-purple',
      icon: currentTheme === 'night-purple' ? <CheckOutlined /> : <span style={{ width: 14, display: 'inline-block' }} />,
      label: t('theme.nightPurple'),
      onClick: () => applyTheme('night-purple'),
    },
    {
      key: 'theme-aurora-green',
      icon: currentTheme === 'aurora-green' ? <CheckOutlined /> : <span style={{ width: 14, display: 'inline-block' }} />,
      label: t('theme.auroraGreen'),
      onClick: () => applyTheme('aurora-green'),
    },
  ];

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'info',
      label: (
        <div style={{ padding: '4px 0' }}>
          <div style={{ fontWeight: 600, color: 'var(--cosmic-text-primary)', fontSize: 14 }}>{username}</div>
          <div style={{ color: 'var(--cosmic-text-muted)', fontSize: 12 }}>{t('user.roleAdmin')}</div>
        </div>
      ),
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'profile',
      icon: <IdcardOutlined />,
      label: t('user.editProfile'),
      onClick: () => navigate('/admin/account'),
    },
    {
      key: 'password',
      icon: <KeyOutlined />,
      label: t('user.changePassword'),
      onClick: () => navigate('/admin/account?tab=security'),
    },
    { type: 'divider' },
    {
      key: 'theme-label',
      icon: <SkinOutlined />,
      label: t('user.switchTheme'),
      disabled: true,
    },
    ...themeMenuItems,
    { type: 'divider' },
    {
      key: 'lang',
      icon: <GlobalOutlined />,
      label: i18n.language.startsWith('zh') ? 'Switch to English' : '切换到中文',
      onClick: toggleLang,
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: t('app.logout'),
      onClick: handleLogout,
      danger: true,
    },
  ];

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
            FE: v{__APP_VERSION__}-{__GIT_HASH__} / {formatBuildTime(__BUILD_TIME__)}
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
          <Space size="large">
            <Button
              type="text"
              icon={<GlobalOutlined />}
              onClick={toggleLang}
              style={{ color: 'var(--cosmic-text-secondary)', fontSize: 16 }}
            />
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
              <Space align="center" style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 8, transition: 'background 0.2s' }}
                onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(56,189,248,0.06)')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}>
                <Avatar
                  size={32}
                  icon={<UserOutlined />}
                  style={{ backgroundColor: 'var(--cosmic-cyan)', color: 'var(--cosmic-void)', fontWeight: 600 }}
                />
                <span style={{ color: 'var(--cosmic-text-primary)', fontSize: 13, fontWeight: 500, maxWidth: 100, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {username}
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{
          margin: 20,
          padding: 24,
          minHeight: 280,
          background: 'var(--cosmic-surface)',
          borderRadius: 'var(--cosmic-radius-lg)',
          border: '1px solid var(--cosmic-border)',
        }}>
          <ErrorBoundary>
            <SessionTimeoutOverlay />
            <Outlet />
          </ErrorBoundary>
        </Content>
      </Layout>
    </Layout>
  );
}

export default AdminLayout;
