// Intentionally public FICTIONAL credentials. Dynamically imported only by the local dev-only demo entry.
export const demoAccounts = (__MEDBRIDGE_DEMO_SERVER__ || import.meta.env.MODE === 'test') ? [
  { label: 'Donor', name: 'Meera K', email: 'meera@medbridge.test', password: 'DemoDonor2026!' },
  { label: 'Verified NGO', name: 'Divya Rao · GreenCross Foundation', email: 'divya@medbridge.test', password: 'DemoNgo2026!' },
  { label: 'Pending NGO', name: 'HopeCare Foundation', email: 'pending@medbridge.test', password: 'DemoPending2026!' },
  { label: 'Admin', name: 'MedBridge Review Team', email: 'admin@medbridge.test', password: 'DemoAdmin2026!' },
] : [];
