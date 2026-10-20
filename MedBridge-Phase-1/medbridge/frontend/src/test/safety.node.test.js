import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildRegistrationPayload, validateRegistration } from '../lib/registration.js';

const safe = {
  name: ' Dev Person ', email: ' DEMO@example.test ', password: 'not-a-real-password',
  role: 'DONOR', orgName: 'Should be stripped', licenseNo: 'NotUsed', serviceArea: 'NotUsed',
};

test('donor registration sanitizes and strips NGO details', () => {
  assert.equal(validateRegistration(safe), '');
  assert.deepEqual(buildRegistrationPayload(safe), {
    name: 'Dev Person', email: 'demo@example.test', password: 'not-a-real-password', role: 'DONOR',
    latitude: null, longitude: null, orgName: null, licenseNo: null, serviceArea: null,
  });
});

test('admin public signup is blocked', () => {
  assert.match(validateRegistration({...safe, role:'ADMIN'}), /Select donor or NGO/);
});

test('too-short and too-long passwords are blocked', () => {
  for (const password of ['12345','x'.repeat(73)]) assert.match(validateRegistration({...safe,password}), /12–72/);
});

test('NGO fields are required and normalized', () => {
  assert.match(validateRegistration({...safe,role:'NGO',orgName:'',licenseNo:'',serviceArea:''}), /Complete all NGO/);
  const org={...safe,role:'NGO',orgName:' Example NGO ',licenseNo:' REG-123 ',serviceArea:' Chennai '};
  assert.equal(validateRegistration(org),'');
  assert.equal(buildRegistrationPayload(org).licenseNo,'REG-123');
});

test('invalid email and name are rejected', () => {
  assert.match(validateRegistration({...safe,email:'missing-email'}), /valid email/);
  assert.match(validateRegistration({...safe,name:'  '}), /name/);
});
