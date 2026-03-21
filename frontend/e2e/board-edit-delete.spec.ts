import { test, expect } from '@playwright/test';
import { auth0Login } from './auth0-login';

async function createBoardViaUi(page: import('@playwright/test').Page) {
  await auth0Login(page);

  await page.getByRole('button', { name: 'Create a Board' }).click();

  const boardTitle = `E2E Edit Board ${Date.now()}`;
  const recipientName = 'Edit Recipient';

  await page.getByLabel('Board title').fill(boardTitle);
  await page.getByLabel('Recipient name').fill(recipientName);
  await page.getByRole('button', { name: 'Create Board' }).click();

  await expect(page).toHaveURL(/\/board\/.+/);
  await expect(page.getByRole('heading', { name: new RegExp(boardTitle) })).toBeVisible();

  return { boardTitle, recipientName };
}

test.describe('Board edit and delete', () => {
  test('owner can download board PDF from board page (async job flow)', async ({ page }) => {
    await createBoardViaUi(page);

    await expect(page.getByTestId('board-download-pdf-button')).toBeVisible();

    // Wait for the app to request the PDF (relative URL: fetch; absolute: navigation).
    // Fetch + blob path does not emit Playwright's "download" event, so assert on the response instead.
    const downloadResponsePromise = page.waitForResponse(
      (res) => res.url().includes('/pdf-jobs/') && res.url().endsWith('/download') && res.status() === 200,
      { timeout: 30000 },
    );

    await page.getByTestId('board-download-pdf-button').click();

    // Button shows spinner while job is being processed
    await expect(page.getByTestId('board-download-pdf-button')).toBeDisabled({ timeout: 5000 }).catch(() => {
      // Job may complete too fast for the spinner to be observed; that's fine
    });

    const downloadResponse = await downloadResponsePromise;
    const disposition = (downloadResponse.headers()['content-disposition'] ?? '').toLowerCase();
    expect(disposition).toMatch(/\.pdf/);
    expect((downloadResponse.headers()['content-type'] ?? '').toLowerCase()).toMatch(/application\/pdf/);
  });

  test('owner can edit and delete a board from dashboard tiles', async ({ page }) => {
    const { boardTitle } = await createBoardViaUi(page);
    await page.getByRole('link', { name: 'Back to Dashboard' }).click();

    const initialTile = page.getByTestId('dashboard-board-tile').filter({ hasText: boardTitle }).first();
    await expect(initialTile).toBeVisible();
    await expect(initialTile.getByTestId('dashboard-board-edit-button')).toBeVisible();
    await expect(initialTile.getByTestId('dashboard-board-delete-button')).toBeVisible();

    await initialTile.getByTestId('dashboard-board-edit-button').click();
    await expect(page.getByRole('heading', { name: 'Edit board' }).first()).toBeVisible();

    const newTitle = `Updated Board ${Date.now()}`;
    const newRecipient = 'Updated Recipient';

    await page.getByLabel('Board title').fill(newTitle);
    await page.getByLabel('Recipient name').fill(newRecipient);
    await page.getByTestId('save-board-button').click();

    const updatedTile = page.getByTestId('dashboard-board-tile').filter({ hasText: newTitle }).first();
    await expect(updatedTile).toBeVisible();
    await expect(page.getByTestId('dashboard-board-tile').filter({ hasText: boardTitle })).toHaveCount(0);

    await updatedTile.getByTestId('dashboard-board-delete-button').click();
    await expect(page.getByRole('heading', { name: 'Delete board' })).toBeVisible();
    await page.getByTestId('dashboard-confirm-delete-board-button').click();

    await expect(updatedTile).toHaveCount(0, { timeout: 10000 });
  });

  test('anonymous visitors cannot see edit/delete controls', async ({ page, browser }) => {
    await createBoardViaUi(page);
    const boardUrl = page.url();

    const anonContext = await browser.newContext();
    const anonPage = await anonContext.newPage();
    await anonPage.goto(boardUrl);

    await expect(anonPage.getByRole('button', { name: 'Add Post' })).toBeVisible();
    await expect(anonPage.getByTestId('board-edit-button')).toHaveCount(0);
    await expect(anonPage.getByTestId('board-delete-button')).toHaveCount(0);
    await expect(anonPage.getByTestId('board-download-pdf-button')).toHaveCount(0);

    await anonContext.close();
  });
});

