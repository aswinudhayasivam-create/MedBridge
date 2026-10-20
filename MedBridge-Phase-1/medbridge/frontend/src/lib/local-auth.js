import { ApiError, createApiClient } from './http.js';

export function canEnableLocalAuth({ devServer = false, mode, enabled, apiBaseUrl = '', origin } = {}) {
  if (!devServer || mode !== 'local-auth' || enabled !== 'true' || apiBaseUrl.trim()) return false;
  try {
    const url = new URL(origin);
    return url.protocol === 'http:' && ['127.0.0.1', 'localhost'].includes(url.hostname) && url.port === '5173';
  } catch { return false; }
}

export function createLocalAuthClient(options) {
  const request = createApiClient(options);
  return async (path, configuration) => {
    if (!options.enabled) return request(path, configuration);
    // Confirm the backend's isolated profile before transmitting a password or JWT.
    let capability;
    try { capability = await request('/api/auth/local-mode'); }
    catch { throw new ApiError('Start the isolated local authentication backend before signing in.', 503); }
    if (capability.environment !== 'isolated-local-auth') {
      throw new ApiError('The backend is not the isolated local authentication environment.', 503);
    }
    if (path === '/api/auth/register' && capability.registrationEnabled !== true) {
      throw new ApiError('Registration is unavailable in this local environment.', 503);
    }
    return request(path, configuration);
  };
}
