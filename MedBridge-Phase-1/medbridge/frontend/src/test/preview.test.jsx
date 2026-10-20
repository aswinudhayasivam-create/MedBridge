import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext.jsx';
import App from '../App.jsx';

function view(route) {
  return render(<MemoryRouter initialEntries={[route]}><AuthProvider><App/></AuthProvider></MemoryRouter>);
}

describe('phase 3 safe preview', () => {
  it('shows homepage safety notice', () => {
    view('/');
    expect(screen.getByText(/No medicine submissions or transfers are available/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Care is meant/i })).toBeInTheDocument();
  });
  it('does not submit real registration data in preview mode', async () => {
    const request = vi.spyOn(globalThis, 'fetch');
    view('/register');
    expect(screen.getByRole('button', { name: /Create account/i })).toBeDisabled();
    expect(screen.getByText(/No account data will be sent/i)).toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
    request.mockRestore();
  });
  it('shows distinct donor and NGO static previews', () => {
    const first = view('/preview/donor');
    expect(screen.getByText('DONOR PORTAL')).toBeInTheDocument();
    expect(screen.getByText(/No records, accounts, or medicine submissions have been created/i)).toBeInTheDocument();
    first.unmount();
    view('/preview/ngo');
    expect(screen.getByText('NGO PORTAL')).toBeInTheDocument();
    expect(screen.getByText(/NGO verification workflow/)).toBeInTheDocument();
  });
  it('redirects unauthenticated dashboard visits to login', () => {
    view('/dashboard/donor');
    expect(screen.getByRole('heading', { name: /Sign in to MedBridge/ })).toBeInTheDocument();
  });
  it('can switch registration to NGO but never submit a request', async () => {
    const user = userEvent.setup();
    const request = vi.spyOn(globalThis, 'fetch');
    view('/register');
    await user.click(screen.getByRole('button', { name: /I'm an NGO/ }));
    expect(screen.getByLabelText(/Registered organization name/i)).toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
    request.mockRestore();
  });
});
