// ── Color mappings (universal, no i18n needed) ──

/** Tag color for identity providers */
export const PROVIDER_COLORS: Record<string, string> = {
  PASSWORD: 'default',
  WECHAT: 'green',
  APPLE: 'default',
  GOOGLE: 'blue',
  SMS: 'orange',
  EMAIL: 'purple',
};

/** Tag color for login/audit results */
export const RESULT_COLORS: Record<string, string> = {
  SUCCESS: 'green',
  FAILURE: 'red',
  LOCKED: 'orange',
};

/** Tag color for entity statuses */
export const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'green',
  DISABLED: 'red',
  LOCKED: 'orange',
  PENDING: 'gold',
  EXPIRED: 'default',
};

// ── Display labels — use i18n `label.*` keys via `useTranslation()` instead ──
//
// Provider labels:  t('label.password') / t('label.wechat') / t('label.apple') / ...
// Login type labels: t('label.passwordLogin') / t('label.smsLogin') / ...
//
// See: frontend/src/i18n/locales/{zh-CN,en-US}.json → "label" section
