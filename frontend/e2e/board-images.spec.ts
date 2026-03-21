import { test, expect } from '@playwright/test';
import { auth0Login } from './auth0-login';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtureImagePath = path.resolve(__dirname, 'fixtures', 'tiny.png');

async function createBoardViaUi(page: import('@playwright/test').Page) {
  await auth0Login(page);

  await page.getByRole('button', { name: 'Create a Board' }).click();

  const boardTitle = `E2E Image Board ${Date.now()}`;
  const recipientName = 'E2E Recipient';

  await page.getByLabel('Board title').fill(boardTitle);
  await page.getByLabel('Recipient name').fill(recipientName);
  await page.getByRole('button', { name: 'Create Board' }).click();

  await expect(page).toHaveURL(/\/board\/.+/);
  await expect(page.getByRole('heading', { name: new RegExp(boardTitle) })).toBeVisible();
}

test.describe('Board image uploads', () => {
  test('creates a post with an uploaded image', async ({ page }) => {
    await createBoardViaUi(page);

    await page.getByRole('button', { name: 'Add Post' }).click();
    await expect(page.getByText('Add your message')).toBeVisible();

    const richTextEditor = page.locator('.tiptap');
    await richTextEditor.click();
    const messageText = `E2E image message ${Date.now()}`;
    await richTextEditor.pressSequentially(messageText);

    await page.getByPlaceholder('Your Name').fill('E2E Image User');

    // Pick a file for upload (input is visually hidden)
    await page.getByTestId('upload-image-input').setInputFiles(fixtureImagePath);

    // Wait for the preview to appear before posting
    const previewImage = page.locator('.selected-gif-preview img').first();
    await expect(previewImage).toBeVisible();

    await page.getByRole('button', { name: 'Post', exact: true }).click();

    // Verify message and author appear
    await expect(page.getByText(messageText)).toBeVisible();
    await expect(page.getByText('E2E Image User')).toBeVisible();

    // Verify an image is rendered in the post card
    const postCard = page.locator('.post-card').last();
    const postImage = postCard.locator('.post-media img').first();
    await expect(postImage).toBeVisible();

    const src = await postImage.getAttribute('src');
    expect(src).toBeTruthy();
  });
});

