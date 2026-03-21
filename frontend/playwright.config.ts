import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { loadEnvFile } from 'node:process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Load frontend/.env.local so AUTH0_E2E_* and VITE_* are available in E2E tests
try {
  loadEnvFile(path.resolve(__dirname, '.env.local'));
} catch {
  // .env.local optional (e.g. in CI these come from secrets)
}

const requiredAuth0EnvVars = ['AUTH0_E2E_EMAIL', 'AUTH0_E2E_PASSWORD', 'VITE_AUTH0_DOMAIN'];
const missingAuth0EnvVars = requiredAuth0EnvVars.filter((name) => !process.env[name]);

if (process.env.CI && missingAuth0EnvVars.length > 0) {
  throw new Error(
    `Missing required Auth0 E2E env vars in CI: ${missingAuth0EnvVars.join(', ')}.`,
  );
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

