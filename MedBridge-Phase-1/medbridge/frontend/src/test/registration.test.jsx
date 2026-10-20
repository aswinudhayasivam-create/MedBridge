import { describe, expect, it } from 'vitest';
import { buildRegistrationPayload, validateRegistration } from '../lib/registration.js';

const donor = { name: ' Example Donor ', email: ' Donor@Example.test ', password: 'example-pass-123', role: 'DONOR', orgName: 'Ignored NGO', licenseNo: 'Ignored', serviceArea: 'Ignored' };

describe('safe registration payload', () => {
  it('normalizes donor identity and omits NGO-only details', () => {
    expect(validateRegistration(donor)).toBe('');
    expect(buildRegistrationPayload(donor)).toEqual({
      name: 'Example Donor', email: 'donor@example.test', password: 'example-pass-123', role: 'DONOR',
      latitude: null, longitude: null, orgName: null, licenseNo: null, serviceArea: null,
    });
  });
  it('blocks admin registration', () => {
    expect(validateRegistration({ ...donor, role: 'ADMIN' })).toMatch(/Select donor or NGO/);
  });
  it('enforces the server password limit', () => {
    expect(validateRegistration({ ...donor, password: 'weak' })).toMatch(/12–72/);
    expect(validateRegistration({ ...donor, password: 'a'.repeat(73) })).toMatch(/12–72/);
  });
  it('requires NGO verification details', () => {
    expect(validateRegistration({ ...donor, role: 'NGO', orgName: '', licenseNo: '', serviceArea: '' })).toMatch(/organization details/);
    const ngo = { ...donor, role: 'NGO', orgName: ' Charity ', licenseNo: ' REG-01 ', serviceArea: ' Chennai ' };
    expect(validateRegistration(ngo)).toBe('');
    expect(buildRegistrationPayload(ngo)).toMatchObject({ orgName: 'Charity', licenseNo: 'REG-01', serviceArea: 'Chennai', role: 'NGO' });
  });
});
