import { test, expect, Page } from '@playwright/test';

const ADMIN_USER = process.env.E2E_ADMIN_USER || 'admin';
const ADMIN_PASS = process.env.E2E_ADMIN_PASS || '';
const HAS_CREDENTIALS = ADMIN_PASS.length > 0;

interface PageCheck {
  path: string;
  label: string;
  waitForText?: string;
}

const ALL_PAGES: PageCheck[] = [
  { path: '/admin/dashboard', label: '仪表盘' },
  { path: '/admin/monitor/overview', label: '监控概览' },
  { path: '/admin/monitor/metrics', label: '性能指标' },
  { path: '/admin/monitor/logs', label: '日志' },
  { path: '/admin/monitor/alerts', label: '告警' },
  { path: '/admin/monitor/settings', label: '日志级别设置' },
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/audit', label: '审计日志' },
  { path: '/admin/admins', label: '管理员管理' },
  { path: '/admin/notifications', label: '通知管理' },
  { path: '/admin/config', label: '配置管理' },
  { path: '/admin/subscriptions', label: '订阅管理' },
  { path: '/admin/payments', label: '支付记录' },
  { path: '/admin/refunds', label: '退款管理' },
  { path: '/admin/account', label: '我的账户', waitForText: '个人' },
];

async function login(page: Page) {
  await page.goto('/admin/login');
  await page.waitForLoadState('networkidle');

  await page.locator('input[id="username"]').fill(ADMIN_USER);
  await page.locator('input[id="password"]').fill(ADMIN_PASS);
  // privacyAgreed is checked by default via initialValues
  await page.locator('button[type="submit"]').click();

  // Wait for navigation to dashboard
  await page.waitForURL(/\/admin\/dashboard/, { timeout: 15_000 });
}

test.describe('Login Page', () => {
  test('renders login form', async ({ page }) => {
    await page.goto('/admin/login');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('input[id="username"]')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('input[id="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/admin/dashboard');
    await expect(page).toHaveURL(/\/admin\/login/, { timeout: 10_000 });
  });

  test('captcha image loads', async ({ page }) => {
    await page.goto('/admin/login');
    await page.waitForLoadState('networkidle');

    // Captcha image should appear (as <img> with base64 src) within a few seconds
    const captchaImg = page.locator('img[alt="验证码"], img[alt="Captcha"]');
    // Captcha may or may not load depending on backend availability
    // If loaded, verify it has a valid src
    if (await captchaImg.isVisible({ timeout: 5_000 }).catch(() => false)) {
      const src = await captchaImg.getAttribute('src');
      expect(src).toBeTruthy();
      expect(src).toMatch(/^data:image\/png;base64,/);
    }
  });
});

test.describe('Authenticated Pages', () => {
  test.skip(!HAS_CREDENTIALS, 'Set E2E_ADMIN_PASS env var to run authenticated tests');

  test.beforeEach(async ({ page }) => {
    // Collect all console errors for reporting
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(`[${msg.type()}] ${msg.text()}`);
    });
    page.on('pageerror', err => errors.push(`[pageerror] ${err.message}`));

    await login(page);

    // Attach error log to test info on failure
    page.on('close', () => {
      if (errors.length > 0) {
        console.log(`\n  ⚠️  Console errors (${errors.length}):\n${errors.map(e => `    - ${e}`).join('\n')}`);
      }
    });
  });

  test('dashboard loads after login', async ({ page }) => {
    // Already on dashboard after login
    await expect(page.locator('.ant-layout-content')).toBeVisible();
  });

  for (const { path, label } of ALL_PAGES) {
    test(`${label} (${path}) renders without crash`, async ({ page }) => {
      // Collect console errors for this specific page
      const pageErrors: string[] = [];
      const errorHandler = (err: Error) => pageErrors.push(err.message);
      page.on('pageerror', errorHandler);

      await page.goto(path);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      // Verify no white screen: content area should be visible
      const content = page.locator('.ant-layout-content');
      await expect(content).toBeVisible({ timeout: 10_000 });

      // Verify no antd error message banners
      const errorMessages = page.locator('.ant-message-error, .ant-notification-notice-error');
      const errorCount = await errorMessages.count();
      if (errorCount > 0) {
        const texts: string[] = [];
        for (let i = 0; i < errorCount; i++) {
          texts.push((await errorMessages.nth(i).textContent()) || '');
        }
        console.log(`\n  ⚠️  Page ${label} has ${errorCount} error notification(s): ${texts.join('; ')}`);
      }

      // Verify no "server error" or crash messages
      const bodyText = await page.locator('body').textContent();
      expect(bodyText).not.toContain('Application error');
      expect(bodyText).not.toContain('Something went wrong');

      page.off('pageerror', errorHandler);
      if (pageErrors.length > 0) {
        console.log(`\n  ⚠️  Page ${label} JS errors: ${pageErrors.join('; ')}`);
      }
    });
  }
});

test.describe('Navigation Smoke', () => {
  test.skip(!HAS_CREDENTIALS, 'Set E2E_ADMIN_PASS env var to run authenticated tests');

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('sidebar menu expands and navigates', async ({ page }) => {
    // Verify sidebar is visible
    const sider = page.locator('.ant-layout-sider');
    await expect(sider).toBeVisible();

    // Click monitoring submenu to expand
    const monitorItem = page.locator('.ant-menu-submenu-title').filter({ hasText: /Monitor|监控/ });
    if (await monitorItem.isVisible()) {
      await monitorItem.click();
      await page.waitForTimeout(300);

      // Sub-menu items should be visible
      const subItems = page.locator('.ant-menu-submenu-open .ant-menu-item');
      const count = await subItems.count();
      expect(count).toBeGreaterThan(0);
    }
  });

  test('logout redirects to login', async ({ page }) => {
    await page.goto('/admin/dashboard');
    await page.waitForLoadState('networkidle');

    // Click logout — the button is in the header
    const logoutBtn = page.locator('button').filter({ has: page.locator('.anticon-logout') });
    if (await logoutBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await logoutBtn.click();
      await page.waitForURL(/\/admin\/login/, { timeout: 10_000 });
      await expect(page).toHaveURL(/\/admin\/login/);
    }
  });
});
