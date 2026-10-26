import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractCandidates } from '../lib/ocr.js';

test('extracts name, batch and numeric expiry from synthetic label text', () => {
  const found = extractCandidates('PARACETAMOL 500mg Tablets\nBatch No: AB12CD34\nMfg 01/2026\nExp 06/2028\nStore cool and dry');
  assert.equal(found.name, 'PARACETAMOL 500mg Tablets');
  assert.equal(found.batch, 'AB12CD34');
  assert.equal(found.expiry, '2028-06-01');
});
test('accepts B.No phrasing and month-name expiry', () => {
  const found = extractCandidates('Amoxicillin 250mg\nB.No: XYZ-900\nUse before Mar 2028');
  assert.equal(found.batch, 'XYZ-900');
  assert.equal(found.expiry, '2028-03-01');
});
test('returns blanks for unreadable text and never invents values', () => {
  const found = extractCandidates('@@## 111 ***');
  assert.deepEqual(found, { name: '', batch: '', expiry: '' });
});
test('rejects impossible dates instead of guessing', () => {
  assert.equal(extractCandidates('Vitamin C\nExp 13/2099').expiry, '');
  assert.equal(extractCandidates('Vitamin C\nExp 02/99').expiry, '2099-02-01');
});
test('parses the bundled synthetic label wording exactly', () => {
  const found = extractCandidates('SYNTHETIC DEMO LABEL - NOT FOR MEDICAL USE\nParacetamol 500 mg | Fictional tablets\nBatch: DEMO-2026 | Expiry: 2027-08-01\nNo active medicine. No patient information.');
  assert.equal(found.name, 'Paracetamol 500 mg  Fictional tablets'.replace(/\s+/g, ' '));
  assert.equal(found.batch, 'DEMO-2026');
  assert.equal(found.expiry, '2027-08-01');
});
test('suggestions only: result carries no confirmation flag', () => {
  const found = extractCandidates('PARACETAMOL\nBatch: AB1234\nExp 06/2028');
  assert.equal('photoConfirmed' in found, false);
  assert.equal('confirmed' in found, false);
});
