import { test, expect } from '@playwright/test';
import { auth0Login } from './auth0-login';

test.describe('Dashboard', () => {
  test('shows unauthenticated landing and login', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Thank You Boards', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Log in', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: /log in to get started/i })).toBeVisible();
  });

  test('after login shows boards section and create button', async ({ page }) => {
    await auth0Login(page);

    await expect(page.getByRole('heading', { name: 'Thank You Boards', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Your boards' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create a Board' })).toBeVisible();
  });

  test('keeps the dashboard session after reload', async ({ page }) => {
    await auth0Login(page);

    await page.reload();

    await expect(page.getByRole('heading', { name: 'Thank You Boards', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Your boards' })).toBeVisible({ timeout: 60000 });
    await expect(page.getByRole('button', { name: 'Create a Board' })).toBeVisible({ timeout: 60000 });
    await expect(page.getByRole('button', { name: 'Log in', exact: true })).toHaveCount(0);
  });

  test('opens Create Board modal (authenticated)', async ({ page }) => {
    await auth0Login(page);

    await page.getByRole('button', { name: 'Create a Board' }).click();

    await expect(page.getByRole('heading', { name: 'Create a Board' })).toBeVisible();
    await expect(page.getByLabel('Board title')).toBeVisible();
    await expect(page.getByLabel('Recipient name')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create Board' })).toBeVisible();
  });
});

