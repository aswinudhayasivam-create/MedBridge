export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function normalizeOrigin(raw, development) {
  if (!raw) return '';
  let origin;
  try { origin = new URL(raw.trim().replace(/\/$/, '')); }
  catch { throw new ApiError('API origin is not a valid URL.', 0); }
  const local = ['localhost', '127.0.0.1'].includes(origin.hostname);
  if (origin.protocol !== 'https:' && !(development && local && origin.protocol === 'http:')) {
    throw new ApiError('The MedBridge API must use HTTPS.', 0);
  }
  if (origin.username || origin.password || origin.search || origin.hash || origin.pathname !== '/') {
    throw new ApiError('API base URL must contain only the origin.', 0);
  }
  return origin.origin;
}

export function createApiClient({ enabled = false, baseUrl = '', development = false, fetcher = globalThis.fetch, timeoutMs = 15000 } = {}) {
  return async function request(path, { method = 'GET', body, token, signal } = {}) {
    if (!enabled) throw new ApiError('Authentication is disabled for this preview.', 0);
    if (!path.startsWith('/api/') || path.startsWith('//')) throw new ApiError('Invalid API path.', 0);
    const url = normalizeOrigin(baseUrl, development) + path;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const onAbort = () => controller.abort();
    signal?.addEventListener('abort', onAbort, { once: true });
    try {
      const res = await fetcher(url, {
        method,
        headers: {
          Accept: 'application/json',
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal: controller.signal,
        credentials: 'omit',
        cache: 'no-store',
      });
      const text = await res.text();
      let data;
      try { data = text ? JSON.parse(text) : null; } catch { data = null; }
      if (!res.ok) {
        const fromApi = data && typeof data.error === 'string' ? data.error : null;
        throw new ApiError(fromApi?.slice(0, 240) || `Request failed (HTTP ${res.status}).`, res.status);
      }
      if (!data || typeof data !== 'object') throw new ApiError('Unexpected API response.', res.status);
      return data;
    } catch (error) {
      if (error.name === 'AbortError') throw new ApiError('Request timed out. Please try again.', 0);
      if (error instanceof ApiError) throw error;
      throw new ApiError('Unable to contact MedBridge securely. Please try again later.', 0);
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener('abort', onAbort);
    }
  };
}
