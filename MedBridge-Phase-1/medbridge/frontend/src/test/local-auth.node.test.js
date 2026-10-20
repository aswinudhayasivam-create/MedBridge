import test from 'node:test';
import assert from 'node:assert/strict';
import { canEnableLocalAuth, createLocalAuthClient } from '../lib/local-auth.js';
import { ApiError } from '../lib/http.js';

const local = { devServer: true, mode: 'local-auth', enabled: 'true', origin: 'http://127.0.0.1:5173' };
test('only the explicitly enabled loopback Vite development server can submit', () => {
  assert.equal(canEnableLocalAuth(local), true);
  for (const override of [{ devServer: false }, { mode: 'production' }, { enabled: 'false' },
    { origin: 'https://example.invalid' }, { origin: 'http://127.0.0.1:4173' },
    { origin: 'http://127.0.0.1.example.invalid:5173' }, { apiBaseUrl: 'https://example.invalid' }]) {
    assert.equal(canEnableLocalAuth({ ...local, ...override }), false);
  }
});
const response = (body, status = 200) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) });
test('disabled clients make no network request', async () => {
  const client = createLocalAuthClient({ enabled: false, fetcher: () => assert.fail('unexpected request') });
  await assert.rejects(client('/api/auth/register'), /disabled/);
});
test('passwords and tokens are withheld when the backend isolation handshake fails', async () => {
  for (const capability of [{}, { environment: 'production' }, { environment: 'isolated-local-auth', registrationEnabled: false }]) {
    const requests = [];
    const client = createLocalAuthClient({ enabled: true, fetcher: async (...args) => {
      requests.push(args); return response(capability);
    } });
    await assert.rejects(client('/api/auth/register', { method: 'POST', body: { password: crypto.randomUUID() } }));
    assert.equal(requests.length, 1);
    assert.equal(requests[0][0], '/api/auth/local-mode');
    assert.equal(requests[0][1].body, undefined);
    assert.equal(requests[0][1].headers.Authorization, undefined);
  }
});
test('an isolated backend permits relative API requests and safe field errors', async () => {
  const requests = [];
  const client = createLocalAuthClient({ enabled: true, fetcher: async (...args) => {
    requests.push(args);
    return requests.length === 1 ? response({ environment: 'isolated-local-auth', registrationEnabled: true })
      : response({ error: 'Validation failed', fields: { email: 'Invalid email', internal: 'private' } }, 400);
  } });
  await assert.rejects(client('/api/auth/register', { method: 'POST', body: {} }), error => {
    assert.ok(error instanceof ApiError); assert.deepEqual(error.fields, { email: 'Invalid email' }); return true;
  });
  assert.equal(requests[1][0], '/api/auth/register');
  assert.equal(requests[1][1].credentials, 'omit');
});
