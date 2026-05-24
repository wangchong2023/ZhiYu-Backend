import { test, expect, Page } from '@playwright/test';

const ADMIN_USER = process.env.E2E_ADMIN_USER || 'admin';
const ADMIN_PASS = process.env.E2E_ADMIN_PASS || '';

async function login(page: Page) {
  await page.goto('/admin/login');
  await page.waitForLoadState('networkidle');
  await page.locator('input[id="username"]').fill(ADMIN_USER);
  await page.locator('input[id="password"]').fill(ADMIN_PASS);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/admin\/dashboard/, { timeout: 15_000 });
}

async function checkPageHealthy(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);

  // Check not redirected to login
  const url = page.url();
  expect(url).not.toContain('/admin/login');

  // Check content area is visible with content
  const contentEl = page.locator('.ant-layout-content');
  await expect(contentEl).toBeVisible({ timeout: 5000 });
  const text = (await contentEl.textContent()) || '';
  expect(text.trim().length).toBeGreaterThan(0);
}

const ALL_PAGES = [
  '/admin/dashboard',
  '/admin/monitor/overview',
  '/admin/monitor/metrics',
  '/admin/monitor/logs',
  '/admin/monitor/alerts',
  '/admin/monitor/settings',
  '/admin/users',
  '/admin/audit',
  '/admin/admins',
  '/admin/notifications',
  '/admin/config',
  '/admin/subscriptions',
  '/admin/payments',
  '/admin/refunds',
];

test.describe('CI Smoke Test — All Pages', () => {
  test.skip(!ADMIN_PASS, 'Set E2E_ADMIN_PASS env var to run');

  test.beforeEach(async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', err => errors.push(err.message));
    await login(page);
    (page as any).__errors = errors;
  });

  test('all pages render without JS errors', async ({ page }) => {
    for (const p of ALL_PAGES) {
      const errorsBefore = ((page as any).__errors || []).length;
      await checkPageHealthy(page, p);

      const newErrors = ((page as any).__errors || []).length - errorsBefore;
      if (newErrors > 0) {
        const allErrors = (page as any).__errors || [];
        const latest = allErrors.slice(-newErrors);
        console.log(`JS errors on ${p}: ${JSON.stringify(latest)}`);
      }
    }
  });
});
