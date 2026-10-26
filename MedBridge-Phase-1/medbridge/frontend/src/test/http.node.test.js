import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, createApiClient } from '../lib/http.js';

const fakeResponse = (value, status = 200) => ({
  ok: status >= 200 && status < 300, status,
  text: async () => JSON.stringify(value),
});

test('preview API client does not call network', async () => {
  let calls = 0;
  const request = createApiClient({ enabled: false, fetcher: async () => { calls++; } });
  await assert.rejects(request('/api/auth/register', { method: 'POST', body: { email: 'demo@example.test' } }), /disabled for this preview/);
  assert.equal(calls, 0);
});

test('enabled client sends JSON and bearer header to HTTPS API', async () => {
  let captured;
  const request = createApiClient({ enabled: true, baseUrl: 'https://api.example.test', fetcher: async (url, options) => {
    captured = { url, options };
    return fakeResponse({ id: 2, email: 'demo@example.test' });
  }});
  const data = await request('/api/auth/me', { token: 'synthetic-token-not-real' });
  assert.equal(data.id, 2);
  assert.equal(captured.url, 'https://api.example.test/api/auth/me');
  assert.equal(captured.options.headers.Authorization, 'Bearer synthetic-token-not-real');
  assert.equal(captured.options.credentials, 'omit');
});

test('enabled client sends registration JSON to the expected endpoint', async () => {
  let captured;
  const request = createApiClient({ enabled: true, baseUrl: 'https://api.example.test', fetcher: async (url, options) => {
    captured = { url, options };
    return fakeResponse({ user: { role: 'NGO', verified: false }, token: 'synthetic' }, 201);
  }});
  const result = await request('/api/auth/register', { method: 'POST', body: { role: 'NGO', email: 'ngo@example.test' } });
  assert.equal(captured.url, 'https://api.example.test/api/auth/register');
  assert.equal(captured.options.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(captured.options.body), { role: 'NGO', email: 'ngo@example.test' });
  assert.equal(result.user.verified, false);
});

test('rejects insecure remote origins and unrelated URL paths', async () => {
  const fetcher = () => { throw new Error('Should not be called'); };
  const insecure = createApiClient({ enabled: true, baseUrl: 'http://example.test', fetcher });
  await assert.rejects(insecure('/api/auth/me'), /must use HTTPS/);
  const secure = createApiClient({ enabled: true, baseUrl: 'https://example.test', fetcher });
  await assert.rejects(secure('https://other.example.test/foo'), /Invalid API path/);
});

test('401 is preserved without leaking response details', async () => {
  const request = createApiClient({ enabled: true, baseUrl: 'https://api.example.test', fetcher: async () => fakeResponse({ error: 'Unauthorized' }, 401) });
  await assert.rejects(request('/api/auth/me'), (err) => err instanceof ApiError && err.status === 401);
});
