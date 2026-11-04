import { useEffect, useState } from "react";
import { api } from "../api.js";
import { Notice } from "../App.jsx";

const LABELS = {
  listed: "Listed by donor",
  admin_approved: "Admin approved the listing",
  admin_rejected: "Admin rejected the listing",
  ngo_requested: "An admin-approved NGO asked for it",
  allocated: "Admin allocated it to an NGO",
  handover_attested: "Donor confirmed the handover",
  handover_code_failed: "A wrong handover code was entered",
  code_reissued: "Admin issued a new handover code",
  cancelled: "Cancelled by donor",
  expired: "Expired before handover",
};

export function Timeline({ donationId, token }) {
  const [t, setT] = useState(null);
  const [err, setErr] = useState("");
  useEffect(() => {
    api(`/donations/${donationId}/timeline`, { token }).then(setT).catch((e) => setErr(e.message));
  }, [donationId, token]);
  if (err) return <Notice kind="error">{err}</Notice>;
  if (!t) return <p className="text-sm text-forest-500">Loading timeline...</p>;
  return (
    <div className="mt-3 border-t border-forest-100 pt-3" data-testid={`timeline-${donationId}`}>
      <ol className="space-y-2">
        {t.events.map((e, i) => (
          <li key={i} className="flex gap-3 text-sm">
            <span className="mt-1.5 h-2.5 w-2.5 rounded-full bg-forest-600 shrink-0" aria-hidden="true" />
            <span className="text-forest-800">
              {LABELS[e.label] || e.label}
              <span className="block text-xs text-forest-500">
                {new Date(e.created_at).toLocaleString()} · {e.actor_role || "system"}
              </span>
            </span>
          </li>
        ))}
      </ol>
      <p className="text-xs text-forest-500 mt-3">
        Record check: {t.integrity.ok ? "chain intact" : `PROBLEM: ${t.integrity.problem}`}. This shows later edits to
        stored events. It does not prove a medicine is genuine or that a handover physically happened.
      </p>
    </div>
  );
}

export function FlagList({ flags }) {
  if (!flags || flags.length === 0) return null;
  return (
    <ul className="mt-2 space-y-1" aria-label="Screening flags">
      {flags.map((f) => (
        <li key={f.code} className="text-xs rounded-lg bg-amber-50 border border-amber-200 text-amber-900 px-2 py-1">
          {f.text}
        </li>
      ))}
    </ul>
  );
}

export function Bell({ token }) {
  const [data, setData] = useState({ notifications: [], unread: 0 });
  const [open, setOpen] = useState(false);
  const load = () => api("/notifications", { token }).then(setData).catch(() => {});
  useEffect(() => { load(); }, [token]);
  async function toggle() {
    setOpen(!open);
    if (!open && data.unread) { await api("/notifications/read-all", { method: "POST", token }); }
  }
  return (
    <section className="mb-6">
      <button className="btn-ghost !py-1.5 !min-h-[36px]" onClick={toggle} aria-expanded={open}>
        Alerts{data.unread ? ` (${data.unread} new)` : ""}
      </button>
      {open && (
        <div className="mt-2 space-y-2">
          {data.notifications.length === 0 && <Notice>No alerts. You get an in-app alert 30 days before a listing expires.</Notice>}
          {data.notifications.map((n) => (
            <Notice key={n.id} kind={n.kind === "expired" ? "error" : "info"}>{n.message}</Notice>
          ))}
        </div>
      )}
    </section>
  );
}

export function VerifyBanner({ user, token }) {
  const [sent, setSent] = useState(false);
  if (user.email_verified) return null;
  return (
    <div className="mb-6">
      <Notice>
        Your email is not verified yet. Check your inbox for the link.{" "}
        <button className="underline font-semibold" disabled={sent}
          onClick={() => api("/auth/resend-verification", { method: "POST", token }).then(() => setSent(true))}>
          {sent ? "Sent" : "Send it again"}
        </button>
      </Notice>
    </div>
  );
}
