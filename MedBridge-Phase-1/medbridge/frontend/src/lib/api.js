import { createApiClient } from './http.js';
export { ApiError } from './http.js';

export const AUTH_ENABLED = import.meta.env.VITE_ENABLE_AUTH_SUBMISSIONS === 'true';
export const apiRequest = createApiClient({
  enabled: AUTH_ENABLED,
  baseUrl: import.meta.env.VITE_API_BASE_URL || '',
  development: import.meta.env.DEV,
});
