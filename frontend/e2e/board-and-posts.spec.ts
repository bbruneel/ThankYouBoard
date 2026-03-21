import { test, expect } from '@playwright/test';
import { auth0Login } from './auth0-login';

async function createBoardViaUi(page: import('@playwright/test').Page) {
  await auth0Login(page);

  await page.getByRole('button', { name: 'Create a Board' }).click();

  const boardTitle = `E2E Board ${Date.now()}`;
  const recipientName = 'E2E Recipient';

  await page.getByLabel('Board title').fill(boardTitle);
  await page.getByLabel('Recipient name').fill(recipientName);
  await page.getByRole('button', { name: 'Create Board' }).click();

  await expect(page).toHaveURL(/\/board\/.+/);
  await expect(page.getByRole('heading', { name: new RegExp(boardTitle) })).toBeVisible();
}

async function addPostViaModal(page: import('@playwright/test').Page, messageText: string, authorName: string) {
  await page.getByRole('button', { name: 'Add Post' }).click();
  await expect(page.getByText('Add your message')).toBeVisible();

  const richTextEditor = page.locator('.tiptap');
  await richTextEditor.click();
  await richTextEditor.pressSequentially(messageText);
  await page.getByPlaceholder('Your Name').fill(authorName);
  await page.getByRole('button', { name: 'Post', exact: true }).click();
}

test.describe('Board view and posts', () => {
  test('create board and land on board page', async ({ page }) => {
    await createBoardViaUi(page);
  });

  test('add post from board page (anonymous board access)', async ({ page, browser }) => {
    await createBoardViaUi(page);

    const boardUrl = page.url();

    const anonContext = await browser.newContext();
    const anonPage = await anonContext.newPage();
    await anonPage.goto(boardUrl);

    await expect(anonPage.getByRole('button', { name: 'Add Post' })).toBeVisible();
    await anonPage.getByRole('button', { name: 'Add Post' }).click();

    await expect(anonPage.getByText('Add your message')).toBeVisible();

    const messageText = `E2E message ${Date.now()}`;
    const authorName = 'E2E User';

    const richTextEditor = anonPage.locator('.tiptap');
    await richTextEditor.click();
    await richTextEditor.pressSequentially(messageText);
    await anonPage.getByPlaceholder('Your Name').fill(authorName);
    await anonPage.getByRole('button', { name: 'Post', exact: true }).click();

    await expect(anonPage.getByText(messageText)).toBeVisible();
    await expect(anonPage.getByText(authorName)).toBeVisible();

    await anonContext.close();
  });

  test('add post from board page (authenticated session also works)', async ({ page }) => {
    await createBoardViaUi(page);

    const messageText = `E2E message ${Date.now()}`;
    const authorName = 'E2E User';
    await addPostViaModal(page, messageText, authorName);

    await expect(page.getByText(messageText)).toBeVisible();
    await expect(page.getByText(authorName)).toBeVisible();
  });

  test('rejects posting when board post limit is reached', async ({ page }) => {
    await createBoardViaUi(page);

    await addPostViaModal(page, `Limit message 1 ${Date.now()}`, 'Limit User');
    await addPostViaModal(page, `Limit message 2 ${Date.now()}`, 'Limit User');
    await addPostViaModal(page, `Limit message 3 ${Date.now()}`, 'Limit User');

    await addPostViaModal(page, `Limit message 4 ${Date.now()}`, 'Limit User');
    await expect(page.getByText('Board post limit reached (3).')).toBeVisible();
  });
});

