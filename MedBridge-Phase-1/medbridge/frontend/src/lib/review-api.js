import { ApiError } from './http.js';

let invitationKey = '';
export function setReviewAccessKey(value) { invitationKey = String(value || '').trim(); }
export function clearReviewAccessKey() { invitationKey = ''; }

export function canEnableReview({ build, mode, enabled, apiBaseUrl, origin, development = false }) {
  if (!build || mode !== 'review' || enabled !== 'true') return false;
  try {
    const api = new URL(apiBaseUrl), page = new URL(origin);
    if (api.username || api.password || api.search || api.hash || api.pathname !== '/') return false;
    const hosted = api.protocol === 'https:' && page.protocol === 'https:' && !['localhost', '127.0.0.1'].includes(api.hostname);
    const local = development && api.origin === 'http://127.0.0.1:18080' && page.origin === 'http://127.0.0.1:5174';
    return hosted || local;
  } catch { return false; }
}
export const REVIEW_ENABLED = canEnableReview({ build: __MEDBRIDGE_REVIEW_BUILD__, mode: import.meta.env.MODE,
  enabled: import.meta.env.VITE_ENABLE_REVIEW, apiBaseUrl: import.meta.env.VITE_REVIEW_API_ORIGIN || '',
  origin: typeof window === 'undefined' ? '' : window.location.origin, development: import.meta.env.DEV });

export function createReviewClient({ enabled, baseUrl, fetcher = globalThis.fetch, key = () => invitationKey, now = () => Date.now() }) {
  async function send(path, { method = 'GET', body, token, binary = false } = {}) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    try {
      const multipart = body instanceof FormData;
      const response = await fetcher(baseUrl + path, { method, credentials: 'omit', cache: 'no-store', signal: controller.signal,
        headers: { 'X-MedBridge-Review-Key': key(), ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(!multipart && body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
        ...(body === undefined ? {} : { body: multipart ? body : JSON.stringify(body) }) });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new ApiError(data.error || `Review request failed (${response.status}).`, response.status);
      }
      if (binary) return response.blob();
      const text = await response.text(); return text ? JSON.parse(text) : null;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError('Unable to contact the protected review API.', 0);
    } finally { clearTimeout(timer); }
  }
  return async function request(path, options = {}) {
    if (!enabled || !key()) throw new ApiError('Protected review access is unavailable. Enter the private invitation key.', 403);
    if (!/^\/api\/(?:auth\/(?:login|me)|demo\/)/.test(path) || path.includes('..') || path.includes('#') || path.includes('\\')) throw new ApiError('Invalid review route.', 400);
    const capability = await send('/api/auth/review-mode');
    if (capability?.environment !== 'isolated-review' || capability.reviewEnabled !== true || capability.registrationEnabled !== false || capability.syntheticOnly !== true
      || !Number.isFinite(Date.parse(capability.expiresAt)) || Date.parse(capability.expiresAt) <= now()
      || Date.parse(capability.expiresAt) > Date.parse('2026-11-22T18:30:00Z')) throw new ApiError('Review access has expired or is unavailable.', 403);
    return send(path, options);
  };
}
export const reviewRequest = createReviewClient({ enabled: REVIEW_ENABLED, baseUrl: import.meta.env.VITE_REVIEW_API_ORIGIN || '' });
