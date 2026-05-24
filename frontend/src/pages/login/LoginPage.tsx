import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, Typography, message, Tabs, Space, Checkbox } from 'antd';
import { UserOutlined, LockOutlined, PhoneOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';

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
        message.success(t('login.loginSuccess'));
        navigate('/admin/dashboard');
      }
    } finally {
      setLoading(false);
    }
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
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card style={{ width: 420, boxShadow: '0 8px 32px rgba(0,0,0,0.1)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={2} style={{ marginBottom: 4 }}>{t('app.title')}</Title>
          <Text type="secondary">{t('app.subtitle')}</Text>
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
                <Form form={form} onFinish={handleLogin} size="large">
                  <Form.Item name="username" rules={[{ required: true, message: t('login.usernameRequired') }]}>
                    <Input prefix={<UserOutlined />} placeholder={t('login.username')} autoComplete="username" />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, message: t('login.passwordRequired') }]}>
                    <Input.Password prefix={<LockOutlined />} placeholder={t('login.password')} autoComplete="current-password" />
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
                <Form form={form} onFinish={handleSmsLogin} size="large">
                  <Form.Item
                    name="phone"
                    rules={[
                      { required: true, message: t('login.phoneRequired') },
                      { pattern: /^1[3-9]\d{9}$/, message: t('login.phoneInvalid') },
                    ]}
                  >
                    <Input prefix={<PhoneOutlined />} placeholder={t('login.phone')} />
                  </Form.Item>
                  <Form.Item
                    name="smsCode"
                    rules={[{ required: true, message: t('login.smsCodeRequired') }]}
                  >
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
      </Card>
    </div>
  );
}

export default LoginPage;
