import { AUTH_ENABLED, apiRequest, ApiError } from './api.js';

export const DEMO_ENABLED = __MEDBRIDGE_DEMO_SERVER__ && AUTH_ENABLED;

export async function demoRequest(path, token, { method = 'GET', body, binary = false } = {}) {
  if (!DEMO_ENABLED) throw new ApiError('Fictional demo workflows are disabled.', 404);
  const capability = await apiRequest('/api/auth/local-mode');
  if (capability.environment !== 'isolated-local-auth' || capability.demoEnabled !== true) {
    throw new ApiError('Start the isolated backend with its explicit demo-workflows flag.', 503);
  }
  if (!path.startsWith('/api/demo/')) throw new ApiError('Invalid demo route.', 400);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const multipart = body instanceof FormData;
    const response = await fetch(path, {
      method, credentials: 'omit', cache: 'no-store', signal: controller.signal,
      headers: { Authorization: `Bearer ${token}`, ...(!multipart && body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new ApiError(data.error || `Demo request failed (${response.status}).`, response.status, data.fields);
    }
    if (binary) return response.blob();
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  } finally { clearTimeout(timer); }
}
