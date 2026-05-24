import { test, expect, Page } from '@playwright/test';
import path from 'path';

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

async function isPageBlank(page: Page): Promise<{ blank: boolean; reason: string }> {
  const url = page.url();

  // If we got redirected to login, that's a blank-page equivalent
  if (url.includes('/admin/login')) {
    return { blank: true, reason: `redirected to login (URL: ${url})` };
  }

  const contentEl = page.locator('.ant-layout-content');
  const visible = await contentEl.isVisible({ timeout: 2000 }).catch(() => false);
  if (!visible) {
    return { blank: true, reason: '.ant-layout-content not visible' };
  }

  const textContent = (await contentEl.textContent({ timeout: 2000 }).catch(() => '')) || '';
  if (textContent.trim().length === 0) {
    return { blank: true, reason: '.ant-layout-content has empty text' };
  }

  return { blank: false, reason: 'ok' };
}

test.describe('Blank Page Root Cause Diagnosis', () => {
  test.skip(!ADMIN_PASS, 'Set E2E_ADMIN_PASS env var to run');

  test.beforeEach(async ({ page }) => {
    const errors: string[] = [];
    const networkFails: string[] = [];

    page.on('pageerror', err => {
      errors.push(err.message);
    });

    page.on('response', resp => {
      if (resp.status() === 401) {
        networkFails.push(`401 on ${resp.url()}`);
      }
      if (resp.status() >= 500) {
        networkFails.push(`${resp.status()} on ${resp.url()}`);
      }
    });

    await login(page);
    (page as any).__errors = errors;
    (page as any).__networkFails = networkFails;
  });

  // Diagnostic test: rapid submenu clicks with full error capture
  test('diagnostic: submenu toggle with error capture', async ({ page }) => {
    await page.goto('/admin/dashboard');
    await page.waitForLoadState('networkidle');

    for (let i = 0; i < 10; i++) {
      // Toggle monitor submenu
      const monitorParent = page.locator('.ant-menu-submenu-title').filter({ hasText: /监控|Monitoring/ }).first();
      if (await monitorParent.isVisible().catch(() => false)) {
        await monitorParent.click();
        await page.waitForTimeout(400);
      }

      // Click a child item
      const overviewItem = page.locator('.ant-menu-item').filter({ hasText: /概览|Overview/ }).first();
      if (await overviewItem.isVisible().catch(() => false)) {
        await overviewItem.click();
        await page.waitForTimeout(600);
      }

      const result = await isPageBlank(page);
      const jsErrors = (page as any).__errors || [];
      const netFails = (page as any).__networkFails || [];

      if (result.blank) {
        console.log(`\n=== BLANK at iteration ${i} ===`);
        console.log(`Reason: ${result.reason}`);
        console.log(`Current URL: ${page.url()}`);
        console.log(`JS errors: ${JSON.stringify(jsErrors)}`);
        console.log(`Network fails: ${JSON.stringify(netFails)}`);

        // Take screenshot and dump full page HTML
        await page.screenshot({
          path: path.resolve(test.info().outputDir, `diagnostic-blank-${i}.png`),
          fullPage: true,
        });
        const html = await page.content();
        console.log(`Page HTML (first 2000 chars): ${html.substring(0, 2000)}`);
      }

      expect(result.blank).toBe(false);
    }
  });

  // Check if specific pages throw errors
  test('diagnostic: visit all monitor pages and capture errors', async ({ page }) => {
    const monitorPages = [
      '/admin/monitor/overview',
      '/admin/monitor/metrics',
      '/admin/monitor/logs',
      '/admin/monitor/alerts',
      '/admin/monitor/settings',
    ];

    for (const p of monitorPages) {
      await page.goto(p);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000);

      const result = await isPageBlank(page);
      const jsErrors = (page as any).__errors || [];
      const netFails = (page as any).__networkFails || [];

      console.log(`\n--- Page: ${p} ---`);
      console.log(`Blank: ${result.blank} (${result.reason})`);
      console.log(`URL: ${page.url()}`);
      console.log(`JS errors: ${JSON.stringify(jsErrors)}`);
      console.log(`Network fails: ${JSON.stringify(netFails)}`);

      if (result.blank) {
        await page.screenshot({
          path: path.resolve(test.info().outputDir, `page-${p.replace(/\//g, '_')}.png`),
          fullPage: true,
        });
      }

      expect(result.blank).toBe(false);
    }
  });

  // Clicking same menu item rapidly
  test('diagnostic: rapid same-page clicks with error capture', async ({ page }) => {
    await page.goto('/admin/monitor/overview');
    await page.waitForLoadState('networkidle');

    for (let i = 0; i < 20; i++) {
      const menuItem = page.locator('.ant-menu-item').filter({ hasText: /性能指标|Metrics/ }).first();
      const visible = await menuItem.isVisible().catch(() => false);

      if (visible) {
        await menuItem.click();
      } else {
        // Need to expand submenu first
        const monitorParent = page.locator('.ant-menu-submenu-title').filter({ hasText: /监控|Monitoring/ }).first();
        await monitorParent.click();
        await page.waitForTimeout(300);
        await menuItem.click();
      }

      await page.waitForTimeout(500);

      const result = await isPageBlank(page);
      if (result.blank) {
        const jsErrors = (page as any).__errors || [];
        const netFails = (page as any).__networkFails || [];
        console.log(`\n=== BLANK at iteration ${i} ===`);
        console.log(`Reason: ${result.reason}`);
        console.log(`URL: ${page.url()}`);
        console.log(`JS errors: ${JSON.stringify(jsErrors)}`);
        console.log(`Network fails: ${JSON.stringify(netFails)}`);
        await page.screenshot({
          path: path.resolve(test.info().outputDir, `rapid-click-blank-${i}.png`),
          fullPage: true,
        });
      }

      expect(result.blank).toBe(false);
    }
  });
});
