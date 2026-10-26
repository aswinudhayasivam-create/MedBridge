import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DemoApp from '../demo/DemoApp.jsx';
import DateEntry from '../demo/DateEntry.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { demoRequest } from '../lib/demo-api.js';
vi.mock('../context/AuthContext.jsx', () => ({ useAuth: vi.fn() }));
vi.mock('../lib/demo-api.js', () => ({ DEMO_ENABLED: true, demoRequest: vi.fn() }));
let session, summary;
function view(path='/dashboard'){return render(<MemoryRouter initialEntries={[path]}><DemoApp/></MemoryRouter>);}
beforeEach(()=>{
  vi.clearAllMocks();
  session={token:crypto.randomUUID(),user:{name:'Meera K',role:'DONOR'}};
  summary={user:{name:'Meera K',role:'DONOR',verified:false},medicines:[],requests:[],unread:0};
  useAuth.mockImplementation(()=>({session,login:vi.fn(),logout:vi.fn(),refreshProfile:vi.fn()}));
  demoRequest.mockImplementation(async path=>path==='/api/demo/summary'?summary:[]);
});
describe('isolated PDF-inspired demo screens',()=>{
  it('autofills a fictional account without submitting login',()=>{
    const login=vi.fn();useAuth.mockReturnValue({session:null,login});view('/login');
    fireEvent.click(screen.getByRole('button',{name:'Verified NGO',exact:true}));
    expect(screen.getByLabelText('Email')).toHaveValue('divya@medbridge.test');
    expect(screen.getByLabelText(/Password/,{selector:'input'})).toHaveValue('DemoNgo2026!');
    expect(login).not.toHaveBeenCalled();
  });
  it('uses returned MySQL records rather than illustrated PDF dashboard numbers',async()=>{
    summary.medicines=[{medicine_id:1,name:'Synthetic record',status:'LISTED',expiry_date:'2027-08-01'}];
    view();await screen.findByText('Synthetic record');
    expect(screen.getByText('Donations').closest('article')).toHaveTextContent('1');
    expect(screen.getByText('In review').closest('article')).toHaveTextContent('1');
    expect(screen.getByText('Simulated collections').closest('article')).toHaveTextContent('0');
  });
  it('prevents a pending NGO from entering the request UI',async()=>{
    session.user.role='NGO';summary.user={name:'HopeCare Contact',role:'NGO',verified:false};view('/browse');
    expect(await screen.findByText(/Verification is required before browsing/)).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Request',exact:true})).not.toBeInTheDocument();
    expect(demoRequest.mock.calls.some(([path])=>path.startsWith('/api/demo/browse'))).toBe(false);
  });
  it('sends discovery filters to the backend',async()=>{
    session.user.role='NGO';summary.user={name:'Divya Rao',role:'NGO',verified:true};view('/browse');
    await screen.findByRole('button',{name:'Within 5 km'});fireEvent.click(screen.getByRole('button',{name:'Within 5 km'}));
    await waitFor(()=>expect(demoRequest.mock.calls.some(([path])=>path.includes('radius=5'))).toBe(true));
  });
  it('marks only the selected notification through the API',async()=>{
    demoRequest.mockImplementation(async path=>path==='/api/demo/summary'?summary:path==='/api/demo/notifications'?[{notif_id:9,message:'Persisted fictional event',read:false,sent_at:'2026-10-24T10:00:00Z'}]:null);
    view('/notifications');fireEvent.click(await screen.findByRole('button',{name:'Mark notification 9 read'}));
    await waitFor(()=>expect(demoRequest).toHaveBeenCalledWith('/api/demo/notifications/9/read',session.token,{method:'POST'}));
  });
  it('offers a keyboard-friendly manual date fallback without inventing a date',()=>{
    const change=vi.fn();render(<DateEntry value="" onChange={change}/>);
    fireEvent.click(screen.getByRole('button',{name:'Enter date manually'}));
    expect(screen.getByPlaceholderText('YYYY-MM-DD')).toHaveAttribute('type','text');
    expect(change).not.toHaveBeenCalled();
  });
  it('does not render notification rows as listing cards while a new route loads',async()=>{
    session.user.role='NGO';summary.user={name:'Divya Rao',role:'NGO',verified:true};
    let finishBrowse;
    demoRequest.mockImplementation(async path=>{
      if(path==='/api/demo/summary')return summary;
      if(path==='/api/demo/notifications')return [{notif_id:12,message:'Only a notification',read:true}];
      if(path.startsWith('/api/demo/browse'))return new Promise(resolve=>{finishBrowse=resolve;});
      return null;
    });
    view('/notifications');await screen.findByText('Only a notification');
    fireEvent.click(screen.getByRole('navigation',{name:'Demo workspace'}).querySelector('a[href="/browse"]'));
    await waitFor(()=>expect(screen.getByRole('heading',{name:'Browse donations',exact:true})).toBeInTheDocument());
    expect(screen.queryByText('Only a notification')).not.toBeInTheDocument();
    expect(screen.queryByText('Label details confirmed by donor · assisted OCR suggestion only')).not.toBeInTheDocument();
    await waitFor(()=>expect(finishBrowse).toBeTypeOf('function'));await act(async()=>finishBrowse([]));
  });
});
