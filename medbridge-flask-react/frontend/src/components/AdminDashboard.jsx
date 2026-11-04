import { useCallback, useEffect, useState } from "react";
import { api, openFile } from "../api.js";
import { Notice, StatusBadge } from "../App.jsx";
import { FlagList } from "./Common.jsx";

export default function AdminDashboard({ token }) {
  const [counts, setCounts] = useState(null);
  const [pending, setPending] = useState([]);
  const [requests, setRequests] = useState([]);
  const [allocated, setAllocated] = useState([]);
  const [docs, setDocs] = useState([]);
  const [ngos, setNgos] = useState([]);
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");
  const [reasons, setReasons] = useState({});
  const [notes, setNotes] = useState({});

  const load = useCallback(() => {
    const g = (p, f) => api(p, { token }).then(f).catch((e) => setError(e.message));
    g("/admin/overview", (d) => setCounts(d.counts));
    g("/admin/donations?status=pending_review", (d) => setPending(d.donations));
    g("/admin/requests?status=pending", (d) => setRequests(d.requests));
    g("/admin/requests?status=approved", (d) => setAllocated(d.requests));
    g("/admin/ngo-documents?status=pending", (d) => setDocs(d.documents));
    g("/admin/ngos", (d) => setNgos(d.ngos));
  }, [token]);
  useEffect(load, [load]);

  async function run(promise, message) {
    setError(""); setOk("");
    try { await promise; setOk(message); load(); } catch (err) { setError(err.message); }
  }
  const review = (id, decision) =>
    run(api(`/admin/donations/${id}/review`, { method: "POST", token, body: { decision, reason: reasons[id], override_note: notes[id] } }), `Donation ${decision}d.`);
  const decide = (id, decision) =>
    run(api(`/admin/requests/${id}/decide`, { method: "POST", token, body: { decision } }), `Request ${decision}d.`);
  const reviewDoc = (id, decision) =>
    run(api(`/admin/ngo-documents/${id}/review`, { method: "POST", token, body: { decision, note: reasons[`d${id}`] } }),
        decision === "approve" ? "Document approved. NGO is now admin-approved (demo)." : "Document rejected.");

  return (
    <div className="space-y-10">
      {error && <Notice kind="error">{error}</Notice>}
      {ok && <Notice kind="success">{ok}</Notice>}
      {counts && (
        <section className="grid grid-cols-2 md:grid-cols-5 gap-3">
          {[["To review", counts.donations_pending_review], ["Listed", counts.donations_approved], ["Requests", counts.requests_pending],
            ["NGOs pending", counts.ngos_pending_approval], ["Collected", counts.collected]].map(([label, n]) => (
            <div key={label} className="card anim-rise text-center">
              <p className="font-display text-3xl text-forest-800">{n}</p>
              <p className="text-xs uppercase tracking-wide text-forest-500 font-semibold">{label}</p>
            </div>
          ))}
        </section>
      )}
      <section>
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-1">Donations awaiting review</h2>
        <p className="text-sm text-forest-500 mb-4">Screening flags mean a person should look closer. No flags does not mean a medicine is genuine. Check the batch against CDSCO alerts by hand.</p>
        {pending.length === 0 && <Notice>The review queue is clear.</Notice>}
        <div className="space-y-3">
          {pending.map((d) => (
            <div key={d.id} className="card hoverable anim-rise">
              <h3 className="font-display font-semibold text-forest-900">{d.medicine_name} <span className="text-sm text-forest-500">by {d.donor_name}</span></h3>
              <p className="text-sm text-forest-600">
                {d.quantity} {d.unit} · {d.category} · expires {d.expiry_date} · {d.sealed ? "sealed" : "opened"}
                · batch {d.batch_number || "not given"} · {d.manufacturer || "manufacturer not given"}
                · QR/barcode: {d.has_qr || "not stated"}{d.has_qr === "yes" ? (d.qr_self_report ? " (donor says it matched)" : "") : ""}
              </p>
              {d.notes && <p className="text-sm text-forest-500">{d.notes}</p>}
              <FlagList flags={d.flags} />
              <div className="flex flex-wrap items-center gap-2 mt-3">
                {d.flags.length > 0 && (
                  <label className="text-xs font-semibold text-forest-700">What you checked (needed to approve)
                    <input className="input !w-64 block" aria-label={`What you checked for ${d.medicine_name}`} value={notes[d.id] || ""}
                      onChange={(e) => setNotes({ ...notes, [d.id]: e.target.value })} /></label>
                )}
                <label className="text-xs font-semibold text-forest-700">Rejection reason
                  <input className="input !w-48 block" aria-label={`Rejection reason for ${d.medicine_name}`} value={reasons[d.id] || ""}
                    onChange={(e) => setReasons({ ...reasons, [d.id]: e.target.value })} /></label>
                <button className="btn-primary !py-2" onClick={() => review(d.id, "approve")}>Approve</button>
                <button className="btn-ghost !py-2" onClick={() => review(d.id, "reject")}>Reject</button>
              </div>
            </div>
          ))}
        </div>
      </section>
      <section>
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-4">Pending requests</h2>
        {requests.length === 0 && <Notice>No requests waiting.</Notice>}
        <div className="space-y-3">
          {requests.map((r) => (
            <div key={r.id} className="card hoverable anim-rise flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="font-display font-semibold text-forest-900">{r.donation?.medicine_name} <span className="text-sm text-forest-500">requested by {r.ngo_organization || r.ngo_name}</span></h3>
                {r.message && <p className="text-sm text-forest-600">{r.message}</p>}
              </div>
              <div className="flex gap-2">
                <button className="btn-primary !py-2" onClick={() => decide(r.id, "approve")}>Approve + issue code</button>
                <button className="btn-ghost !py-2" onClick={() => decide(r.id, "reject")}>Reject</button>
              </div>
            </div>
          ))}
        </div>
      </section>
      {allocated.length > 0 && (
        <section>
          <h2 className="font-display text-2xl font-semibold text-forest-900 mb-4">Allocated, awaiting handover</h2>
          <div className="space-y-3">
            {allocated.map((r) => (
              <div key={r.id} className="card flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-forest-700">{r.donation?.medicine_name} to {r.ngo_organization || r.ngo_name}</p>
                <button className="btn-ghost !py-1.5 !min-h-[36px]" onClick={() => run(api(`/admin/requests/${r.id}/reissue-code`, { method: "POST", token }), "New handover code issued.")}>
                  Reissue code
                </button>
              </div>
            ))}
          </div>
        </section>
      )}
      <section>
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-1">NGO documents to review</h2>
        <p className="text-sm text-forest-500 mb-4">A hand check of the uploaded paper. It is not an identity check, a licence check or permission to receive medicines. Cross-check the registration number and Darpan ID yourself.</p>
        {docs.length === 0 && <Notice>No documents waiting.</Notice>}
        <div className="space-y-3">
          {docs.map((d) => (
            <div key={d.id} className="card hoverable anim-rise">
              <h3 className="font-display font-semibold text-forest-900">{d.organization} <span className="text-sm text-forest-500">{d.ngo_email}</span></h3>
              <p className="text-sm text-forest-600">Registration number: {d.registration_number} · {d.filename}</p>
              <FlagList flags={d.review_flags.map((t, i) => ({ code: `r${i}`, text: t }))} />
              <div className="flex flex-wrap items-center gap-2 mt-3">
                <button className="btn-ghost !py-2" onClick={() => openFile(`/admin/ngo-documents/${d.id}/file`, token, d.filename).catch((e) => setError(e.message))}>Open document</button>
                <label className="text-xs font-semibold text-forest-700">Review note (needed to reject)
                  <input className="input !w-56 block" aria-label={`Review note for ${d.organization}`} value={reasons[`d${d.id}`] || ""}
                    onChange={(e) => setReasons({ ...reasons, [`d${d.id}`]: e.target.value })} /></label>
                <button className="btn-primary !py-2" onClick={() => reviewDoc(d.id, "approve")}>Approve document</button>
                <button className="btn-ghost !py-2" onClick={() => reviewDoc(d.id, "reject")}>Reject</button>
              </div>
            </div>
          ))}
        </div>
      </section>
      <section>
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-1">NGO accounts</h2>
        <p className="text-sm text-forest-500 mb-4">"Admin-approved (demo)" means an admin accepted the uploaded document. It is not a real registration or identity check.</p>
        <div className="space-y-3">
          {ngos.map((n) => (
            <div key={n.id} className="card hoverable anim-rise flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="font-display font-semibold text-forest-900">{n.organization || n.name}</h3>
                <p className="text-sm text-forest-600">{n.email}{n.darpan_id ? ` · Darpan ID ${n.darpan_id}` : ""}</p>
                {!n.ngo_verified && n.review_flags.map((t) => <p key={t} className="text-xs text-amber-800">{t}</p>)}
              </div>
              {n.ngo_verified ? <StatusBadge status="approved" /> : <StatusBadge status="pending" />}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
