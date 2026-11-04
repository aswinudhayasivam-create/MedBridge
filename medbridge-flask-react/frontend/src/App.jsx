import { useEffect, useState } from "react";
import { api } from "./api.js";
import Landing from "./components/Landing.jsx";
import AuthPage from "./components/AuthPage.jsx";
import DonorDashboard from "./components/DonorDashboard.jsx";
import NgoDashboard from "./components/NgoDashboard.jsx";
import AdminDashboard from "./components/AdminDashboard.jsx";

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem("mb_token") || "");
  const [user, setUser] = useState(null);
  const [page, setPage] = useState(() => (new URLSearchParams(window.location.search).get("reset-password") || new URLSearchParams(window.location.search).get("verify-email") ? "auth" : "landing"));
  const [bootError, setBootError] = useState("");
  const params = new URLSearchParams(window.location.search);
  const resetToken = params.get("reset-password") || "";
  const verifyToken = params.get("verify-email") || "";

  useEffect(() => {
    if (!token) return;
    api("/auth/me", { token })
      .then((d) => setUser(d.user))
      .catch(() => {
        localStorage.removeItem("mb_token");
        setToken("");
        setBootError("Your session expired. Please sign in again.");
      });
  }, [token]);

  function onAuth(d) {
    localStorage.setItem("mb_token", d.token);
    setToken(d.token);
    setUser(d.user);
    setPage("home");
  }

  function signOut() {
    localStorage.removeItem("mb_token");
    setToken("");
    setUser(null);
    setPage("landing");
  }

  if (user) {
    const Dash =
      user.role === "admin" ? AdminDashboard : user.role === "ngo" ? NgoDashboard : DonorDashboard;
    return (
      <Shell user={user} onSignOut={signOut}>
        <Dash token={token} user={user} />
      </Shell>
    );
  }

  if (page === "auth") {
    return (
      <Shell onSignOut={null}>
        {bootError && <p className="text-center text-red-700 font-semibold mb-4">{bootError}</p>}
        <AuthPage onAuth={onAuth} onBack={() => setPage("landing")} resetToken={resetToken} verifyToken={verifyToken} />
      </Shell>
    );
  }
  return (
    <Shell onSignOut={null}>
      <Landing onStart={() => setPage("auth")} />
    </Shell>
  );
}

export function Shell({ children, user, onSignOut }) {
  const [meta, setMeta] = useState(null);
  useEffect(() => { api("/health").then(setMeta).catch(() => {}); }, []);
  return (
    <div className="min-h-screen watermark">
      <div role="note" className="bg-amber-100 text-amber-900 text-xs sm:text-sm text-center px-3 py-1.5 font-semibold">
        Test version: sample data only. Not for real medicines.
        {meta && !meta.signup_open && " Invited testers only."}
        {meta && meta.email === "off" && " Email is off: no reset or confirmation messages are sent."}
        {meta && meta.env !== "development" && " Audit anchoring is not available on this server."}
      </div>
      <header className="sticky top-0 z-10 bg-cream/90 backdrop-blur border-b border-forest-100">
        <div className="max-w-6xl mx-auto flex items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-forest-700 text-white font-display text-lg">M</span>
            <span className="font-display font-semibold text-xl text-forest-900">MedBridge</span>
          </div>
          {user && (
            <div className="flex items-center gap-3">
              <span className="text-sm text-forest-700 hidden sm:block">
                {user.name} <span className="badge bg-forest-100 text-forest-800 ml-1">{user.role}</span>
              </span>
              <button className="btn-ghost !py-1.5 !min-h-[36px]" onClick={onSignOut}>Sign out</button>
            </div>
          )}
        </div>
      </header>
      <main className="max-w-6xl mx-auto px-4 py-8 anim-fade">{children}</main>
      <footer className="text-center text-xs text-forest-500 pb-8 px-4">
        MedBridge is in development. Accounts and donations you see here are sample test data.
        Do not hand over real medicines through it: real use needs legal and pharmacy review first.
      </footer>
    </div>
  );
}

export function Notice({ kind = "info", children }) {
  const styles = {
    info: "bg-forest-50 border-forest-200 text-forest-800",
    error: "bg-red-50 border-red-200 text-red-800",
    success: "bg-green-50 border-green-200 text-green-800",
  };
  return <div className={`rounded-xl border px-4 py-3 text-sm ${styles[kind]}`}>{children}</div>;
}

export function StatusBadge({ status }) {
  const map = {
    pending_review: "bg-amber-100 text-amber-800",
    approved: "bg-green-100 text-green-800",
    rejected: "bg-red-100 text-red-800",
    requested: "bg-blue-100 text-blue-800",
    allocated: "bg-purple-100 text-purple-800",
    collected: "bg-forest-100 text-forest-800",
    cancelled: "bg-gray-200 text-gray-600",
    pending: "bg-amber-100 text-amber-800",
    withdrawn: "bg-gray-200 text-gray-600",
    expired: "bg-gray-200 text-gray-600",
    completed: "bg-forest-100 text-forest-800",
  };
  return <span className={`badge ${map[status] || "bg-gray-100 text-gray-700"}`}>{status.replaceAll("_", " ")}</span>;
}
