import { defineConfig, configDefaults } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command, mode, isPreview }) => ({
  define: {
    __MEDBRIDGE_REVIEW_BUILD__: JSON.stringify(mode === 'review' && process.env.VITE_ENABLE_REVIEW === 'true' && !isPreview),
    __MEDBRIDGE_LOCAL_AUTH_SERVER__: JSON.stringify(command === 'serve' && mode === 'local-auth' && !isPreview),
    __MEDBRIDGE_DEMO_SERVER__: JSON.stringify(command === 'serve' && mode === 'local-auth' && !isPreview && process.env.VITE_ENABLE_DEMO_WORKFLOWS === 'true'),
  },
  plugins: [react()],
  server: { host: '127.0.0.1', port: mode === 'review' ? 5174 : 5173, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false } } },
  test: {
    environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.js'], css: true,
    exclude: [...configDefaults.exclude, '**/*.node.test.js'],
  },
}));
