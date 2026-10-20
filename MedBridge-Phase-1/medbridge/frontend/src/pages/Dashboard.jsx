import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Activity, ArrowLeft, ArrowRight, Bell, CheckCircle2, ClipboardList, Clock3, HandHeart, HeartHandshake, LockKeyhole, ShieldCheck, UserRound, UsersRound } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import { AUTH_ENABLED } from '../lib/api.js';

function DashboardContent({ role, preview, user, signOut, refreshProfile }) {
  const location = useLocation();
  const [profileMessage, setProfileMessage] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  async function refresh() {
    setRefreshing(true);
    try { await refreshProfile(); setProfileMessage('Local profile refreshed from the server.'); }
    catch (error) { setProfileMessage(error.message); }
    finally { setRefreshing(false); }
  }
  const isNgo=role==='NGO';
  const verified=Boolean(user?.verified);
  return <main className="dashboard-page">
    <div className="container dashboard-container">
      <div className="workspace-back"><Link to="/"><ArrowLeft size={16}/> Back to MedBridge</Link><span>Platform workspace · {preview?'Preview':'Private account'}</span></div>
      {preview && <div className="workspace-preview-banner" role="status"><ShieldCheck size={19}/><span><strong>Preview workspace:</strong> This is a static demonstration. No records, accounts, or medicine submissions have been created.</span></div>}
      {!preview && AUTH_ENABLED && (location.state?.authMessage || profileMessage) && <div className="workspace-preview-banner" role="status">{profileMessage || location.state.authMessage}</div>}
      <div className="workspace-shell"><aside className="workspace-sidebar">
        <Link to="/" className="workspace-logo"><span><HeartHandshake size={24}/></span> MedBridge</Link>
        <div className="side-title">WORKSPACE</div>
        <div className="side-link active"><Activity size={18}/> Overview</div>
        <div className="side-link disabled"><ClipboardList size={18}/> {isNgo?'Organization review':'Medicine submissions'} <span>soon</span></div>
        <div className="side-link disabled"><Bell size={18}/> Notifications <span>soon</span></div>
        <div className="sidebar-bottom"><ShieldCheck size={17}/> Built for safe, accountable care</div>
      </aside>
      <div className="workspace-main"><div className="workspace-topline"><div><span className="workspace-kicker">{isNgo?'NGO PORTAL':'DONOR PORTAL'}</span><h1>{preview?'Explore your future workspace':`Welcome${user?.name?', '+user.name.split(' ')[0]:''}`}</h1><p>{isNgo?'A secure space for organization verification and approved participation.':'A clear place to follow safety information and future donation steps.'}</p></div><div className="workspace-avatar">{isNgo?<UsersRound size={24}/>:<UserRound size={24}/>}</div></div>
        <div className="workspace-card-grid"><article className="workspace-status-card"><div className="status-card-icon"><ShieldCheck size={26}/></div><span className="small-label">ACCOUNT STATUS</span><h2>{preview?'Preview only':isNgo?verified?'Verified organization':'Awaiting verification':'Account created'}</h2><p>{preview?'A real account is required before account features can be used.':isNgo?verified?'Your account is marked as verified. Review workflows remain unavailable until the platform launches.':'NGO privileges remain restricted until verification is completed.':'Your account is available. Medicine features are not yet accepting submissions.'}</p><span className="state-pill"><Clock3 size={14}/>{preview?'No active account':isNgo?(verified?'Verified':'Verification pending'):'Platform preview'}</span></article>
        <article className="workspace-accent-card"><span className="small-label">YOUR SPACE</span><div className="workspace-accent-icon">{isNgo?<UsersRound size={30}/>:<HandHeart size={30}/>}</div><h2>{isNgo?'Organizations making a difference':'Every act of care matters'}</h2><p>{isNgo?'Verification will be a core part of the NGO experience.':'The next release will guide donors through safe eligibility checks.'}</p><span>MedBridge Phase 3 <ArrowRight size={15}/></span></article></div>
        <section className="workspace-section"><div className="workspace-section-header"><div><span className="workspace-kicker">COMING NEXT</span><h2>What happens from here?</h2></div><span className="soft-tag">Designed around safety</span></div><div className="checklist"><div className="checklist-row"><span className="checklist-symbol"><CheckCircle2 size={22}/></span><div><strong>{preview?'Explore the interface':'Secure account access'}</strong><p>{preview?'You can safely explore these screens without signing up.':'Your login is protected by token-based authentication.'}</p></div><span className="checklist-state">{preview?'Preview':'Foundation'}</span></div><div className="checklist-row"><span className="checklist-symbol soon"><Clock3 size={22}/></span><div><strong>{isNgo?'NGO verification workflow':'Medicine screening workflow'}</strong><p>{isNgo?'Compliance and identity review must be completed before privileged access.':'Medicine handling will remain unavailable pending professional and regulatory review.'}</p></div><span className="checklist-state">Planned</span></div><div className="checklist-row"><span className="checklist-symbol soon"><LockKeyhole size={22}/></span><div><strong>Approved participation</strong><p>No redistribution or transfers can be initiated through this release.</p></div><span className="checklist-state">Restricted</span></div></div></section>
        <div className="workspace-info"><ShieldCheck size={21}/><p>MedBridge does not accept medicines, payments, patient information, or transfer requests through this preview. Please do not send physical medicines to any address.</p></div>
        <div className="workspace-actions">{!preview && AUTH_ENABLED && <button type="button" className="btn btn-quiet" onClick={refresh} disabled={refreshing}>{refreshing ? 'Refreshing…' : 'Refresh local profile'}</button>}{preview?<Link to="/register" className="btn btn-dark">Explore registration <ArrowRight size={16}/></Link>:<button type="button" className="btn btn-dark" onClick={signOut}>Sign out <ArrowRight size={16}/></button>}<Link to="/" className="btn btn-quiet">Back to homepage</Link></div>
      </div></div>
    </div>
  </main>;
}

export function DashboardPreview() {
  const { role } = useParams();
  if (!['donor','ngo'].includes(role)) return <Navigate to="/" replace/>;
  return <DashboardContent role={role.toUpperCase()} preview/>;
}

export function Dashboard() {
  const { session, logout, refreshProfile } = useAuth();
  const { role } = useParams();
  const navigate = useNavigate();
  if (!session) return <Navigate to="/login" replace/>;
  if (!['DONOR','NGO'].includes(session.user.role)) return <Navigate to="/" replace/>;
  if (role && role.toUpperCase() !== session.user.role) return <Navigate to={`/dashboard/${session.user.role.toLowerCase()}`} replace/>;
  return <DashboardContent role={session.user.role} user={session.user} refreshProfile={refreshProfile} signOut={() => { logout(); navigate('/'); }}/ >;
}
