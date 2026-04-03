import { expect, test, type Page } from "@playwright/test";

type Episode = {
  id: number;
  title: string;
  summary: string | null;
};

const sampleEpisodes: Episode[] = [
  { id: 1, title: "Alpha Episode", summary: "First summary" },
  { id: 2, title: "Beta Episode", summary: null },
];

/** Mock GET /episodes/ and GET /episodes/{id} so tests do not need FastAPI running. */
async function mockEpisodesApi(page: Page) {
  await page.route("**/*", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    if (!url.pathname.startsWith("/episodes")) {
      return route.continue();
    }
    if (req.method() !== "GET") {
      return route.continue();
    }
    if (url.pathname === "/episodes" || url.pathname === "/episodes/") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(sampleEpisodes),
      });
      return;
    }
    const m = url.pathname.match(/^\/episodes\/(\d+)$/);
    if (m) {
      const id = Number(m[1]);
      const ep = sampleEpisodes.find((e) => e.id === id);
      if (!ep) {
        await route.fulfill({ status: 404, body: "Not Found" });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(ep),
      });
      return;
    }
    return route.continue();
  });
}

test.describe("All episodes page", () => {
  test("lists mocked episodes from GET /episodes/", async ({ page }) => {
    await mockEpisodesApi(page);
    await page.goto("/");

    await expect(
      page.getByRole("heading", { name: "Episodes", exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("region", { name: "Episode list" })).toBeVisible();
    await expect(page.getByText("Alpha Episode")).toBeVisible();
    await expect(page.getByText("Beta Episode")).toBeVisible();
    await expect(page.getByText("First summary")).toBeVisible();
  });
});

test.describe("Single episode page", () => {
  test("shows episode from GET /episodes/{id}", async ({ page }) => {
    await mockEpisodesApi(page);
    await page.goto("/episodes/1");

    await expect(
      page.getByRole("heading", { name: "Episode detail", exact: true }),
    ).toBeVisible();
    await expect(page.getByText("Alpha Episode")).toBeVisible();
    await expect(page.getByText("First summary")).toBeVisible();
    await expect(
      page.getByRole("link", { name: "← All episodes" }),
    ).toBeVisible();
  });

  test("navigates from list to detail via link", async ({ page }) => {
    await mockEpisodesApi(page);
    await page.goto("/");
    await page.locator('a[href="/episodes/1"]').click();

    await expect(page).toHaveURL(/\/episodes\/1$/);
    await expect(page.getByText("Alpha Episode")).toBeVisible();
  });

  test("shows not found for unknown id", async ({ page }) => {
    await mockEpisodesApi(page);
    await page.goto("/episodes/999");

    await expect(page.getByRole("alert")).toHaveText("Episode not found.");
  });
});
