import { useCallback, useEffect, useState } from "react";
import { api } from "../api.js";
import { Notice, StatusBadge } from "../App.jsx";
import { Bell, Timeline, VerifyBanner } from "./Common.jsx";

export default function NgoDashboard({ token, user }) {
  const [donations, setDonations] = useState([]);
  const [requests, setRequests] = useState([]);
  const [docs, setDocs] = useState([]);
  const [myNeeds, setMyNeeds] = useState([]);
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");
  const [reg, setReg] = useState("");
  const [darpan, setDarpan] = useState(user.darpan_id || "");
  const [file, setFile] = useState(null);
  const [need, setNeed] = useState({ medicine_name: "", quantity: "", needed_by: "" });
  const [open, setOpen] = useState({});
  const approved = user.ngo_verified;

  const load = useCallback(() => {
    api("/ngo/documents", { token }).then((d) => setDocs(d.documents)).catch((e) => setError(e.message));
    if (!approved) return;
    api("/donations/browse", { token }).then((d) => setDonations(d.donations)).catch((e) => setError(e.message));
    api("/requests/mine", { token }).then((d) => setRequests(d.requests)).catch((e) => setError(e.message));
    api("/needs", { token }).then((d) => setMyNeeds(d.needs.filter((n) => n.ngo_id === user.id))).catch(() => {});
  }, [token, approved, user.id]);
  useEffect(load, [load]);

  const act = async (fn, msg) => { setError(""); setOk(""); try { await fn(); setOk(msg); load(); } catch (e) { setError(e.message); } };

  const upload = (e) => {
    e.preventDefault();
    const fd = new FormData();
    fd.append("registration_number", reg);
    fd.append("darpan_id", darpan);
    fd.append("document", file);
    act(() => api("/ngo/documents", { method: "POST", token, body: fd }), "Document submitted. An admin will review it by hand.");
  };

  if (!approved) {
    return (
      <div className="space-y-6 max-w-2xl">
        <VerifyBanner user={user} token={token} />
        {error && <Notice kind="error">{error}</Notice>}
        {ok && <Notice kind="success">{ok}</Notice>}
        <Notice>Your NGO account is awaiting admin approval. An admin reviews your registration document by hand. This is a demo review, not a check against any government registry.</Notice>
        <section className="card">
          <h2 className="font-display text-xl font-semibold text-forest-900 mb-3">Registration document</h2>
          <form onSubmit={upload} className="space-y-3">
            <div><label className="label" htmlFor="f-reg">Registration number</label>
              <input id="f-reg" className="input" value={reg} onChange={(e) => setReg(e.target.value)} required /></div>
            <div><label className="label" htmlFor="f-darpan">NGO Darpan ID (optional)</label>
              <input id="f-darpan" className="input" value={darpan} onChange={(e) => setDarpan(e.target.value)} /></div>
            <div><label className="label" htmlFor="f-doc">Document (PDF, PNG or JPEG, max 2 MB)</label>
              <input id="f-doc" className="input" type="file" accept="application/pdf,image/png,image/jpeg" onChange={(e) => setFile(e.target.files?.[0] || null)} required /></div>
            <button className="btn-primary" disabled={!file}>Submit for review</button>
          </form>
          {docs.map((d) => (
            <p key={d.id} className="text-sm mt-3 text-forest-700">
              {d.filename} <StatusBadge status={d.status} />{d.review_note ? ` · ${d.review_note}` : ""}
            </p>
          ))}
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-10">
      <VerifyBanner user={user} token={token} />
      <Bell token={token} />
      {error && <Notice kind="error">{error}</Notice>}
      {ok && <Notice kind="success">{ok}</Notice>}
      <section className="card">
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-1">Post a need</h2>
        <p className="text-sm text-forest-500 mb-3">Donors can see open needs and list against them. It is a request for help, not a guarantee of supply.</p>
        <form className="grid sm:grid-cols-4 gap-3 items-end" onSubmit={(e) => {
          e.preventDefault();
          act(() => api("/needs", { method: "POST", token, body: { ...need, quantity: Number(need.quantity) } }), "Need posted.")
            .then(() => setNeed({ medicine_name: "", quantity: "", needed_by: "" }));
        }}>
          <div className="sm:col-span-2"><label className="label" htmlFor="n-name">Medicine</label>
            <input id="n-name" className="input" value={need.medicine_name} onChange={(e) => setNeed({ ...need, medicine_name: e.target.value })} required /></div>
          <div><label className="label" htmlFor="n-qty">Quantity</label>
            <input id="n-qty" className="input" type="number" min="1" value={need.quantity} onChange={(e) => setNeed({ ...need, quantity: e.target.value })} required /></div>
          <div><label className="label" htmlFor="n-by">Needed by</label>
            <input id="n-by" className="input" type="date" value={need.needed_by} onChange={(e) => setNeed({ ...need, needed_by: e.target.value })} required /></div>
          <button className="btn-primary sm:col-span-4 sm:w-fit">Post need</button>
        </form>
        {myNeeds.map((n) => (
          <p key={n.id} className="text-sm mt-3 text-forest-700">
            {n.medicine_name} x{n.quantity} by {n.needed_by}{" "}
            <button className="underline" onClick={() => act(() => api(`/needs/${n.id}/close`, { method: "POST", token }), "Need closed.")}>Close</button>
          </p>
        ))}
      </section>
      <section>
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-4">Available donations</h2>
        {donations.length === 0 && <Notice>Nothing is listed right now. New donations appear after admin review.</Notice>}
        <div className="grid md:grid-cols-2 gap-4">
          {donations.map((d) => (
            <div key={d.id} className="card hoverable anim-rise">
              <div className="flex items-center gap-2 flex-wrap">
                <h3 className="font-display font-semibold text-forest-900">{d.medicine_name}</h3>
                <StatusBadge status={d.status} />
                {d.match && <span className="badge bg-forest-100 text-forest-800">{d.match}</span>}
              </div>
              <p className="text-sm text-forest-600 mt-1">
                {d.quantity} {d.unit}{d.strength ? ` · ${d.strength}` : ""} · expires {d.expiry_date}
                {d.pickup_city ? ` · ${d.pickup_city}${d.pickup_pincode ? " " + d.pickup_pincode : ""}` : ""}
              </p>
              {d.notes && <p className="text-sm text-forest-500 mt-1">{d.notes}</p>}
              <button className="btn-primary mt-3 !py-2" onClick={() => act(() => api("/requests", { method: "POST", token, body: { donation_id: d.id } }), "Request sent to the admin team.")}>Request</button>
            </div>
          ))}
        </div>
      </section>
      <section>
        <h2 className="font-display text-2xl font-semibold text-forest-900 mb-4">My requests</h2>
        {requests.length === 0 && <Notice>You have not requested any donations yet.</Notice>}
        <div className="space-y-3">
          {requests.map((r) => (
            <div key={r.id} className="card hoverable anim-rise">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-display font-semibold text-forest-900">{r.donation?.medicine_name || `Donation #${r.donation_id}`}</h3>
                    <StatusBadge status={r.status} />
                  </div>
                  {r.status === "approved" && r.collection_code && (
                    <p className="text-sm mt-1">
                      Handover code: <span className="font-mono font-bold text-forest-800">{r.collection_code}</span>
                      <span className="block text-forest-500">Show this code to the donor when you collect. The donor confirms the handover.</span>
                    </p>
                  )}
                  {r.status === "completed" && <p className="text-sm mt-1 text-forest-600">Handover confirmed by the donor.</p>}
                </div>
                {["approved", "completed"].includes(r.status) && (
                  <button className="btn-ghost !py-1.5 !min-h-[36px]" onClick={() => setOpen({ ...open, [r.id]: !open[r.id] })}>Timeline</button>
                )}
              </div>
              {open[r.id] && <Timeline donationId={r.donation_id} token={token} />}
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
