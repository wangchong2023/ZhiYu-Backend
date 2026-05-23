import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, Typography, message, Space, Image } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import apiClient from '../../api/client';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
  captchaCode: string;
}

interface CaptchaData {
  captchaToken: string;
  captchaImage: string;
}

function LoginPage() {
  const [loading, setLoading] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaData | null>(null);
  const [captchaLoading, setCaptchaLoading] = useState(false);
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

  const handleLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const payload: Record<string, string> = {
        username: values.username,
        password: values.password,
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
        message.success('登录成功');
        navigate('/admin/dashboard');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card style={{ width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.1)' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Title level={2} style={{ marginBottom: 4 }}>ZhiYu 管理后台</Title>
          <Text type="secondary">请使用管理员账号登录</Text>
        </div>
        <Form<LoginForm> onFinish={handleLogin} size="large">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="captchaCode">
            <Space.Compact style={{ width: '100%' }}>
              <Input
                prefix={<SafetyCertificateOutlined />}
                placeholder="验证码"
                autoComplete="off"
              />
              <Button
                type="default"
                loading={captchaLoading}
                onClick={fetchCaptcha}
                style={{ height: 40, padding: 0, minWidth: 100 }}
              >
                {captcha?.captchaImage ? (
                  <Image
                    src={captcha.captchaImage}
                    alt="验证码"
                    preview={false}
                    style={{ height: 38, width: 98, objectFit: 'contain' }}
                  />
                ) : (
                  '获取验证码'
                )}
              </Button>
            </Space.Compact>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}

export default LoginPage;
