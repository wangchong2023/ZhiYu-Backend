import { useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Tabs, Descriptions, Table, Button, Tag, message, Popconfirm,
  Space, Spin, Alert, Empty, Modal, Input, Form, Upload, Avatar,
} from 'antd';
import type { UploadProps } from 'antd';
import {
  WechatOutlined, GoogleOutlined, AppleOutlined,
  SafetyOutlined, KeyOutlined, UserOutlined, CameraOutlined,
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
  avatar: string | null;
  createdAt: string;
}

function useProviderLabels(t: (key: string) => string): Record<string, { label: string; color: string }> {
  return {
    PASSWORD: { label: t('label.password'), color: 'default' },
    WECHAT: { label: t('label.wechat'), color: 'green' },
    APPLE: { label: t('label.apple'), color: 'default' },
    GOOGLE: { label: t('label.google'), color: 'blue' },
    WEBAUTHN: { label: t('label.passkey'), color: 'purple' },
  };
}

const resultColor: Record<string, string> = {
  SUCCESS: 'green', FAILURE: 'red', LOCKED: 'orange',
};

function mask(s: string): string {
  if (!s) return '-';
  if (s.length <= 8) return s;
  return s.slice(0, 4) + '****' + s.slice(-4);
}

function MyAccountPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeKey = searchParams.get('tab') || 'profile';

  return (
    <Tabs
      activeKey={activeKey}
      onChange={(key) => setSearchParams({ tab: key })}
      items={[
        { key: 'profile', label: t('account.profile'), children: <ProfileTab /> },
        { key: 'identity', label: t('account.identity'), children: <IdentityTab /> },
        { key: 'webauthn', label: t('account.webauthn'), children: <WebAuthnTab /> },
        { key: 'security', label: t('account.security'), children: <SecurityTab /> },
        { key: 'history', label: t('account.loginHistory'), children: <LoginHistoryTab /> },
      ]}
    />
  );
}

function ProfileTab() {
  const { t } = useTranslation();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);

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

  useEffect(() => { fetchProfile(); }, []);

  const avatarUrl = profile?.avatar
    ? `/api/v1/user/avatar/${profile.userId}`
    : undefined;

  const handleUpload: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file as File);
      const res = await apiClient.post('/user/profile/avatar', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const avatarPath = res.data?.data;
      await apiClient.put('/user/profile', { avatar: avatarPath });
      message.success(t('account.avatarUploadSuccess'));
      onSuccess?.(avatarPath);
      fetchProfile();
    } catch {
      onError?.(new Error(t('account.avatarUploadFailed')));
    } finally {
      setUploading(false);
    }
  };

  if (loading) return <Spin />;
  if (!profile) return <Empty description={t('account.loadFailed')} />;

  return (
    <div>
      <div style={{ marginBottom: 24, textAlign: 'center' }}>
        <Upload
          customRequest={handleUpload}
          showUploadList={false}
          accept="image/png,image/jpeg,image/gif,image/webp"
        >
          <div style={{ position: 'relative', display: 'inline-block', cursor: 'pointer' }}>
            <Avatar
              size={96}
              src={avatarUrl}
              icon={<UserOutlined />}
              style={{
                backgroundColor: 'var(--cosmic-cyan)',
                color: 'var(--cosmic-void)',
                fontWeight: 600,
              }}
            />
            <div style={{
              position: 'absolute', bottom: 0, right: 0,
              width: 28, height: 28, borderRadius: '50%',
              background: 'var(--cosmic-cyan)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 2px 4px rgba(0,0,0,0.3)',
            }}>
              <CameraOutlined style={{ color: 'var(--cosmic-void)', fontSize: 14 }} />
            </div>
          </div>
        </Upload>
        {uploading && <div style={{ marginTop: 8, fontSize: 12, color: 'var(--cosmic-text-muted)' }}>{t('account.uploading')}</div>}
      </div>
      <Descriptions column={2} bordered size="small">
        <Descriptions.Item label={t('account.username')}>{profile.username}</Descriptions.Item>
        <Descriptions.Item label={t('account.email')}>{profile.email || '-'}</Descriptions.Item>
        <Descriptions.Item label={t('account.mobile')}>{profile.mobile || '-'}</Descriptions.Item>
        <Descriptions.Item label={t('account.role')}>{profile.scope}</Descriptions.Item>
        <Descriptions.Item label={t('account.status')}>
          <Tag color={profile.status === 'ACTIVE' ? 'green' : 'default'}>
            {profile.status ? t(`userManagement.status${profile.status.charAt(0) + profile.status.slice(1).toLowerCase()}`) : profile.status}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('account.registeredAt')}>
          {profile.createdAt ? dayjs(profile.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
        </Descriptions.Item>
      </Descriptions>
    </div>
  );
}

