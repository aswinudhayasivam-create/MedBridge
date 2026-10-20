import { describe, expect, it, vi } from 'vitest';
import { apiRequest, AUTH_ENABLED } from '../lib/api.js';

describe('preview API safety gate', () => {
  it('stays disabled unless explicitly enabled', () => {
    expect(AUTH_ENABLED).toBe(false);
  });
  it('blocks network requests even when called directly', async () => {
    const request = vi.spyOn(globalThis, 'fetch');
    await expect(apiRequest('/api/auth/register', { method: 'POST', body: { email: 'test@example.test' } }))
      .rejects.toThrow(/disabled for this preview/);
    expect(request).not.toHaveBeenCalled();
    request.mockRestore();
  });
});
