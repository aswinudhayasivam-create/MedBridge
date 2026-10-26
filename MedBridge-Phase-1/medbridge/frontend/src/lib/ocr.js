// Assisted label OCR for the fictional donation demo.
// Tesseract.js runs entirely in the donor's browser (WebAssembly): the label
// image is processed on the donor's device and is never sent to an OCR service.
// On first use the static engine and English language assets download from the
// public jsDelivr CDN; no image data travels with those requests.
// OCR only suggests candidate details. It never auto-accepts anything: the
// donor must still confirm the candidate fields, and a fictional administrator
// still reviews every submission. Any failure falls back to manual entry.

let workerPromise;

async function getWorker(onProgress) {
  if (!workerPromise) {
    workerPromise = import('tesseract.js').then(({ createWorker }) =>
      createWorker('eng', 1, {
        logger: message => {
          if (message && message.status === 'recognizing text' && typeof onProgress === 'function') {
            onProgress(message.progress);
          }
        },
      }),
    ).catch(error => { workerPromise = null; throw error; });
  }
  return workerPromise;
}

export async function recognizeLabel(imageFile, onProgress) {
  const worker = await getWorker(onProgress);
  const { data } = await worker.recognize(imageFile);
  return (data && data.text) || '';
}

const MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 };
const SKIP_NAME = /^(batch|exp|expiry|expires?|mfg|manufactured|qty|quantity|strip|tablets?|capsules?|each|contains|store|keep|dosage|use before)\b/i;

function normaliseExpiry(raw) {
  const text = String(raw || '');
  const iso = text.match(/(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (iso) {
    const y = Number(iso[1]); const m = Number(iso[2]); const d = Number(iso[3]);
    if (m >= 1 && m <= 12 && d >= 1 && d <= 31 && y >= 2000 && y <= 2100) return `${iso[1]}-${iso[2].padStart(2, '0')}-${iso[3].padStart(2, '0')}`;
    return '';
  }
  const numeric = text.match(/(\d{1,2})[\/.-](\d{2,4})/);
  let month; let year;
  if (numeric) {
    month = Number(numeric[1]); year = Number(numeric[2]);
  } else {
    const named = text.match(/\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\s'.-]*(\d{2,4})\b/i);
    if (!named) return '';
    month = MONTHS[named[1].slice(0, 3).toLowerCase()]; year = Number(named[2]);
  }
  if (year < 100) year += 2000;
  if (!month || month < 1 || month > 12 || year < 2000 || year > 2100) return '';
  return `${year}-${String(month).padStart(2, '0')}-01`;
}

export function extractCandidates(text) {
  const lines = String(text || '').split(/\r?\n/).map(line => line.replace(/\s+/g, ' ').trim()).filter(Boolean);
  const candidates = { name: '', batch: '', expiry: '' };
  const expiryLine = lines.find(line => /\b(exp|expiry|expires?|use before)\b/i.test(line));
  if (expiryLine) candidates.expiry = normaliseExpiry(expiryLine);
  const batchLine = lines.find(line => /\b(batch|b\.?\s?no\.?|lot)\b/i.test(line));
  if (batchLine) {
    const match = batchLine.match(/(?:batch|b\.?\s?no\.?|lot)[\s.a-z#:-]{0,12}?([A-Za-z0-9][A-Za-z0-9-]{3,19})/i);
    if (match) candidates.batch = match[1].toUpperCase();
  }
  const eligible = lines.filter(line => {
    const letters = line.replace(/[^A-Za-z]/g, '');
    return letters.length >= 3 && line.length <= 60 && !SKIP_NAME.test(line) && !/\blabel\b/i.test(line) && !/\d{1,2}[\/.-]\d{2}/.test(line) && !/\d{4}-\d{2}-\d{2}/.test(line);
  });
  const nameLine = eligible.find(line => /\d+\s?(mg|mcg|ml|g)\b/i.test(line)) || eligible[0];
  if (nameLine) candidates.name = nameLine.replace(/[^A-Za-z0-9 ()+\-]/g, '').replace(/\s+/g, ' ').trim().slice(0, 200);
  return candidates;
}
