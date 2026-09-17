import { execFileSync } from 'node:child_process';

export default async function globalTeardown() {
  execFileSync('docker', ['compose', '--env-file', '.env.example', '-f', 'compose.prod.yml', 'down', '--remove-orphans'], {
    cwd: '..',
    env: {
      ...process.env,
      COMPOSE_PROJECT_NAME: 'listful-thinking-gui',
      LISTFUL_PORT: process.env.LISTFUL_GUI_PORT ?? '18180',
      LISTFUL_DATA_BIND: '/tmp/listful-thinking-gui-data'
    },
    stdio: 'inherit'
  });
}
