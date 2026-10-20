import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '../context/AuthContext.jsx';
import * as AuthContextModule from '../context/AuthContext.jsx';
import { AuthPage } from '../pages/Auth.jsx';
import App from '../App.jsx';

vi.mock('../lib/api.js', async () => {
  const { createLocalAuthClient } = await import('../lib/local-auth.js');
  return { AUTH_ENABLED: true, apiRequest: createLocalAuthClient({ enabled: true, fetcher: (...args) => globalThis.fetch(...args) }) };
});

let account, secret, bearer, expires, rejectProfile, rejectLogin, rejectRegistration;
const reply = (body, status = 200) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) });
function Controls() {
  const { session } = useAuth(); const navigate = useNavigate();
  return <><output data-testid="session-state">{session ? 'signed in' : 'guest'}</output><button onClick={() => navigate(session?.user.role === 'NGO' ? '/dashboard/donor' : '/dashboard/ngo')}>Test cross-role route</button></>;
}
function view(route = '/register') {
  return render(<MemoryRouter initialEntries={[route]}><AuthProvider><App/><Controls/></AuthProvider></MemoryRouter>);
}
function fill(register = true, ngo = false) {
  if (ngo) fireEvent.click(screen.getByRole('button', { name: /I'm an NGO/ }));
  if (register) fireEvent.change(screen.getByLabelText(/^Full name/), { target: { value: 'Fictional Tester' } });
  fireEvent.change(screen.getByLabelText(/^Email address/), { target: { value: account.email } });
  fireEvent.change(screen.getByLabelText(/^Password/, { selector: 'input' }), { target: { value: secret } });
  if (ngo) {
    for (const [label, value] of [[/^Registered organization name/, 'Fictional Organization'], [/^License \/ registration number/, crypto.randomUUID()], [/^Service area/, 'Fictional Test Area']]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
  }
}
function submit(page) { fireEvent.submit(page.container.querySelector('form')); }
beforeEach(() => {
  secret = crypto.randomUUID(); bearer = crypto.randomUUID(); expires = 3600;
  account = { id: 1, name: 'Fictional Tester', email: `${crypto.randomUUID()}@example.invalid`, role: 'DONOR', verified: false };
  rejectProfile = rejectLogin = rejectRegistration = null;
  vi.stubGlobal('fetch', vi.fn(async (path, options) => {
    if (path === '/api/auth/local-mode') return reply({ environment: 'isolated-local-auth', registrationEnabled: true });
    if (path === '/api/auth/me') {
      expect(options.headers.Authorization).toBe(`Bearer ${bearer}`);
      return rejectProfile || reply(account);
    }
    if (path === '/api/auth/register') {
      if (rejectRegistration) return rejectRegistration;
      account.role = JSON.parse(options.body).role;
      return reply({ token: bearer, expiresInSeconds: expires, user: { ...account, role: 'ADMIN' } });
    }
    if (path === '/api/auth/login') return rejectLogin || reply({ token: bearer, expiresInSeconds: expires });
    throw new Error('Unexpected application request');
  }));
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('explicit local authentication', () => {
  it('preserves the submit redirect when the session updates before login resolves', async () => {
    let finish;
    const login = vi.fn(() => new Promise(resolve => { finish = resolve; }));
    const auth = vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({ session: null, login });
    function Destination() { const location = useLocation(); return <output>{location.state?.authMessage || 'Missing success feedback'}</output>; }
    const tree = <MemoryRouter initialEntries={['/login']}><Routes>
      <Route path="/login" element={<AuthPage/>}/>
      <Route path="/dashboard" element={<Destination/>}/>
      <Route path="/dashboard/donor" element={<Destination/>}/>
    </Routes></MemoryRouter>;
    const page = render(tree); fill(false); submit(page);
    auth.mockReturnValue({ session: { token: bearer, user: account }, login });
    page.rerender(<MemoryRouter initialEntries={['/login']}><Routes>
      <Route path="/login" element={<AuthPage/>}/>
      <Route path="/dashboard" element={<Destination/>}/>
      <Route path="/dashboard/donor" element={<Destination/>}/>
    </Routes></MemoryRouter>);
    expect(screen.getByRole('button', { name: /Please wait/ })).toBeDisabled();
    await act(async () => finish(account));
    expect(await screen.findByText('Signed in to your local test account.')).toBeInTheDocument();
  });
  it.each(['DONOR', 'NGO'])('registers a %s using the database profile, then logs out and back in', async role => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const page = view(); fill(true, role === 'NGO'); submit(page);
    expect(await screen.findByText('Local test account created.')).toBeInTheDocument();
    expect(screen.getByText(`${role} PORTAL`)).toBeInTheDocument();
    if (role === 'NGO') {
      expect(screen.getByText('Awaiting verification')).toBeInTheDocument();
      expect(screen.getByText(/NGO privileges remain restricted/)).toBeInTheDocument();
    }
    fireEvent.click(screen.getByText('Test cross-role route'));
    await waitFor(() => expect(screen.getByText(`${role} PORTAL`)).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Refresh local profile' }));
    expect(await screen.findByText('Local profile refreshed from the server.')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /Sign out/ })[0]);
    expect(screen.getByTestId('session-state')).toHaveTextContent('guest');
    page.unmount();
    const loginPage = view('/login'); fill(false); submit(loginPage);
    expect(await screen.findByText('Signed in to your local test account.')).toBeInTheDocument();
    expect(storage).not.toHaveBeenCalled();
  });
  it('validates required NGO fields before making requests', () => {
    const page = view(); fill(); fireEvent.click(screen.getByRole('button', { name: /I'm an NGO/ })); submit(page);
    expect(screen.getByRole('alert')).toHaveTextContent(/organization|registration|service/i);
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each(['Email already registered.', 'NGO registration number already registered.'])('shows duplicate errors: %s', async error => {
    rejectRegistration = reply({ error }, 409);
    const page = view(); fill(); submit(page);
    expect(await screen.findByRole('alert')).toHaveTextContent(error);
    expect(screen.getByTestId('session-state')).toHaveTextContent('guest');
  });
  it('associates backend field validation with the affected input', async () => {
    rejectRegistration = reply({ error: 'Validation failed', fields: { email: 'Invalid fictional email' } }, 400);
    const page = view(); fill(); submit(page);
    await screen.findByText('Invalid fictional email');
    expect(screen.getByLabelText(/^Email address/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/^Email address/)).toHaveAccessibleDescription('Invalid fictional email');
  });
  it('shows loading state and rejects invalid credentials without establishing a session', async () => {
    let release;
    rejectLogin = new Promise(resolve => { release = resolve; });
    const page = view('/login'); fill(false); submit(page);
    expect(screen.getByRole('button', { name: /Please wait/ })).toBeDisabled();
    await act(async () => release(reply({ error: 'Invalid email or password.' }, 401)));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
    expect(screen.getByTestId('session-state')).toHaveTextContent('guest');
  });
  it('clears the session after a rejected profile refresh', async () => {
    const page = view('/login'); fill(false); submit(page);
    await screen.findByText('Signed in to your local test account.');
    rejectProfile = reply({ error: 'Unauthorized' }, 401);
    fireEvent.click(screen.getByRole('button', { name: 'Refresh local profile' }));
    expect(await screen.findByRole('heading', { name: 'Sign in to MedBridge' })).toBeInTheDocument();
    expect(screen.getByTestId('session-state')).toHaveTextContent('guest');
  });
  it('expires in-memory sessions without persisting a token', async () => {
    expires = 1;
    const page = view('/login'); fill(false); submit(page);
    await screen.findByText('Signed in to your local test account.');
    // Install fake timers before a profile refresh re-schedules the existing expiry.
    vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: 'Refresh local profile' }));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(1100); });
    expect(screen.getByTestId('session-state')).toHaveTextContent('guest');
  });
  it('does not establish a session when /me rejects the issued JWT', async () => {
    rejectProfile = reply({ error: 'Unauthorized' }, 401);
    const page = view('/login'); fill(false); submit(page);
    expect(await screen.findByRole('alert')).toHaveTextContent('Unauthorized');
    expect(screen.getByTestId('session-state')).toHaveTextContent('guest');
  });
});
