import { defineConfig, devices } from '@playwright/test';

const port = Number(process.env.LISTFUL_GUI_PORT ?? 18180);
const baseURL = process.env.LISTFUL_GUI_BASE_URL ?? `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './tests/gui',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  webServer: {
    command: `bash -lc 'rm -rf /tmp/listful-thinking-gui-data && mkdir -p /tmp/listful-thinking-gui-data && chmod 777 /tmp/listful-thinking-gui-data && COMPOSE_PROJECT_NAME=listful-thinking-gui LISTFUL_PORT=${port} LISTFUL_DATA_BIND=/tmp/listful-thinking-gui-data docker compose --env-file .env.example -f compose.prod.yml up --build --force-recreate'`,
    cwd: '..',
    url: `${baseURL}/api/v1/health`,
    timeout: 120_000,
    reuseExistingServer: false
  },
  globalTeardown: './tests/gui/global-teardown.ts',
  projects: [
    {
      name: 'chromium-desktop',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