function IdentityTab() {
  const { t } = useTranslation();
  const [identities, setIdentities] = useState<IdentityDto[]>([]);
  const [loading, setLoading] = useState(true);
  const providerLabels = useProviderLabels(t);

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
    message.success(t('account.unbindSuccess'));
    fetchIdentities();
  };

  const columns = [
    {
      title: t('account.provider'), dataIndex: 'provider', width: 100,
      render: (v: string) => {
        const info = providerLabels[v] || { label: v, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: t('account.openid'), dataIndex: 'openid', ellipsis: true,
      render: (v: string) => mask(v),
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
      title: t('account.actions'), width: 80,
      render: (_: unknown, record: IdentityDto) => {
        if (record.provider === 'PASSWORD') return null;
        const isOnly = identities.length <= 1;
        return (
          <Popconfirm
            title={t('account.confirmUnbind')}
            onConfirm={() => handleUnbind(record.identityId)}
          >
            <Button type="link" size="small" danger disabled={isOnly}>
              {t('account.unbind')}
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
          {t('account.bindWechat')}
        </Button>
        <Button icon={<GoogleOutlined />}
          onClick={() => { window.location.href = oauthUrls.google; }}>
          {t('account.bindGoogle')}
        </Button>
        <Button icon={<AppleOutlined />}
          onClick={() => { window.location.href = oauthUrls.apple; }}>
          {t('account.bindApple')}
        </Button>
      </Space>

      <Table
        dataSource={identities}
        rowKey="identityId"
        loading={loading}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: t('account.noIdentities') }}
      />
    </div>
  );
}

function WebAuthnTab() {
  const { t } = useTranslation();
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
      message.success(t('account.registerSuccess'));
      setRegisterOpen(false);
      fetchCredentials();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : t('account.registerFailed');
      message.error(msg);
    } finally {
      setRegisterLoading(false);
    }
  };

  const handleDelete = async (credentialId: string) => {
    await apiClient.post(`/user/webauthn/delete/${credentialId}`);
    message.success(t('account.deleteSuccess'));
    fetchCredentials();
  };

  const columns = [
    {
      title: t('account.deviceName'), dataIndex: 'deviceName',
      render: (v: string) => v || t('account.unnamedDevice'),
    },
    {
      title: t('account.registeredAt'), dataIndex: 'createdAt', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('account.lastUsed'), dataIndex: 'lastUsedTime', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : t('account.lastUsed'),
    },
    {
      title: t('account.actions'), width: 80,
      render: (_: unknown, record: WebAuthnCredentialDto) => (
        <Popconfirm
          title={t('account.confirmDeleteWebAuthn')}
          onConfirm={() => handleDelete(record.credentialId)}
        >
          <Button type="link" size="small" danger>{t('common.delete')}</Button>
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
          {t('account.registerNewKey')}
        </Button>
      </Space>

      <Table
        dataSource={credentials}
        rowKey="credentialId"
        loading={loading}
        columns={columns}
        pagination={false}
        size="small"
        locale={{ emptyText: t('account.noCredentials') }}
      />

      <Modal
        title={t('account.registerNewKey')}
        open={registerOpen}
        onOk={handleRegister}
        onCancel={() => setRegisterOpen(false)}
        confirmLoading={registerLoading}
        okText={t('account.startRegister')}
        cancelText={t('common.cancel')}
      >
        <p>{t('account.webAuthnPrompt')}</p>
      </Modal>
    </div>
  );
}

function SecurityTab() {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleChangePassword = async (values: { oldPassword: string; newPassword: string; confirmPassword: string }) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error(t('account.passwordMismatch'));
      return;
    }
    setLoading(true);
    try {
      await apiClient.post('/user/change-password', {
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success(t('account.changePasswordSuccess'));
      form.resetFields();
    } catch {
      message.error(t('account.changePasswordFail'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form form={form} layout="vertical" onFinish={handleChangePassword} style={{ maxWidth: 400 }}>
      <Form.Item
        name="oldPassword"
        label={t('account.currentPassword')}
        rules={[{ required: true, message: t('account.enterCurrentPassword') }]}
      >
        <Input.Password autoComplete="current-password" />
      </Form.Item>
      <Form.Item
        name="newPassword"
        label={t('account.newPassword')}
        rules={[
          { required: true, message: t('account.enterNewPassword') },
          { min: 8, message: t('account.passwordMinLength') },
        ]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item
        name="confirmPassword"
        label={t('account.confirmPassword')}
        rules={[
          { required: true, message: t('account.enterConfirmPassword') },
          ({ getFieldValue }: { getFieldValue: (field: string) => string }) => ({
            validator(_: unknown, value: string) {
              if (!value || getFieldValue('newPassword') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error(t('account.passwordMismatch')));
            },
          }),
        ]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          {t('account.changePasswordButton')}
        </Button>
      </Form.Item>
    </Form>
  );
}

function LoginHistoryTab() {
  const { t } = useTranslation();
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

  const typeLabel = (v: string) => {
    const map: Record<string, string> = {
      PASSWORD: t('label.passwordLogin'),
      SMS: t('label.smsLogin'),
      WECHAT: t('label.wechatLogin'),
      APPLE: t('label.appleLogin'),
      GOOGLE: t('label.googleLogin'),
    };
    return map[v] || v;
  };

  const columns = [
    {
      title: t('account.loginMethod'), dataIndex: 'type', width: 100,
      render: (v: string) => <Tag>{typeLabel(v)}</Tag>,
    },
    {
      title: t('account.result'), dataIndex: 'result', width: 80,
      render: (v: string) => {
        const label: Record<string, string> = {
          SUCCESS: t('audit.success'), FAILURE: t('audit.failure'), LOCKED: t('audit.locked'),
        };
        return <Tag color={resultColor[v] || 'default'}>{label[v] || v}</Tag>;
      },
    },
    { title: t('account.ip'), dataIndex: 'ip', width: 140 },
    { title: t('account.device'), dataIndex: 'device', ellipsis: true },
    {
      title: t('account.time'), dataIndex: 'time', width: 170,
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
      locale={{ emptyText: t('account.noLoginHistory') }}
    />
  );
}

export default MyAccountPage;
