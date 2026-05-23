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
} from '@ant-design/icons';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '仪表盘' },
  {
    key: '/admin/monitor',
    icon: <MonitorOutlined />,
    label: '运行监控',
    children: [
      { key: '/admin/monitor/overview', label: '概览' },
      { key: '/admin/monitor/metrics', label: 'API 指标' },
      { key: '/admin/monitor/logs', label: '日志检索' },
      { key: '/admin/monitor/alerts', label: '告警面板' },
      { key: '/admin/monitor/settings', label: '日志级别' },
    ],
  },
  { key: '/admin/users', icon: <UserOutlined />, label: '用户管理' },
  { key: '/admin/audit', icon: <AuditOutlined />, label: '审计日志' },
  { key: '/admin/account', icon: <SettingOutlined />, label: '我的账户' },
];

function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

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
          {collapsed ? 'ZY' : 'ZhiYu 管理后台'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['/admin/monitor']}
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
            退出登录
          </Button>
        </Header>
        <Content style={{
          margin: 24, padding: 24,
          background: colorBgContainer,
          borderRadius: borderRadiusLG,
          minHeight: 280,
        }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

export default AdminLayout;
