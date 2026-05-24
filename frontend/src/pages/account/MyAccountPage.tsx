import { useEffect, useState, useCallback } from 'react';
import {
  Tabs, Descriptions, Table, Button, Tag, message, Popconfirm,
  Space, Spin, Alert, Empty, Modal, Input,
} from 'antd';
import {
  WechatOutlined, GoogleOutlined, AppleOutlined,
  SafetyOutlined, KeyOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import apiClient from '../../api/client';
import authApi, { oauthUrls } from '../../api/authApi';
import type { IdentityDto, LoginLogDto, WebAuthnCredentialDto } from '../../api/types';

interface UserProfile {
  userId: number;
  username: string;
  email: string;
  mobile: string;
  scope: string;
  status: string;
  createdAt: string;
}

const providerLabels: Record<string, { label: string; color: string }> = {
  PASSWORD: { label: '密码', color: 'default' },
  WECHAT: { label: '微信', color: 'green' },
  APPLE: { label: 'Apple', color: 'default' },
  GOOGLE: { label: 'Google', color: 'blue' },
  WEBAUTHN: { label: '通行密钥', color: 'purple' },
};

const typeLabels: Record<string, string> = {
  PASSWORD: '密码', WECHAT: '微信', APPLE: 'Apple',
  GOOGLE: 'Google', WEBAUTHN: '通行密钥',
};

const resultColor: Record<string, string> = {
  SUCCESS: 'green', FAILURE: 'red', LOCKED: 'orange',
};

function mask(s: string): string {
  if (!s) return '-';
  if (s.length <= 8) return s;
  return s.slice(0, 4) + '****' + s.slice(-4);
}

function MyAccountPage() {
  return (
    <Tabs
      defaultActiveKey="profile"
      items={[
        { key: 'profile', label: '个人信息', children: <ProfileTab /> },
        { key: 'identity', label: '认证身份', children: <IdentityTab /> },
        { key: 'webauthn', label: '通行密钥', children: <WebAuthnTab /> },
        { key: 'history', label: '登录历史', children: <LoginHistoryTab /> },
      ]}
    />
  );
}

function ProfileTab() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const res = await apiClient.get('/user/profile');
        setProfile(res.data?.data);
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    };
    fetchProfile();
  }, []);

  if (loading) return <Spin />;
  if (!profile) return <Empty description="无法加载个人信息" />;

  return (
    <Descriptions column={2} bordered size="small">
      <Descriptions.Item label="用户名">{profile.username}</Descriptions.Item>
      <Descriptions.Item label="邮箱">{profile.email || '-'}</Descriptions.Item>
      <Descriptions.Item label="手机">{profile.mobile || '-'}</Descriptions.Item>
      <Descriptions.Item label="角色">{profile.scope}</Descriptions.Item>
      <Descriptions.Item label="状态">
        <Tag color={profile.status === '正常' ? 'green' : 'default'}>{profile.status}</Tag>
      </Descriptions.Item>
      <Descriptions.Item label="注册时间">
        {profile.createdAt ? dayjs(profile.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
      </Descriptions.Item>
    </Descriptions>
  );
}

function IdentityTab() {
  const [identities, setIdentities] = useState<IdentityDto[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchIdentities = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/user/identities');
      setIdentities(res.data?.data?.identities || res.data?.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchIdentities(); }, [fetchIdentities]);

  const handleUnbind = async (identityId: number) => {
    await apiClient.delete(`/user/identities/${identityId}`);
    message.success('已解绑');
    fetchIdentities();
  };

  const columns = [
    {
      title: '类型', dataIndex: 'provider', width: 100,
      render: (v: string) => {
        const info = providerLabels[v] || { label: v, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: '标识', dataIndex: 'openid', ellipsis: true,
      render: (v: string) => mask(v),
    },
    {
      title: '昵称', dataIndex: 'nickname',
      render: (v: string) => v || '-',
    },
    {
      title: '绑定时间', dataIndex: 'createdAt', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 80,
      render: (_: unknown, record: IdentityDto) => {
        if (record.provider === 'PASSWORD') return null;
        const isOnly = identities.length <= 1;
        return (
          <Popconfirm
            title="确定解绑该认证方式？"
            onConfirm={() => handleUnbind(record.identityId)}
          >
            <Button type="link" size="small" danger disabled={isOnly}>
              解绑
            </Button>
          </Popconfirm>
        );
      },
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<WechatOutlined />}
          onClick={() => { window.location.href = oauthUrls.wechat; }}>
          绑定微信
        </Button>
        <Button icon={<GoogleOutlined />}
          onClick={() => { window.location.href = oauthUrls.google; }}>
          绑定 Google
        </Button>
        <Button icon={<AppleOutlined />}
          onClick={() => { window.location.href = oauthUrls.apple; }}>
          绑定 Apple
        </Button>
      </Space>

      <Table
        dataSource={identities}
        rowKey="identityId"
        loading={loading}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '暂未绑定第三方账号' }}
      />
    </div>
  );
}

function WebAuthnTab() {
  const [credentials, setCredentials] = useState<WebAuthnCredentialDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [registerLoading, setRegisterLoading] = useState(false);

  const fetchCredentials = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/user/webauthn/credentials');
      setCredentials(res.data?.data || []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchCredentials(); }, [fetchCredentials]);

  const handleRegister = async () => {
    setRegisterLoading(true);
    try {
      const beginRes = await authApi.webauthnRegisterBegin();
      const { challengeId, optionsJson } = beginRes.data.data;
      const credential = await navigator.credentials.create({
        publicKey: JSON.parse(optionsJson),
      });
      await authApi.webauthnRegisterFinish(challengeId, JSON.stringify(credential));
      message.success('通行密钥注册成功');
      setRegisterOpen(false);
      fetchCredentials();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '注册失败';
      message.error(msg);
    } finally {
      setRegisterLoading(false);
    }
  };

  const handleDelete = async (credentialId: string) => {
    await apiClient.post(`/user/webauthn/delete/${credentialId}`);
    message.success('已删除');
    fetchCredentials();
  };

  const columns = [
    {
      title: '设备名称', dataIndex: 'deviceName',
      render: (v: string) => v || '未命名设备',
    },
    {
      title: '注册时间', dataIndex: 'createdAt', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '最近使用', dataIndex: 'lastUsedTime', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '未使用',
    },
    {
      title: '操作', width: 80,
      render: (_: unknown, record: WebAuthnCredentialDto) => (
        <Popconfirm
          title="确定删除该通行密钥？"
          onConfirm={() => handleDelete(record.credentialId)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<KeyOutlined />}
          onClick={() => setRegisterOpen(true)}
        >
          注册新密钥
        </Button>
      </Space>

      <Table
        dataSource={credentials}
        rowKey="credentialId"
        loading={loading}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: '暂未注册通行密钥' }}
      />

      <Modal
        title="注册通行密钥"
        open={registerOpen}
        onOk={handleRegister}
        onCancel={() => setRegisterOpen(false)}
        confirmLoading={registerLoading}
        okText="开始注册"
        cancelText="取消"
      >
        <p>点击"开始注册"后，浏览器将提示您验证指纹、面容或输入设备 PIN 码。</p>
      </Modal>
    </div>
  );
}

function LoginHistoryTab() {
  const [data, setData] = useState<LoginLogDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      try {
        const res = await apiClient.get('/user/login-history', { params: { page: 1, size: 20 } });
        setData(res.data?.data?.records || res.data?.data || []);
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, []);

  const columns = [
    {
      title: '方式', dataIndex: 'type', width: 100,
      render: (v: string) => <Tag>{typeLabels[v] || v}</Tag>,
    },
    {
      title: '结果', dataIndex: 'result', width: 80,
      render: (v: string) => <Tag color={resultColor[v] || 'default'}>{v}</Tag>,
    },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '设备', dataIndex: 'device', ellipsis: true },
    {
      title: '时间', dataIndex: 'time', width: 170,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
  ];

  return (
    <Table
      dataSource={data}
      rowKey="id"
      loading={loading}
      columns={columns}
      pagination={false}
      size="small"
      locale={{ emptyText: '暂无登录记录' }}
    />
  );
}

export default MyAccountPage;
