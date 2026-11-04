import { useCallback, useEffect, useState } from "react";
import { api, openFile } from "../api.js";
import { Notice, StatusBadge } from "../App.jsx";
import { Bell, FlagList, Timeline, VerifyBanner } from "./Common.jsx";

const EMPTY = {
  medicine_name: "", strength: "", category: "general", quantity: "", unit: "tablets", expiry_date: "",
  sealed: true, notes: "", batch_number: "", manufacturer: "", has_qr: "", qr_self_report: false,
  pickup_city: "", pickup_pincode: "", need_id: "",
};

export default function DonorDashboard({ token, user }) {
  const [donations, setDonations] = useState([]);
  const [needs, setNeeds] = useState([]);
  const [form, setForm] = useState({ ...EMPTY, pickup_city: user.city || "", pickup_pincode: user.pincode || "" });
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");
  const [busy, setBusy] = useState(false);
  const [codes, setCodes] = useState({});
  const [open, setOpen] = useState({});
  const [scanNote, setScanNote] = useState("");
  // scan = fields that still hold a value read from the photo (untouched since), and the name it was read for.
  const [scanState, setScan] = useState({ name: "", fields: {}, confirmed: false, cleared: "" });

  const load = useCallback(() => {
    api("/donations/mine", { token }).then((d) => setDonations(d.donations)).catch((e) => setError(e.message));
    api("/needs", { token }).then((d) => setNeeds(d.needs)).catch(() => {});
  }, [token]);
  useEffect(load, [load]);

  const DEPENDENTS = ["strength", "expiry_date", "batch_number", "manufacturer"];
  const LABEL = { strength: "strength", expiry_date: "expiry date", batch_number: "batch number", manufacturer: "manufacturer", medicine_name: "medicine name" };

  const set = (k) => (e) => {
    const value = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    let next = { ...form, [k]: value };
    let nextScan = scanState;
    if (k in scanState.fields && value !== scanState.fields[k]) {
      // The donor edited a photo-read field: it is now their own entry, no longer "from the photo".
      const { [k]: _gone, ...rest } = scanState.fields;
      nextScan = { ...nextScan, fields: rest };
    }
    if (k === "medicine_name" && scanState.name && value.trim().toLowerCase() !== scanState.name.trim().toLowerCase()) {
      // Medicine identity changed: details still holding the photo's values belong to the OLD medicine. Clear them
      // (values the donor typed themselves are kept).
      const cleared = [];
      const rest = { ...nextScan.fields };
      for (const f of DEPENDENTS) {
        if (f in rest && next[f] === rest[f]) { next[f] = ""; cleared.push(LABEL[f]); delete rest[f]; }
      }
      if (cleared.length) nextScan = { ...nextScan, fields: rest, cleared: `Cleared ${cleared.join(", ")} because they were read from the photo of "${scanState.name}" and you changed the medicine name. Enter them for the new medicine.` };
    }
    setForm(next);
    setScan(nextScan);
  };
  const scanLeft = Object.keys(scanState.fields);

  async function scan(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(""); setScanNote("Reading the label...");
    const fd = new FormData();
    fd.append("image", file);
    try {
      const d = await api("/donations/scan", { method: "POST", token, body: fd });
      const s = d.suggestions;
      const read = {};
      for (const k of ["medicine_name", "strength", "expiry_date", "batch_number", "manufacturer"]) if (s[k]) read[k] = s[k];
      setScan({ name: s.medicine_name || "", fields: read, confirmed: false, cleared: "" });
      setForm((f) => ({
        ...f,
        medicine_name: s.medicine_name || f.medicine_name,
        strength: s.strength || f.strength,
        expiry_date: s.expiry_date || f.expiry_date,
        batch_number: s.batch_number || f.batch_number,
        manufacturer: s.manufacturer || f.manufacturer,
      }));
      setScanNote(`${Object.keys(s).length ? "Filled in what could be read." : "Nothing could be read."} ${d.notice}`);
    } catch (err) { setScanNote(""); setError(err.message); }
    e.target.value = "";
  }

  async function submit(e) {
    e.preventDefault();
    setError(""); setOk(""); setBusy(true);
    try {
      const body = { ...form, quantity: Number(form.quantity), need_id: form.need_id ? Number(form.need_id) : null, scan_fields: scanLeft, scan_confirmed: scanState.confirmed };
      const d = await api("/donations", { method: "POST", token, body });
      const n = d.donation.flags.length;
      setOk(n ? `Submitted. ${n} screening flag${n > 1 ? "s" : ""} will get a closer look from an admin.` : "Donation submitted for admin review.");
      setForm({ ...EMPTY, pickup_city: user.city || "", pickup_pincode: user.pincode || "" });
      setScanNote(""); setScan({ name: "", fields: {}, confirmed: false, cleared: "" });
      load();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  const act = async (fn, msg) => { setError(""); setOk(""); try { await fn(); if (msg) setOk(msg); load(); } catch (err) { setError(err.message); } };
  const cancel = (id) => act(() => api(`/donations/${id}/cancel`, { method: "POST", token }), "Donation cancelled.");
  const confirmHandover = (id) =>
    act(() => api(`/donations/${id}/handover`, { method: "POST", token, body: { collection_code: codes[id] || "" } }), "Handover confirmed. Thank you.");

  return (
    <div>
      <VerifyBanner user={user} token={token} />
      <Bell token={token} />
      <div className="grid lg:grid-cols-5 gap-8">
        <section className="lg:col-span-2 card h-fit">
          <h2 className="font-display text-2xl font-semibold text-forest-900 mb-4">Donate medicines</h2>
          {error && <div className="mb-3"><Notice kind="error">{error}</Notice></div>}
          {ok && <div className="mb-3"><Notice kind="success">{ok}</Notice></div>}
          <div className="mb-4">
            <label className="label" htmlFor="f-label-photo">Photo of the label (optional)</label>
            <input id="f-label-photo" className="input" type="file" accept="image/png,image/jpeg" capture="environment" onChange={scan} />
            {scanNote && <p className="text-xs text-forest-600 mt-1" role="status">{scanNote}</p>}
          </div>
          <form onSubmit={submit} className="space-y-3">
            {scanState.cleared && <Notice kind="error">{scanState.cleared}</Notice>}
            {scanLeft.length > 0 && (
              <Notice>
                Read from your photo, please check against the pack: <b>{scanLeft.map((f) => LABEL[f]).join(", ")}</b>.
                Changing a field makes it your own entry.
                <label className="flex items-start gap-2 mt-2">
                  <input type="checkbox" className="h-4 w-4 mt-0.5" checked={scanState.confirmed}
                    onChange={(e) => setScan({ ...scanState, confirmed: e.target.checked })} />
                  <span>I checked these details against the pack</span>
                </label>
              </Notice>
            )}
            {needs.length > 0 && (
              <div><label className="label" htmlFor="f-need">Donating against a need? (optional)</label>
                <select id="f-need" className="input" value={form.need_id} onChange={set("need_id")}>
                  <option value="">No, just list it</option>
                  {needs.map((n) => <option key={n.id} value={n.id}>{n.organization}: {n.medicine_name} x{n.quantity}, by {n.needed_by}</option>)}
                </select>
                {form.need_id && (() => { const n = needs.find((x) => String(x.id) === String(form.need_id)); return n ? (
                  <p className="text-sm text-forest-800 mt-1">Selected: {n.organization} needs {n.medicine_name}, {n.quantity} {n.unit}, by {n.needed_by}.</p>) : null; })()}
                <p className="text-xs text-forest-500 mt-1">Choosing a need does not allocate anything. An admin still reviews first.</p></div>
            )}
            <div><label className="label" htmlFor="f-medicine-name">Medicine name</label>
              <input id="f-medicine-name" className="input" value={form.medicine_name} onChange={set("medicine_name")} required /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label" htmlFor="f-strength">Strength</label>
                <input id="f-strength" className="input" value={form.strength} onChange={set("strength")} /></div>
              <div><label className="label" htmlFor="f-category">Category</label>
                <select id="f-category" className="input" value={form.category} onChange={set("category")}>
                  <option value="general">General</option><option value="antibiotic">Antibiotic</option>
                  <option value="chronic">Chronic care</option><option value="first-aid">First aid</option>
                </select></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label" htmlFor="f-quantity">Quantity</label>
                <input id="f-quantity" className="input" type="number" min="1" value={form.quantity} onChange={set("quantity")} required /></div>
              <div><label className="label" htmlFor="f-unit">Unit</label>
                <input id="f-unit" className="input" value={form.unit} onChange={set("unit")} /></div>
            </div>
            <div><label className="label" htmlFor="f-expiry-date">Expiry date</label>
              <input id="f-expiry-date" className="input" type="date" value={form.expiry_date} onChange={set("expiry_date")} required />
              <p className="text-xs text-forest-500 mt-1">Must be at least 30 days before expiry.</p></div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label" htmlFor="f-batch">Batch number</label>
                <input id="f-batch" className="input" value={form.batch_number} onChange={set("batch_number")} /></div>
              <div><label className="label" htmlFor="f-mfr">Manufacturer</label>
                <input id="f-mfr" className="input" value={form.manufacturer} onChange={set("manufacturer")} /></div>
            </div>
            <div><label className="label" htmlFor="f-qr">Does the pack have a QR code or barcode?</label>
              <select id="f-qr" className="input" value={form.has_qr} onChange={set("has_qr")}>
                <option value="">Not sure / skip</option><option value="yes">Yes</option><option value="no">No</option>
              </select>
              {form.has_qr === "yes" && (
                <label className="flex items-center gap-2 text-sm text-forest-800 mt-2">
                  <input type="checkbox" checked={form.qr_self_report} onChange={set("qr_self_report")} className="h-4 w-4" />
                  I scanned it and the result matched the pack (my own report, not checked by MedBridge)
                </label>
              )}</div>
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label" htmlFor="f-pcity">Pickup city</label>
                <input id="f-pcity" className="input" value={form.pickup_city} onChange={set("pickup_city")} /></div>
              <div><label className="label" htmlFor="f-ppin">Pickup PIN code</label>
                <input id="f-ppin" className="input" inputMode="numeric" maxLength={6} value={form.pickup_pincode} onChange={set("pickup_pincode")} /></div>
            </div>
            <label className="flex items-center gap-2 text-sm text-forest-800">
              <input type="checkbox" checked={form.sealed} onChange={set("sealed")} className="h-4 w-4" />
              Packaging is sealed and unopened
            </label>
            <div><label className="label" htmlFor="f-notes">Notes (optional)</label>
              <textarea id="f-notes" className="input" rows="2" value={form.notes} onChange={set("notes")} /></div>
            <button className="btn-accent w-full" disabled={busy || (scanLeft.length > 0 && !scanState.confirmed)}>{busy ? "Submitting..." : "Submit donation"}</button>
          </form>
        </section>
        <section className="lg:col-span-3 space-y-4">
          <h2 className="font-display text-2xl font-semibold text-forest-900">My donations</h2>
          {donations.length === 0 && <Notice>No donations yet. Your first one is a form away.</Notice>}
          {donations.map((d) => (
            <div key={d.id} className="card hoverable anim-rise">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-display font-semibold text-forest-900">{d.medicine_name}</h3>
                    <StatusBadge status={d.status} />
                  </div>
                  <p className="text-sm text-forest-600">
                    {d.quantity} {d.unit} · expires {d.expiry_date} · {d.sealed ? "sealed" : "opened"}
                    {d.batch_number ? ` · batch ${d.batch_number}` : ""}
                  </p>
                  {d.rejection_reason && <p className="text-sm text-red-700 mt-1">Reason: {d.rejection_reason}</p>}
                  {d.status === "pending_review" && <FlagList flags={d.flags} />}
                </div>
                <div className="flex gap-2">
                  <button className="btn-ghost !py-1.5 !min-h-[36px]" aria-expanded={!!open[d.id]}
                    onClick={() => setOpen({ ...open, [d.id]: !open[d.id] })}>Timeline</button>
                  <button className="btn-ghost !py-1.5 !min-h-[36px]"
                    onClick={() => openFile(`/donations/${d.id}/receipt.pdf`, token, `medbridge-donation-${d.id}.pdf`).catch((e) => setError(e.message))}>Receipt</button>
                  {["pending_review", "approved", "requested"].includes(d.status) && (
                    <button className="btn-ghost !py-1.5 !min-h-[36px]" onClick={() => cancel(d.id)}>Cancel</button>
                  )}
                </div>
              </div>
              {d.status === "allocated" && (
                <div className="w-full mt-3">
                  <p className="text-sm text-forest-700">Allocated to <b>{d.allocated_to}</b>. When they collect, ask for their handover code and enter it here.</p>
                  <div className="flex items-center gap-2 mt-2">
                    <input className="input !w-40" aria-label={`Handover code for ${d.medicine_name}`}
                      value={codes[d.id] || ""} onChange={(e) => setCodes({ ...codes, [d.id]: e.target.value })} />
                    <button className="btn-accent !py-2" onClick={() => confirmHandover(d.id)}>Confirm handover</button>
                  </div>
                </div>
              )}
              {open[d.id] && <Timeline donationId={d.id} token={token} />}
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
