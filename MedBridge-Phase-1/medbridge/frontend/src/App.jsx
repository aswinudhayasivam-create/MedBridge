import { Activity, HeartHandshake, ShieldCheck } from 'lucide-react';
import { motion } from 'framer-motion';

export default function App() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-background via-background to-[#092012] px-6 py-16 text-white">
      <motion.section initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-2xl rounded-card border border-border bg-surface p-8 shadow-[0_0_45px_rgba(34,197,94,0.07)] sm:p-12">
        <div className="mb-8 flex items-center gap-3 text-primary"><HeartHandshake size={34} /><span className="font-heading text-xl font-bold tracking-tight">MedBridge</span></div>
        <span className="inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs font-medium text-mint"><Activity size={14} /> Phase 1 • Project foundation</span>
        <h1 className="mt-6 font-heading text-4xl font-semibold leading-tight sm:text-5xl">Giving unused medicine a safer path to those in need.</h1>
        <p className="mt-5 leading-relaxed text-muted">Donation-only medicine redistribution. The application foundation is ready; registration, medicine scanning, matching, and dashboards will be implemented in later phases.</p>
        <div className="mt-8 flex gap-3 rounded-xl border border-border bg-surfaceRaised p-4 text-sm text-muted"><ShieldCheck className="shrink-0 text-primary" size={22} /><p>Demo environment only. Medicines must be checked by licensed professionals and handled according to applicable regulations. No buying, selling, or payment features.</p></div>
      </motion.section>
    </main>
  );
}
