import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext.jsx';
import App from '../App.jsx';

function view(route) {
  return render(<MemoryRouter initialEntries={[route]}><AuthProvider><App/></AuthProvider></MemoryRouter>);
}

describe('final QA safeguards and interactions', () => {
  it.each(['login', 'donor registration', 'NGO registration'])('blocks forced %s submission without network access', async (mode) => {
    const user = userEvent.setup();
    const request = vi.spyOn(globalThis, 'fetch');
    const page = view(mode === 'login' ? '/login' : '/register');
    if (mode === 'NGO registration') await user.click(screen.getByRole('button', { name: /I'm an NGO/ }));
    expect(page.container.querySelector('button[type="submit"]')).toBeDisabled();
    fireEvent.submit(page.container.querySelector('form'));
    expect(screen.getByRole('alert')).toHaveTextContent(/No information has been sent/);
    expect(request).not.toHaveBeenCalled();
    request.mockRestore();
  });

  it.each(['/dashboard', '/dashboard/donor', '/dashboard/ngo'])('redirects guests at %s without calling the API', (route) => {
    const request = vi.spyOn(globalThis, 'fetch');
    view(route);
    expect(screen.getByRole('heading', { name: 'Sign in to MedBridge' })).toBeInTheDocument();
    expect(screen.queryByText('ACCOUNT STATUS')).not.toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
    request.mockRestore();
  });

  it.each(['/login', '/register'])('toggles password visibility on %s without submitting', async (route) => {
    const user = userEvent.setup();
    const request = vi.spyOn(globalThis, 'fetch');
    view(route);
    const password = screen.getByLabelText(/^Password/, { selector: 'input' });
    await user.type(password, 'synthetic-qa-only');
    expect(password).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password).toHaveAttribute('type', 'text');
    expect(password).toHaveValue('synthetic-qa-only');
    await user.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(password).toHaveAttribute('type', 'password');
    expect(request).not.toHaveBeenCalled();
    request.mockRestore();
  });

  it('closes mobile navigation on Escape and restores toggle focus', async () => {
    const user = userEvent.setup();
    view('/');
    const toggle = screen.getByRole('button', { name: 'Open navigation' });
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    const nav = screen.getByRole('navigation', { name: 'Main navigation' });
    const target = screen.getByRole('link', { name: 'Get started' });
    target.focus();
    await user.keyboard('{Escape}');
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(toggle).toHaveFocus();
    expect(toggle).toHaveAttribute('aria-controls', nav.id);
    expect(nav.id).not.toBe('');
  });

  it('closes navigation after following a page link', async () => {
    const user = userEvent.setup();
    view('/');
    await user.click(screen.getByRole('button', { name: 'Open navigation' }));
    await user.click(screen.getByRole('link', { name: 'Get started' }));
    expect(screen.getByRole('heading', { name: 'Join the MedBridge journey' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open navigation' })).toHaveAttribute('aria-expanded', 'false');
  });

  it.each([['donor', 'DONOR PORTAL'], ['NGO', 'NGO PORTAL']])('opens the %s preview from the homepage', async (role, title) => {
    const user = userEvent.setup();
    view('/');
    await user.click(screen.getByRole('link', { name: `Explore ${role} dashboard` }));
    expect(screen.getByText(title)).toBeInTheDocument();
    expect(screen.getByText(/No records, accounts, or medicine submissions have been created/)).toBeInTheDocument();
  });

  it('redirects unsupported preview roles to the homepage', () => {
    view('/preview/admin');
    expect(screen.getByRole('heading', { name: /Care is meant/ })).toBeInTheDocument();
  });
});
