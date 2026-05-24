import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import App from './App';
import './i18n';
import './styles/cosmic-theme.css';

const cosmicTheme = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#38bdf8',
    colorSuccess: '#22c55e',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorInfo: '#38bdf8',
    colorBgBase: '#080c14',
    colorBgContainer: '#151d2e',
    colorBgElevated: '#1a2338',
    colorBgLayout: '#0f1624',
    colorBorder: 'rgba(56, 189, 248, 0.08)',
    colorBorderSecondary: 'rgba(56, 189, 248, 0.06)',
    borderRadius: 10,
    borderRadiusLG: 14,
    colorText: 'rgba(226, 232, 240, 0.92)',
    colorTextSecondary: 'rgba(148, 163, 184, 0.75)',
    colorTextTertiary: 'rgba(100, 116, 139, 0.55)',
    fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
    fontSize: 14,
    lineHeight: 1.6,
    controlHeight: 40,
  },
  components: {
    Menu: {
      darkItemBg: '#0f1624',
      darkSubMenuItemBg: '#0f1624',
      darkItemSelectedBg: 'rgba(56, 189, 248, 0.15)',
      darkItemHoverBg: 'rgba(56, 189, 248, 0.08)',
      itemBorderRadius: 8,
    },
    Card: {
      colorBgContainer: 'linear-gradient(135deg, rgba(26, 35, 56, 0.8) 0%, rgba(21, 29, 46, 0.7) 100%)',
      borderRadiusLG: 14,
      paddingLG: 20,
    },
    Button: {
      borderRadius: 8,
      controlHeight: 40,
    },
    Input: {
      borderRadius: 8,
      controlHeight: 40,
    },
    Table: {
      borderRadius: 10,
      headerBg: 'rgba(26, 35, 56, 0.6)',
      rowHoverBg: 'rgba(56, 189, 248, 0.04)',
    },
    Select: {
      borderRadius: 8,
    },
    Tag: {
      borderRadiusSM: 4,
    },
    Statistic: {
      contentFontSize: 28,
    },
    Tabs: {
      itemSelectedColor: '#38bdf8',
      inkBarColor: '#38bdf8',
    },
  },
};

function AntdProvider() {
  const lang = localStorage.getItem('lang') || 'zh-CN';
  const locale = lang.startsWith('en') ? enUS : zhCN;
  return (
    <ConfigProvider theme={cosmicTheme} locale={locale}>
      <App />
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AntdProvider />
    </BrowserRouter>
  </React.StrictMode>,
);
