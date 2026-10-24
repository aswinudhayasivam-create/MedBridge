import { canEnableLocalAuth, createLocalAuthClient } from './local-auth.js';
export { ApiError } from './http.js';
import { REVIEW_ENABLED, reviewRequest } from './review-api.js';

const LOCAL_AUTH_ENABLED = canEnableLocalAuth({
  devServer: __MEDBRIDGE_LOCAL_AUTH_SERVER__,
  mode: import.meta.env.MODE,
  enabled: import.meta.env.VITE_ENABLE_AUTH_SUBMISSIONS,
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '',
  origin: typeof window === 'undefined' ? '' : window.location.origin,
});
export const AUTH_ENABLED = LOCAL_AUTH_ENABLED || REVIEW_ENABLED;
export const apiRequest = REVIEW_ENABLED ? reviewRequest : createLocalAuthClient({
  enabled: LOCAL_AUTH_ENABLED,
  baseUrl: '',
  development: import.meta.env.DEV,
});
