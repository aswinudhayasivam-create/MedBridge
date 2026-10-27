import { describe, it, expect, vi } from 'vitest';
import { canEnableReview, createReviewClient } from '../lib/review-api.js';

const origin='https://fictional-review.example.invalid';
const apiBaseUrl='https://fictional-api.example.invalid';
const settings={build:true,mode:'review',enabled:'true',apiBaseUrl,origin};
const capability={environment:'isolated-review',reviewEnabled:true,registrationEnabled:false,syntheticOnly:true,expiresAt:'2026-10-26T18:30:00Z'};
const now=()=>Date.parse('2026-10-25T00:00:00Z');
function response(data){return {ok:true,text:async()=>JSON.stringify(data),json:async()=>data};}
describe('explicit protected reviewer transport',()=>{
 it('requires the explicit review build, mode, flag and HTTPS origins',()=>{
  expect(canEnableReview(settings)).toBe(true);
  for(const change of [{build:false},{mode:'production'},{enabled:'false'},{origin:'http://example.invalid'},{apiBaseUrl:'http://example.invalid'},{apiBaseUrl:apiBaseUrl+'/api'},{apiBaseUrl:'https://secret@example.invalid'}])expect(canEnableReview({...settings,...change})).toBe(false);
 });
 it('allows only the exact loopback test pair during development',()=>{
  expect(canEnableReview({...settings,development:true,origin:'http://127.0.0.1:5174',apiBaseUrl:'http://127.0.0.1:18080'})).toBe(true);
  expect(canEnableReview({...settings,development:false,origin:'http://127.0.0.1:5174',apiBaseUrl:'http://127.0.0.1:18080'})).toBe(false);
 });
 it('does not send anything when disabled',async()=>{
  const fetcher=vi.fn();
  for(const options of [{enabled:false}])await expect(createReviewClient({...options,baseUrl:apiBaseUrl,fetcher,now})('/api/auth/login')).rejects.toThrow(/unavailable/);
  expect(fetcher).not.toHaveBeenCalled();
 });
 it('checks server capability before transmitting passwords or JWTs',async()=>{
  const fetcher=vi.fn().mockResolvedValue(response({...capability,registrationEnabled:true}));
  const request=createReviewClient({enabled:true,baseUrl:apiBaseUrl,fetcher,now});
  await expect(request('/api/auth/login',{method:'POST',body:{password:'fictional-fixture'}})).rejects.toThrow(/unavailable/);
  expect(fetcher).toHaveBeenCalledTimes(1);expect(fetcher.mock.calls[0][0]).toBe(apiBaseUrl+'/api/auth/review-mode');
  expect(fetcher.mock.calls[0][1]).not.toHaveProperty('body');
 });
 it('rejects expired or extended deadlines and registration routes',async()=>{
  for(const expiresAt of ['2026-10-24T00:00:00Z','2027-01-01T00:00:00Z']){
   const fetcher=vi.fn().mockResolvedValue(response({...capability,expiresAt}));
   await expect(createReviewClient({enabled:true,baseUrl:apiBaseUrl,fetcher,now})('/api/auth/me')).rejects.toThrow(/expired/);
  }
  const fetcher=vi.fn();await expect(createReviewClient({enabled:true,fetcher,now})('/api/auth/register')).rejects.toThrow(/Invalid review route/);expect(fetcher).not.toHaveBeenCalled();
 });
 it('sends only the JWT in headers and supports actual workflow responses',async()=>{
  const fetcher=vi.fn().mockResolvedValueOnce(response(capability)).mockResolvedValueOnce(response({requestId:7}));
  const request=createReviewClient({enabled:true,baseUrl:apiBaseUrl,fetcher,now});
  expect(await request('/api/demo/medicines/1/requests',{method:'POST',token:'fictional-jwt',body:{quantity:2}})).toEqual({requestId:7});
  const [url,options]=fetcher.mock.calls[1];expect(url).not.toContain("password");expect(options.credentials).toBe('omit');expect(options.cache).toBe('no-store');
  expect(options.headers).not.toHaveProperty('X-MedBridge-Review-Key');expect(options.headers.Authorization).toBe('Bearer fictional-jwt');
 });
});
