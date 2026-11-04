export default function Landing({ onStart }) {
  return (
    <div className="grid md:grid-cols-2 gap-10 items-center py-10 anim-rise">
      <div>
        <p className="badge bg-marigold-500/20 text-marigold-600 mb-4">Donation-only demo</p>
        <h1 className="font-display text-5xl md:text-6xl font-semibold text-forest-900 leading-tight">
          Good medicines should never go to waste.
        </h1>
        <p className="mt-5 text-lg text-forest-700 max-w-lg">
          MedBridge connects people with spare, unopened medicines to admin-approved NGOs -
          with safety review on every donation and a tracked handover at the end.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <button className="btn-accent attention" onClick={onStart}>Get started</button>
          <a className="btn-ghost" href="#how">How it works</a>
        </div>
      </div>
      <div id="how" className="card space-y-5 anim-rise anim-delay-2">
        {[
          ["1", "Donate", "List unopened medicines with expiry dates. Anything expired or near expiry is refused automatically."],
          ["2", "Safety review", "An admin reviews every donation before it becomes visible to NGOs."],
          ["3", "NGO request", "Approved NGOs request what their communities need."],
          ["4", "Tracked handover", "The NGO shows a one-time code and the donor confirms the handover."],
        ].map(([n, t, d]) => (
          <div key={n} className="flex gap-4 anim-rise" style={{ animationDelay: `${0.15 + Number(n) * 0.09}s` }}>
            <span className="font-display text-3xl text-marigold-500">{n}</span>
            <div>
              <h3 className="font-display font-semibold text-forest-900 text-lg">{t}</h3>
              <p className="text-forest-700 text-sm">{d}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
