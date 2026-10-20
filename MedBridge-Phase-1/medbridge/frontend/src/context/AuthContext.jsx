import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../lib/api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Keep JWT in memory only. Refreshing the page ends the session intentionally.
  const [session, setSession] = useState(null);

  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(() => setSession(null), Math.max(0, session.expiresAt - Date.now()));
    return () => clearTimeout(timer);
  }, [session]);

  function checkUser(user) {
    if (!user || !['DONOR', 'NGO'].includes(user.role)) {
      throw Object.assign(new Error('This account cannot use the donor or NGO portal.'), { status: 401 });
    }
    return user;
  }

  async function establishSession(data) {
    if (typeof data.token !== 'string' || !data.token || !Number.isFinite(data.expiresInSeconds)
        || data.expiresInSeconds <= 0 || data.expiresInSeconds > 86400) {
      throw new Error('Unexpected authentication response.');
    }
    const user = checkUser(await apiRequest('/api/auth/me', { token: data.token }));
    setSession({ token: data.token, user, expiresAt: Date.now() + data.expiresInSeconds * 1000 });
    return user;
  }

  async function login(email, password) {
    setSession(null);
    const data = await apiRequest('/api/auth/login', { method: 'POST', body: { email, password } });
    return establishSession(data);
  }

  async function register(body) {
    setSession(null);
    const data = await apiRequest('/api/auth/register', { method: 'POST', body });
    try { return await establishSession(data); }
    catch { throw new Error('The local test account was created, but sign-in could not be confirmed. Please log in.'); }
  }

  async function refreshProfile() {
    if (!session?.token) return null;
    try {
      const user = checkUser(await apiRequest('/api/auth/me', { token: session.token }));
      setSession(current => current?.token === session.token ? { ...current, user } : current);
      return user;
    } catch (error) {
      if (error.status === 401) setSession(current => current?.token === session.token ? null : current);
      throw error;
    }
  }

  const value = useMemo(() => ({ session, login, register, logout: () => setSession(null), refreshProfile }), [session]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
