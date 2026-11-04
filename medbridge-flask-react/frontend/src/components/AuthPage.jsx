import { useState } from "react";
import { api } from "../api.js";
import { Notice } from "../App.jsx";

export default function AuthPage({ onAuth, onBack, resetToken, verifyToken }) {
  const [mode, setMode] = useState(resetToken ? "reset" : "login");
  const [form, setForm] = useState({ name: "", email: "", password: "", role: "donor", organization: "", city: "", pincode: "" });
  const [error, setError] = useState("");
  const [info, setInfo] = useState(verifyToken ? "Confirming your email..." : "");
  const [busy, setBusy] = useState(false);

  useState(() => {
    if (verifyToken) {
      api("/auth/verify-email", { method: "POST", body: { token: verifyToken } })
        .then(() => setInfo("Email confirmed. You can sign in."))
        .catch((e) => { setInfo(""); setError(e.message); });
    }
  });

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setError(""); setInfo(""); setBusy(true);
    try {
      if (mode === "forgot") {
        const d = await api("/auth/forgot-password", { method: "POST", body: { email: form.email } });
        setInfo(d.message);
      } else if (mode === "reset") {
        await api("/auth/reset-password", { method: "POST", body: { token: resetToken, password: form.password } });
        setInfo("Password changed. Sign in with the new one.");
        setMode("login");
      } else {
        const d = mode === "login"
          ? await api("/auth/login", { method: "POST", body: { email: form.email, password: form.password } })
          : await api("/auth/register", { method: "POST", body: form });
        onAuth(d);
      }
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  const tabs = ["login", "register"];
  return (
    <div className="max-w-md mx-auto card anim-rise">
      {(mode === "login" || mode === "register") && (
        <div className="flex gap-2 mb-6">
          {tabs.map((m) => (
            <button key={m} onClick={() => setMode(m)}
              className={`flex-1 rounded-full py-2 font-semibold ${mode === m ? "bg-forest-700 text-white" : "bg-forest-50 text-forest-700"}`}>
              {m === "login" ? "Sign in" : "Create account"}
            </button>
          ))}
        </div>
      )}
      {mode === "forgot" && <h2 className="font-display text-xl font-semibold text-forest-900 mb-4">Reset your password</h2>}
      {mode === "reset" && <h2 className="font-display text-xl font-semibold text-forest-900 mb-4">Choose a new password</h2>}
      {error && <div className="mb-4"><Notice kind="error">{error}</Notice></div>}
      {info && <div className="mb-4"><Notice kind="success">{info}</Notice></div>}
      <form onSubmit={submit} className="space-y-4">
        {mode === "register" && (
          <>
            <div><label className="label" htmlFor="f-full-name">Full name</label>
              <input id="f-full-name" className="input" value={form.name} onChange={set("name")} required /></div>
            <div><label className="label" htmlFor="f-i-am-a">I am a</label>
              <select id="f-i-am-a" className="input" value={form.role} onChange={set("role")}>
                <option value="donor">Donor</option><option value="ngo">NGO</option>
              </select></div>
            {form.role === "ngo" && (
              <div><label className="label" htmlFor="f-organization-name">Organization name</label>
                <input id="f-organization-name" className="input" value={form.organization} onChange={set("organization")} required />
                <p className="text-xs text-forest-500 mt-1">After signing up, upload your registration document. An admin reviews it by hand (demo step, not a government registry check).</p></div>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div><label className="label" htmlFor="f-city">City</label>
                <input id="f-city" className="input" value={form.city} onChange={set("city")} /></div>
              <div><label className="label" htmlFor="f-pincode">PIN code</label>
                <input id="f-pincode" className="input" inputMode="numeric" maxLength={6} value={form.pincode} onChange={set("pincode")} /></div>
            </div>
          </>
        )}
        {mode !== "reset" && (
          <div><label className="label" htmlFor="f-email">Email</label>
            <input id="f-email" className="input" type="email" value={form.email} onChange={set("email")} required /></div>
        )}
        {mode !== "forgot" && (
          <div><label className="label" htmlFor="f-password">{mode === "reset" ? "New password" : "Password"}{mode !== "login" && " (8+ characters)"}</label>
            <input id="f-password" className="input" type="password" value={form.password} onChange={set("password")} required minLength={mode === "login" ? 1 : 8} /></div>
        )}
        <button className="btn-primary w-full" disabled={busy}>
          {busy ? "Please wait..." : { login: "Sign in", register: "Create account", forgot: "Send reset link", reset: "Change password" }[mode]}
        </button>
        {mode === "login" && (
          <button type="button" className="text-sm underline text-forest-700 w-full" onClick={() => { setMode("forgot"); setError(""); setInfo(""); }}>
            Forgot your password?
          </button>
        )}
        <button type="button" className="btn-ghost w-full" onClick={mode === "forgot" ? () => setMode("login") : onBack}>Back</button>
      </form>
    </div>
  );
}
