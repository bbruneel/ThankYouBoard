import { test, expect } from '@playwright/test';
import { auth0Login } from './auth0-login';

async function createBoardViaUi(page: import('@playwright/test').Page) {
  await auth0Login(page);

  await page.getByRole('button', { name: 'Create a Board' }).click();

  const boardTitle = `E2E Post Edit ${Date.now()}`;
  const recipientName = 'E2E Recipient';

  await page.getByLabel('Board title').fill(boardTitle);
  await page.getByLabel('Recipient name').fill(recipientName);
  await page.getByRole('button', { name: 'Create Board' }).click();

  await expect(page).toHaveURL(/\/board\/.+/);
  await expect(page.getByRole('heading', { name: new RegExp(boardTitle) })).toBeVisible();
}

test.describe('Post edit and delete (capability tokens)', () => {
  test('anonymous can edit/delete within session; fresh sessions cannot', async ({ page, browser }) => {
    await createBoardViaUi(page);
    const boardUrl = page.url();

    // Create a post as an anonymous visitor (posts are anonymous-create).
    const anonContext = await browser.newContext();
    const anonPage = await anonContext.newPage();
    await anonPage.goto(boardUrl);

    const originalMessage = `E2E original ${Date.now()}`;
    await anonPage.getByRole('button', { name: 'Add Post' }).click();
    await expect(anonPage.getByText('Add your message')).toBeVisible();
    const richTextEditor = anonPage.locator('.tiptap');
    await richTextEditor.click();
    await richTextEditor.pressSequentially(originalMessage);
    await anonPage.getByPlaceholder('Your Name').fill('Anonymous Poster');
    await anonPage.getByRole('button', { name: 'Post', exact: true }).click();
    await expect(anonPage.getByText(originalMessage)).toBeVisible();

    // Anonymous should see edit/delete controls for their own posts (sessionStorage capability token).
    const anonPostCard = anonPage.locator('.post-card').filter({ hasText: originalMessage }).first();
    await expect(anonPostCard.getByTestId('post-edit-button')).toHaveCount(1);
    await expect(anonPostCard.getByTestId('post-delete-button')).toHaveCount(1);

    // A fresh anonymous browser context should NOT have the token, so controls should be hidden.
    const anonContext2 = await browser.newContext();
    const anonPage2 = await anonContext2.newPage();
    await anonPage2.goto(boardUrl);
    const anonPostCard2 = anonPage2.locator('.post-card').filter({ hasText: originalMessage }).first();
    await expect(anonPostCard2.getByTestId('post-edit-button')).toHaveCount(0);
    await expect(anonPostCard2.getByTestId('post-delete-button')).toHaveCount(0);
    await anonContext2.close();

    await anonPostCard.getByTestId('post-edit-button').click();
    await expect(anonPage.getByRole('heading', { name: 'Edit post' })).toBeVisible();

    const updatedMessage = `E2E updated ${Date.now()}`;
    const editor = anonPage.locator('.tiptap').first();
    await editor.click();
    // Clear existing content; TipTap renders as contenteditable, so use Ctrl+A + Backspace
    await editor.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A');
    await editor.press('Backspace');
    await editor.pressSequentially(updatedMessage);
    await anonPage.getByTestId('save-post-button').click();

    await expect(anonPage.getByText(updatedMessage)).toBeVisible();

    // Delete flow
    const updatedCard = anonPage.locator('.post-card').filter({ hasText: updatedMessage }).first();
    await updatedCard.getByTestId('post-delete-button').click();
    await expect(anonPage.getByRole('heading', { name: 'Delete post' })).toBeVisible();
    await anonPage.getByTestId('confirm-delete-post-button').click();

    await expect(anonPage.getByText(updatedMessage)).toHaveCount(0);

    await anonContext.close();
  });
});

