import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DemoApp from '../demo/DemoApp.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { setReviewAccessKey } from '../lib/review-api.js';
vi.mock('../context/AuthContext.jsx',()=>({useAuth:vi.fn()}));
vi.mock('../lib/review-api.js',()=>({REVIEW_ENABLED:true,setReviewAccessKey:vi.fn()}));
vi.mock('../lib/demo-api.js',()=>({DEMO_ENABLED:false,demoRequest:vi.fn()}));
let login;
beforeEach(()=>{vi.clearAllMocks();login=vi.fn().mockResolvedValue({});useAuth.mockReturnValue({session:null,login});render(<MemoryRouter initialEntries={['/login']}><DemoApp/></MemoryRouter>);});
it('shows separate private review entry without the public local account selectors',()=>{
 expect(screen.getByLabelText('Private review invitation key')).toHaveAttribute('type','password');
 expect(screen.queryByRole('button',{name:'Donor',exact:true})).not.toBeInTheDocument();
 expect(screen.queryByRole('button',{name:'Admin',exact:true})).not.toBeInTheDocument();
 expect(screen.getByText(/expires at the end of 22 November/)).toBeInTheDocument();
});
it('uses the privately entered gate before explicit login and never autofills public credentials',async()=>{
 const gate=crypto.randomUUID()+crypto.randomUUID();
 fireEvent.change(screen.getByLabelText('Private review invitation key'),{target:{value:gate}});
 fireEvent.change(screen.getByLabelText('Email'),{target:{value:'donor@review.medbridge.invalid'}});
 fireEvent.change(screen.getByLabelText(/Password/,{selector:'input'}),{target:{value:'fictional-test-password'}});
 fireEvent.click(screen.getByRole('button',{name:'Login',exact:true}));
 await waitFor(()=>expect(login).toHaveBeenCalledWith('donor@review.medbridge.invalid','fictional-test-password'));
 expect(setReviewAccessKey).toHaveBeenCalledWith(gate);
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
