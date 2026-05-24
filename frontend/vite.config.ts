import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(__dirname, '..');

function gitHash(): string {
  try {
    return execSync('git rev-parse --short HEAD', { cwd: __dirname, encoding: 'utf-8' }).trim();
  } catch {
    return 'unknown';
  }
}

function gitBranch(): string {
  try {
    return execSync('git rev-parse --abbrev-ref HEAD', { cwd: __dirname, encoding: 'utf-8' }).trim();
  } catch {
    return 'unknown';
  }
}

function appVersion(): string {
  try {
    return readFileSync(join(projectRoot, '.version'), 'utf-8').trim();
  } catch {
    return '1.0.0';
  }
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    dedupe: ['react', 'react-dom'],
    alias: {
      react: resolve(__dirname, 'node_modules/react'),
      'react-dom': resolve(__dirname, 'node_modules/react-dom'),
    },
  },
  define: {
    __APP_VERSION__: JSON.stringify(appVersion()),
    __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
    __GIT_HASH__: JSON.stringify(gitHash()),
    __GIT_BRANCH__: JSON.stringify(gitBranch()),
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
