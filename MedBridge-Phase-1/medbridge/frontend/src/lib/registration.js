export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateRegistration(form) {
  if (!form.name.trim() || form.name.trim().length > 120) return 'Enter your name (maximum 120 characters).';
  if (!EMAIL_RE.test(form.email.trim()) || form.email.trim().length > 190) return 'Enter a valid email address.';
  if (form.password.length < 12 || form.password.length > 72) return 'Password must contain 12–72 characters.';
  if (!['DONOR', 'NGO'].includes(form.role)) return 'Select donor or NGO.';
  if (form.role === 'NGO') {
    if (!form.orgName.trim() || !form.licenseNo.trim() || !form.serviceArea.trim()) return 'Complete all NGO organization details.';
    if (form.orgName.trim().length > 200 || form.licenseNo.trim().length > 100 || form.serviceArea.trim().length > 200) return 'NGO details exceed allowed length.';
  }
  return '';
}

export function buildRegistrationPayload(form) {
  return {
    name: form.name.trim(), email: form.email.trim().toLowerCase(), password: form.password,
    role: form.role, latitude: null, longitude: null,
    orgName: form.role === 'NGO' ? form.orgName.trim() : null,
    licenseNo: form.role === 'NGO' ? form.licenseNo.trim() : null,
    serviceArea: form.role === 'NGO' ? form.serviceArea.trim() : null,
  };
}

