import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import App from './App';
import './i18n';

function AntdProvider() {
  const lang = localStorage.getItem('lang') || 'zh-CN';
  const locale = lang.startsWith('en') ? enUS : zhCN;
  return (
    <ConfigProvider locale={locale}>
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
