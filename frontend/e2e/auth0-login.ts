import { expect, test, type Locator, type Page } from '@playwright/test';

function hasWrongCredentialError(page: Page) {
  return page.getByText(/wrong email or password|invalid credentials/i).first();
}

async function submitAuth0Step(page: Page, fallbackInput: Locator) {
  const primarySubmit = page.getByRole('button', { name: /^(continue|log in|sign in)$/i }).first();
  const hasVisibleButton = await primarySubmit.isVisible().catch(() => false);
  if (hasVisibleButton) {
    await primarySubmit.click();
    return;
  }
  await fallbackInput.press('Enter');
}

export async function auth0Login(page: Page) {
  const email = process.env.AUTH0_E2E_EMAIL;
  const password = process.env.AUTH0_E2E_PASSWORD;
  const auth0Domain = process.env.VITE_AUTH0_DOMAIN;

  test.skip(!email || !password || !auth0Domain, 'Missing Auth0 E2E env vars');

  await page.goto('/');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await page.waitForURL(new RegExp(auth0Domain.replaceAll('.', '\\.')), { timeout: 60000 });

  const emailInput = page.locator('input[name="username"], input[name="email"], input[type="email"]').first();
  await expect(emailInput).toBeVisible({ timeout: 30000 });
  await emailInput.fill(email!);

  const passwordInput = page.locator('input[name="password"]:visible, input[type="password"]:visible').first();
  if ((await passwordInput.count()) === 0) {
    // New Auth0 Universal Login can be identifier-first: submit email, then password appears.
    await submitAuth0Step(page, emailInput);
    await expect(passwordInput).toBeVisible({ timeout: 30000 });
  }

  await passwordInput.fill(password!);
  await submitAuth0Step(page, passwordInput);

  const authFailed = await hasWrongCredentialError(page).isVisible({ timeout: 5000 }).catch(() => false);
  if (authFailed) {
    throw new Error('Auth0 login failed: wrong email or password reported by Universal Login.');
  }

  await page.waitForURL(/^http:\/\/localhost:5173\/?/, { timeout: 60000 });
  await expect(page.getByRole('button', { name: 'Create a Board' })).toBeVisible({
    timeout: 60000,
  });
}
