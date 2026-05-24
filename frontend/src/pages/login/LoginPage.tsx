import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Typography, message, Tabs, Space, Checkbox } from 'antd';
import { UserOutlined, LockOutlined, PhoneOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import type { VersionDto } from '../../api/types';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
  phone: string;
  smsCode: string;
  captchaCode: string;
  privacyAgreed: boolean;
}

interface CaptchaData {
  captchaToken: string;
  captchaImage: string;
}

function LoginPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('password');
  const [captcha, setCaptcha] = useState<CaptchaData | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [smsSending, setSmsSending] = useState(false);
  const [smsCountdown, setSmsCountdown] = useState(0);
  const [form] = Form.useForm<LoginForm>();
  const navigate = useNavigate();
  const [backendVersion, setBackendVersion] = useState<VersionDto | null>(null);

  useEffect(() => {
    apiClient.get('/admin/version').then((res) => {
      if (res.data?.data) setBackendVersion(res.data.data);
    }).catch(() => {});
  }, []);

  const fetchCaptcha = useCallback(async () => {
    setCaptchaLoading(true);
    try {
      const res = await apiClient.get('/auth/captcha/image', {
        params: { sceneId: 'zhiyu_login' },
      });
      setCaptcha(res.data?.data);
    } catch {
      // captcha is optional, don't block login
    } finally {
      setCaptchaLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCaptcha();
  }, [fetchCaptcha]);

  useEffect(() => {
    if (smsCountdown <= 0) return;
    const timer = setTimeout(() => setSmsCountdown(smsCountdown - 1), 1000);
    return () => clearTimeout(timer);
  }, [smsCountdown]);

  const handleSendSms = async () => {
    try {
      await form.validateFields(['phone']);
    } catch {
      return;
    }
    setSmsSending(true);
    try {
      await apiClient.post('/auth/sms/send', {
        phone: form.getFieldValue('phone'),
        scene: 'admin_login',
      });
      message.success(t('login.smsSent'));
      setSmsCountdown(60);
    } catch {
      message.error(t('login.smsFailed'));
    } finally {
      setSmsSending(false);
    }
  };

  const handleLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const payload: Record<string, string> = {
        username: values.username,
        password: values.password,
        privacyConsent: String(values.privacyAgreed),
      };
      if (captcha && values.captchaCode) {
        payload.captchaToken = captcha.captchaToken;
        payload.captchaCode = values.captchaCode;
      }
      const res = await apiClient.post('/admin/login', payload);
      const data = res.data?.data;
      if (data) {
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        storeCredential(values.username, values.password);
        message.success(t('login.loginSuccess'));
        navigate('/admin/dashboard');
      }
    } finally {
      setLoading(false);
    }
  };

  const storeCredential = (username: string, password: string) => {
    try {
      if ('PasswordCredential' in window) {
        const cred = new (window as any).PasswordCredential({
          id: username, password, name: username,
        });
        navigator.credentials.store(cred);
      }
    } catch { /* credential store is best-effort */ }
  };

  const handleSmsLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const payload: Record<string, string> = {
        phone: values.phone,
        smsCode: values.smsCode,
        grantType: 'sms',
        privacyConsent: String(values.privacyAgreed),
      };
      if (captcha && values.captchaCode) {
        payload.captchaToken = captcha.captchaToken;
        payload.captchaCode = values.captchaCode;
      }
      const res = await apiClient.post('/admin/login', payload);
      const data = res.data?.data;
      if (data) {
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        message.success(t('login.loginSuccess'));
        navigate('/admin/dashboard');
      }
    } finally {
      setLoading(false);
    }
  };

  const captchaNode = (
    <Form.Item name="captchaCode">
      <Space.Compact style={{ width: '100%' }}>
        <Input
          prefix={<SafetyCertificateOutlined />}
          placeholder={t('login.captcha')}
          autoComplete="off"
          style={{ flex: 1 }}
        />
        <Button
          type="default"
          loading={captchaLoading}
          onClick={fetchCaptcha}
          style={{ height: 40, padding: 0, minWidth: 100 }}
        >
          {captcha?.captchaImage ? (
            <img
              src={captcha.captchaImage}
              alt={t('login.captcha')}
              style={{ height: 38, width: 98, objectFit: 'contain' }}
            />
          ) : (
            t('login.getCaptcha')
          )}
        </Button>
      </Space.Compact>
    </Form.Item>
  );

  return (
    <div className="cosmic-bg" style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh',
    }}>
      <div className="glass-panel" style={{
        width: 420, padding: '32px 36px',
      }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{
            width: 48, height: 48, margin: '0 auto 16px',
            borderRadius: 12,
            background: 'linear-gradient(135deg, var(--cosmic-cyan-dim), rgba(129, 140, 248, 0.2))',
            border: '1px solid var(--cosmic-border-active)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 0 24px var(--cosmic-cyan-dim)',
          }}>
            <span style={{ color: 'var(--cosmic-cyan)', fontWeight: 800, fontSize: 20 }}>ZY</span>
          </div>
          <Title level={2} className="cosmic-heading" style={{ marginBottom: 4, color: 'var(--cosmic-cyan)' }}>
            {t('app.title')}
          </Title>
          <Text style={{ color: 'var(--cosmic-text-secondary)', fontSize: 13 }}>
            {t('app.subtitle')}
          </Text>
        </div>

        <Tabs
          activeKey={activeTab}
          onChange={(key) => { setActiveTab(key); form.resetFields(); }}
          centered
          items={[
            {
              key: 'password',
              label: t('login.passwordTab'),
              children: (
                <Form form={form} onFinish={handleLogin} size="large" initialValues={{ privacyAgreed: true }}>
                  <Form.Item name="username" rules={[{ required: true, message: t('login.usernameRequired') }]}>
                    <Input prefix={<UserOutlined />} placeholder={t('login.username')} autoComplete="username" name="username" />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, message: t('login.passwordRequired') }]}>
                    <Input.Password prefix={<LockOutlined />} placeholder={t('login.password')} autoComplete="current-password" name="password" />
                  </Form.Item>
                  {captchaNode}
                  <Form.Item
                    name="privacyAgreed"
                    valuePropName="checked"
                    rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error(t('login.privacyRequired'))) }]}
                  >
                    <Checkbox>
                      {t('login.privacyAgree')}{' '}
                      <a href="/privacy" target="_blank">{t('login.privacyPolicy')}</a>
                    </Checkbox>
                  </Form.Item>
                  <Form.Item style={{ marginTop: 24 }}>
                    <Button type="primary" htmlType="submit" loading={loading} block>
                      {t('login.loginButton')}
                    </Button>
                  </Form.Item>
                </Form>
              ),
            },
            {
              key: 'sms',
              label: t('login.smsTab'),
              children: (
                <Form form={form} onFinish={handleSmsLogin} size="large" initialValues={{ privacyAgreed: true }}>
                  <Form.Item
                    name="phone"
                    rules={[
                      { required: true, message: t('login.phoneRequired') },
                      { pattern: /^1[3-9]\d{9}$/, message: t('login.phoneInvalid') },
                    ]}
                  >
                    <Input prefix={<PhoneOutlined />} placeholder={t('login.phone')} />
                  </Form.Item>
                  <Form.Item name="smsCode">
                    <Space.Compact style={{ width: '100%' }}>
                      <Input placeholder={t('login.smsCode')} style={{ flex: 1 }} />
                      <Button
                        type="default"
                        loading={smsSending}
                        disabled={smsCountdown > 0}
                        onClick={handleSendSms}
                        style={{ whiteSpace: 'nowrap' }}
                      >
                        {smsCountdown > 0 ? `${smsCountdown}s` : t('login.sendSms')}
                      </Button>
                    </Space.Compact>
                  </Form.Item>
                  {captchaNode}
                  <Form.Item
                    name="privacyAgreed"
                    valuePropName="checked"
                    rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error(t('login.privacyRequired'))) }]}
                  >
                    <Checkbox>
                      {t('login.privacyAgree')}{' '}
                      <a href="/privacy" target="_blank">{t('login.privacyPolicy')}</a>
                    </Checkbox>
                  </Form.Item>
                  <Form.Item style={{ marginTop: 24 }}>
                    <Button type="primary" htmlType="submit" loading={loading} block>
                      {t('login.loginButton')}
                    </Button>
                  </Form.Item>
                </Form>
              ),
            },
          ]}
        />

        <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px solid var(--cosmic-border)', textAlign: 'center' }}>
          <Text style={{ color: 'var(--cosmic-text-muted)', fontSize: 10, display: 'block', lineHeight: '16px' }}>
            FE: v{__APP_VERSION__}-{__GIT_HASH__} / {__BUILD_TIME__.slice(0, 16).replace('T', ' ')} GMT
          </Text>
          {backendVersion && (
            <Text style={{ color: 'var(--cosmic-text-muted)', fontSize: 10, display: 'block', lineHeight: '16px' }}>
              BE: v{backendVersion.version}-{backendVersion.commitId} / {backendVersion.buildTime}
            </Text>
          )}
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
