import { useEffect, useState, useRef, useCallback } from 'react';
import { Modal, Typography } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import authApi from '../api/authApi';

const { Text } = Typography;

const WARN_BEFORE_SEC = 300;

function parseJwtExp(token: string): number {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp || 0;
  } catch {
    return 0;
  }
}

function SessionTimeoutOverlay() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const expiredRef = useRef(false);

  const refreshToken = useCallback(async () => {
    const rt = localStorage.getItem('refreshToken');
    if (!rt) return;
    try {
      const res = await authApi.refresh(rt);
      const data = res.data?.data;
      if (data) {
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
      }
      setOpen(false);
    } catch {
      setOpen(false);
    }
  }, []);

  useEffect(() => {
    const timer = setInterval(() => {
      const at = localStorage.getItem('accessToken');
      if (!at) return;

      const exp = parseJwtExp(at);
      const now = Math.floor(Date.now() / 1000);
      const remaining = exp - now;

      if (remaining <= 0 && !expiredRef.current) {
        expiredRef.current = true;
        setOpen(false);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/admin/login';
        return;
      }

      if (remaining > 0 && remaining <= WARN_BEFORE_SEC) {
        setCountdown(remaining);
        setOpen(true);
      }
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  if (!open) return null;

  const min = Math.floor(countdown / 60);
  const sec = countdown % 60;

  return (
    <Modal
      open={open}
      closable={false}
      maskClosable={false}
      title={
        <span>
          <ExclamationCircleOutlined style={{ color: '#faad14', marginRight: 8 }} />
          {t('session.expiringTitle')}
        </span>
      }
      okText={t('session.extend')}
      cancelText={t('session.logoutNow')}
      onOk={refreshToken}
      onCancel={() => {
        setOpen(false);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/admin/login';
      }}
    >
      <Text>
        {t('session.expiringMessage', { min, sec: String(sec).padStart(2, '0') })}
      </Text>
    </Modal>
  );
}

export default SessionTimeoutOverlay;
