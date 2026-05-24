import { useEffect, useState, useCallback } from 'react';
import {
  Table, Input, Select, Button, Space, Tag, Drawer, Descriptions,
  Spin, Alert, Popconfirm, message, TablePaginationConfig,
} from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import apiClient from '../../api/client';
import type { IdentityDto } from '../../api/types';

interface UserDto {
  userId: number;
  username: string;
  email: string;
  mobile: string;
  createdAt: string;
  status: string;
  scope: string;
}

interface UserDetail {
  userId: number;
  username: string;
  email: string;
  mobile: string;
  createdAt: string;
  status: string;
  scope: string;
  recentLogs: Array<{
    username: string;
    action: string;
    result: string;
    ip: string;
    device: string;
    time: string;
  }>;
  identities?: IdentityDto[];
}

const statusColor: Record<string, string> = {
  ACTIVE: 'green', DISABLED: 'red', DELETED: 'default',
};

function maskIdentifier(openid: string): string {
  if (!openid) return '-';
  if (openid.length <= 8) return openid;
  return openid.slice(0, 4) + '****' + openid.slice(-4);
}

function UserListPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<UserDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [drawerUser, setDrawerUser] = useState<UserDetail | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (keyword) params.keyword = keyword;
      if (statusFilter) params.status = statusFilter;
      const res = await apiClient.get('/admin/users', { params });
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('userManagement.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, keyword, statusFilter, t]);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleTableChange = (pag: TablePaginationConfig) => {
    if (pag.current) setPage(pag.current);
    if (pag.pageSize) setSize(pag.pageSize);
  };

  const handleViewDetail = async (userId: number) => {
    setDrawerLoading(true);
    try {
      const res = await apiClient.get(`/admin/users/${userId}`);
      setDrawerUser(res.data?.data);
    } finally {
      setDrawerLoading(false);
    }
  };

  const handleToggle = async (userId: number, action: 'enable' | 'disable') => {
    await apiClient.post(`/admin/users/${userId}/${action}`);
    message.success(t(action === 'enable' ? 'userManagement.enabled' : 'userManagement.disabled'));
    fetchUsers();
  };

  const handleUnbind = async (identityId: number) => {
    await apiClient.post(`/user/unbind/${identityId}`);
    message.success(t('account.unbindSuccess'));
    if (drawerUser) handleViewDetail(drawerUser.userId);
  };

  const columns = [
    { title: t('common.id'), dataIndex: 'userId', width: 80 },
    { title: t('common.username'), dataIndex: 'username' },
    { title: t('common.email'), dataIndex: 'email', ellipsis: true },
    { title: t('userManagement.mobile'), dataIndex: 'mobile' },
    { title: t('userManagement.role'), dataIndex: 'scope', width: 80 },
    {
      title: t('common.status'), dataIndex: 'status', width: 80,
      render: (s: string) => {
        const label = t(`userManagement.status${s.charAt(0) + s.slice(1).toLowerCase()}`);
        return <Tag color={statusColor[s] || 'default'}>{label}</Tag>;
      },
    },
    {
      title: t('userManagement.registeredAt'), dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'), width: 200,
      render: (_: unknown, record: UserDto) => (
        <Space>
          <Button type="link" size="small" onClick={() => handleViewDetail(record.userId)}>
            {t('userManagement.detail')}
          </Button>
          {record.status === 'DISABLED' ? (
            <Popconfirm title={t('userManagement.confirmEnable')} onConfirm={() => handleToggle(record.userId, 'enable')}>
              <Button type="link" size="small">{t('userManagement.enable')}</Button>
            </Popconfirm>
          ) : record.status === 'ACTIVE' ? (
            <Popconfirm title={t('userManagement.confirmDisable')} onConfirm={() => handleToggle(record.userId, 'disable')}>
              <Button type="link" size="small" danger>{t('userManagement.disable')}</Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const providerLabelMap: Record<string, string> = {
    PASSWORD: t('label.password'),
    WECHAT: t('label.wechat'),
    APPLE: t('label.apple'),
    GOOGLE: t('label.google'),
    WEBAUTHN: t('label.passkey'),
  };

  const providerColorMap: Record<string, string> = {
    PASSWORD: 'default',
    WECHAT: 'green',
    APPLE: 'default',
    GOOGLE: 'blue',
    WEBAUTHN: 'purple',
  };

  const identityColumns = [
    {
      title: t('account.provider'), dataIndex: 'provider', width: 100,
      render: (v: string) => (
        <Tag color={providerColorMap[v] || 'default'}>
          {providerLabelMap[v] || v}
        </Tag>
      ),
    },
    {
      title: t('account.openid'), dataIndex: 'openid', ellipsis: true,
      render: (v: string) => maskIdentifier(v),
    },
    {
      title: t('account.nickname'), dataIndex: 'nickname',
      render: (v: string) => v || '-',
    },
    {
      title: t('account.boundAt'), dataIndex: 'createdAt', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'), width: 80,
      render: (_: unknown, record: IdentityDto) => {
        if (record.provider === 'PASSWORD') return null;
        return (
          <Popconfirm
            title={t('account.confirmUnbind')}
            onConfirm={() => handleUnbind(record.identityId)}
          >
            <Button type="link" size="small" danger>{t('account.unbind')}</Button>
          </Popconfirm>
        );
      },
    },
  ];

  const statusFilterOptions = [
    { label: t('userManagement.statusFilterActive'), value: 'ACTIVE' },
    { label: t('userManagement.statusFilterDisabled'), value: 'DISABLED' },
    { label: t('userManagement.statusFilterDeleted'), value: 'DELETED' },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder={t('userManagement.searchPlaceholder')} allowClear style={{ width: 240 }}
          prefix={<SearchOutlined />} value={keyword}
          onChange={(e) => { setKeyword(e.target.value); setPage(1); }} />
        <Select placeholder={t('userManagement.filterStatus')} allowClear style={{ width: 120 }}
          value={statusFilter} onChange={(v) => { setStatusFilter(v); setPage(1); }}
          options={statusFilterOptions} />
        <Button icon={<ReloadOutlined />} onClick={fetchUsers}>{t('common.refresh')}</Button>
      </Space>

      {error && (
        <Alert type="error" message={error}
          action={<Button onClick={fetchUsers}>{t('common.retry')}</Button>}
          style={{ marginBottom: 16 }} />
      )}

      <Table columns={columns} dataSource={data} rowKey="userId"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={handleTableChange} scroll={{ x: 900 }} />

      <Drawer title={t('userManagement.userDetail')} open={!!drawerUser} onClose={() => setDrawerUser(null)} width={640}
        loading={drawerLoading}>
        {drawerUser && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label={t('userManagement.userId')}>{drawerUser.userId}</Descriptions.Item>
              <Descriptions.Item label={t('common.username')}>{drawerUser.username}</Descriptions.Item>
              <Descriptions.Item label={t('common.email')}>{drawerUser.email || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('userManagement.mobile')}>{drawerUser.mobile || '-'}</Descriptions.Item>
              <Descriptions.Item label={t('userManagement.role')}>{drawerUser.scope}</Descriptions.Item>
              <Descriptions.Item label={t('common.status')}>
                <Tag color={statusColor[drawerUser.status]}>
                  {t(`userManagement.status${drawerUser.status.charAt(0) + drawerUser.status.slice(1).toLowerCase()}`)}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('userManagement.registeredAt')} span={2}>
                {drawerUser.createdAt ? dayjs(drawerUser.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
              </Descriptions.Item>
            </Descriptions>

            <h4 style={{ marginTop: 24, marginBottom: 12 }}>{t('userManagement.identities')}</h4>
            <Table
              dataSource={drawerUser.identities || []}
              rowKey="identityId"
              size="small"
              pagination={false}
              columns={identityColumns}
            />

            <h4 style={{ marginTop: 24, marginBottom: 12 }}>{t('userManagement.recentLogs')}</h4>
            <Table dataSource={drawerUser.recentLogs || []} rowKey={(_, i) => String(i)}
              size="small" pagination={false} columns={[
                { title: t('userManagement.operation'), dataIndex: 'action' },
                { title: t('userManagement.result'), dataIndex: 'result', render: (v: string) => {
                  const labels: Record<string, string> = {
                    SUCCESS: t('audit.success'), FAILURE: t('audit.failure'), LOCKED: t('audit.locked'),
                  };
                  return <Tag color={v === 'SUCCESS' ? 'green' : v === 'LOCKED' ? 'orange' : 'red'}>{labels[v] || v}</Tag>;
                }},
                { title: t('userManagement.ip'), dataIndex: 'ip' },
                { title: t('userManagement.time'), dataIndex: 'time', render: (v: string) =>
                    v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-' },
              ]} />
          </>
        )}
      </Drawer>
    </div>
  );
}

export default UserListPage;
