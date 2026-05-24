// ── Identity provider labels & colors ──
export const PROVIDER_LABELS: Record<string, string> = {
  PASSWORD: '密码',
  WECHAT: '微信',
  APPLE: 'Apple',
  GOOGLE: 'Google',
  SMS: '短信',
  EMAIL: '邮箱',
};

export const PROVIDER_COLORS: Record<string, string> = {
  PASSWORD: 'default',
  WECHAT: 'green',
  APPLE: 'default',
  GOOGLE: 'blue',
  SMS: 'orange',
  EMAIL: 'purple',
};

// ── Login method type labels ──
export const TYPE_LABELS: Record<string, string> = {
  PASSWORD: '密码登录',
  SMS: '短信登录',
  WECHAT: '微信登录',
  APPLE: 'Apple 登录',
  GOOGLE: 'Google 登录',
};

// ── Result color mapping ──
export const RESULT_COLORS: Record<string, string> = {
  SUCCESS: 'green',
  FAILURE: 'red',
  LOCKED: 'orange',
};

// ── Status color mapping ──
export const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'green',
  DISABLED: 'red',
  LOCKED: 'orange',
  PENDING: 'gold',
  EXPIRED: 'default',
};
