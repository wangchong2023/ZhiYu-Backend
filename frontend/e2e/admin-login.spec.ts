import { test, expect } from '@playwright/test';

test.describe('Admin Login Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/admin/login');
  });

  test('displays login form with title', async ({ page }) => {
    await expect(page.locator('text=ZhiYu')).toBeVisible();
    await expect(page.locator('input[id="username"]')).toBeVisible();
    await expect(page.locator('input[id="password"]')).toBeVisible();
  });

  test('shows validation error for empty username', async ({ page }) => {
    await page.locator('button[type="submit"]').click();
    await expect(page.locator('.ant-form-item-explain-error')).toBeVisible();
  });

  test('switches to SMS login tab', async ({ page }) => {
    await page.locator('.ant-tabs-tab').filter({ hasText: /SMS|短信/ }).click();
    await expect(page.locator('input[id="phone"]')).toBeVisible();
  });

  test('privacy consent checkbox is required', async ({ page }) => {
    await page.locator('input[id="username"]').fill('admin');
    await page.locator('input[id="password"]').fill('password');
    await page.locator('button[type="submit"]').click();
    await expect(page.locator('.ant-form-item-explain-error')).toBeVisible();
  });

  test('submits login form with credentials and privacy consent', async ({ page }) => {
    await page.locator('input[id="username"]').fill('admin');
    await page.locator('input[id="password"]').fill('Admin123!');
    const checkbox = page.locator('input[id="privacyAgreed"]');
    await checkbox.check({ force: true });
    await page.locator('button[type="submit"]').click();
    // The request will fail (no backend), but the form should submit without
    // client-side validation errors
    await expect(page.locator('.ant-form-item-explain-error')).not.toBeVisible();
  });
});

test.describe('Admin Navigation', () => {
  test('redirects to login when not authenticated', async ({ page }) => {
    await page.goto('/admin/dashboard');
    // Should redirect to login page
    await expect(page).toHaveURL(/\/admin\/login/);
  });
});
