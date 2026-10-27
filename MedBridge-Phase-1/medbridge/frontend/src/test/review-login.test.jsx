import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DemoApp from '../demo/DemoApp.jsx';
import { useAuth } from '../context/AuthContext.jsx';
vi.mock('../context/AuthContext.jsx',()=>({useAuth:vi.fn()}));
vi.mock('../lib/review-api.js',()=>({REVIEW_ENABLED:true}));
vi.mock('../lib/demo-api.js',()=>({DEMO_ENABLED:false,demoRequest:vi.fn()}));
let login;
beforeEach(()=>{vi.clearAllMocks();login=vi.fn().mockResolvedValue({});useAuth.mockReturnValue({session:null,login});render(<MemoryRouter initialEntries={['/login']}><DemoApp/></MemoryRouter>);});
it('shows separate private review entry without the public local account selectors',()=>{
 expect(screen.queryByLabelText('Private review invitation key')).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'Donor',exact:true})).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'Admin',exact:true})).not.toBeInTheDocument();
 expect(screen.getByText(/expires at the end of 22 November/)).toBeInTheDocument();
});
it('uses email and password without an invitation key and never autofills public credentials',async()=>{
 fireEvent.change(screen.getByLabelText('Email'),{target:{value:'donor@review.medbridge.invalid'}});
 fireEvent.change(screen.getByLabelText(/Password/,{selector:'input'}),{target:{value:'fictional-test-password'}});
 fireEvent.click(screen.getByRole('button',{name:'Login',exact:true}));
 await waitFor(()=>expect(login).toHaveBeenCalledWith('donor@review.medbridge.invalid','fictional-test-password'));
});

it('keeps the password accessible name stable when visibility changes without submitting',()=>{
 const password=screen.getByLabelText('Password',{exact:true});
 expect(password).toHaveAttribute('type','password');
 const show=screen.getByRole('button',{name:'Show password'});
 expect(show).toHaveAttribute('aria-pressed','false');
 fireEvent.click(show);
 expect(screen.getByLabelText('Password',{exact:true})).toBe(password);
 expect(password).toHaveAttribute('type','text');
 expect(screen.getByRole('button',{name:'Hide password'})).toHaveAttribute('aria-pressed','true');
 fireEvent.click(screen.getByRole('button',{name:'Hide password'}));
 expect(password).toHaveAttribute('type','password');
 expect(login).not.toHaveBeenCalled();
});
