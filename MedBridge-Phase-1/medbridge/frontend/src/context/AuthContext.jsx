import { createContext, useContext, useMemo, useState } from 'react';
import { apiRequest } from '../lib/api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Keep JWT in memory only. Refreshing the page ends the session intentionally.
  const [session, setSession] = useState(null);

  async function login(email, password) {
    const data = await apiRequest('/api/auth/login', { method: 'POST', body: { email, password } });
    if (!data.token || !data.user || !['DONOR', 'NGO'].includes(data.user.role)) {
      throw new Error('This account cannot use the Phase 3 portal.');
    }
    setSession({ token: data.token, user: data.user });
    return data.user;
  }

  async function register(body) {
    const data = await apiRequest('/api/auth/register', { method: 'POST', body });
    if (!data.token || !data.user || !['DONOR', 'NGO'].includes(data.user.role)) {
      throw new Error('Unexpected registration response.');
    }
    setSession({ token: data.token, user: data.user });
    return data.user;
  }

  async function refreshProfile() {
    if (!session?.token) return null;
    try {
      const user = await apiRequest('/api/auth/me', { token: session.token });
      setSession(current => current && { ...current, user });
      return user;
    } catch (error) {
      if (error.status === 401) setSession(null);
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
