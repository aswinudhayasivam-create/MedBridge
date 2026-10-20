import { useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, Eye, EyeOff, HandHeart, HeartHandshake, LockKeyhole, ShieldCheck, UsersRound } from 'lucide-react';
import { AUTH_ENABLED } from '../lib/api.js';
import { useAuth } from '../context/AuthContext.jsx';

import { EMAIL_RE, validateRegistration, buildRegistrationPayload } from '../lib/registration.js';

function AuthAside({ register }) { return <aside className="auth-aside"><span className="auth-aside-icon"><HeartHandshake size={32}/></span><div className="eyebrow light-eyebrow">Made for meaningful connections</div><h2>{register ? 'A safer way forward begins with you.' : 'Welcome back to the bridge.'}</h2><p>Explore MedBridge's people-first approach to donation-only medicine redistribution.</p><div className="auth-aside-points"><div><ShieldCheck size={19}/> A security-minded experience</div><div><LockKeyhole size={19}/> Verification before privileges</div><div><HandHeart size={19}/> Always donation-only</div></div><div className="auth-aside-bottom">Platform preview · Not accepting medicines</div></aside>; }

function Field({ id, label, value, onChange, type = 'text', required = false, autoComplete, maxLength, placeholder, error, children }) {
  return <label className="field" htmlFor={id}><span>{label}{required ? ' *' : ''}</span><span className="field-input-wrap"><input id={id} value={value} type={type} required={required} maxLength={maxLength} autoComplete={autoComplete} placeholder={placeholder} onChange={onChange} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined}/>{children}</span>{error && <span id={`${id}-error`} className="form-error">{error}</span>}</label>;
}

const empty = { name: '', email: '', password: '', role: 'DONOR', orgName: '', licenseNo: '', serviceArea: '' };

export function AuthPage({ register = false }) {
  const { session, login, register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState(empty);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const ownsRedirect = useRef(false);
  const update = (key) => (event) => { setError(''); setFieldErrors(s => ({ ...s, [key]: '' })); setForm(s => ({ ...s, [key]: event.target.value })); };

  // Keep the submit redirect authoritative until this page unmounts.
  if (session && !ownsRedirect.current) return <Navigate to="/dashboard" replace/>;
  const submit = async event => {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    if (!AUTH_ENABLED) { setError('Account submissions are disabled in this preview. No information has been sent.'); return; }
    const problem = register ? validateRegistration(form) : (!EMAIL_RE.test(form.email.trim()) || !form.password ? 'Enter your email and password.' : '');
    if (problem) { setError(problem); return; }
    ownsRedirect.current = true;
    setLoading(true);
    try {
      const user = register
        ? await registerUser(buildRegistrationPayload(form))
        : await login(form.email.trim(), form.password);
      setForm(s => ({ ...s, password: '' }));
      navigate(user.role === 'NGO' ? '/dashboard/ngo' : '/dashboard/donor', {
        replace: true, state: { authMessage: register ? 'Local test account created.' : 'Signed in to your local test account.' },
      });
    } catch (err) { ownsRedirect.current = false; setError(err.message || 'Please try again.'); setFieldErrors(err.fields || {}); }
    finally { setLoading(false); }
  };

  return <main className="auth-page"><div className="container auth-layout"><AuthAside register={register}/><div className="auth-form-panel">
    <div className="auth-form-inner"><span className="auth-form-kicker">{register ? 'CREATE AN ACCOUNT' : 'MEMBER ACCESS'}</span><h1>{register ? 'Join the MedBridge journey' : 'Sign in to MedBridge'}</h1><p className="form-subtitle">{register ? 'Start with the role that best describes you.' : 'Access your private donor or NGO workspace.'}</p>
    <div className="preview-alert" role="status"><ShieldCheck size={19}/><div><b>{AUTH_ENABLED ? 'Local development mode' : 'Safe preview mode'}</b><span>{AUTH_ENABLED ? 'Use fictional test accounts only. Authentication connects only to the isolated local backend; medicine submissions remain unavailable.' : 'Registration and sign-in submissions are currently disabled. No account data will be sent.'}</span></div></div>
    <form onSubmit={submit} noValidate>
      {register && <><div className="role-picker" role="group" aria-label="Choose your role"><button type="button" className={form.role==='DONOR'?'role-option selected':'role-option'} aria-pressed={form.role==='DONOR'} onClick={()=>{setForm(s=>({...s,role:'DONOR'}));setError('');}}><HandHeart size={23}/><b>I'm a donor</b><small>For individuals</small></button><button type="button" className={form.role==='NGO'?'role-option selected':'role-option'} aria-pressed={form.role==='NGO'} onClick={()=>{setForm(s=>({...s,role:'NGO'}));setError('');}}><UsersRound size={23}/><b>I'm an NGO</b><small>For organizations</small></button></div><Field id="full-name" error={fieldErrors.name} label="Full name" required autoComplete="name" maxLength={120} placeholder="Your full name" value={form.name} onChange={update('name')}/></>}
      <Field id="email" error={fieldErrors.email} label="Email address" required autoComplete="email" type="email" maxLength={190} placeholder="you@example.com" value={form.email} onChange={update('email')}/>
      <Field id="password" error={fieldErrors.password} label="Password" required autoComplete={register?'new-password':'current-password'} type={showPassword?'text':'password'} maxLength={72} placeholder={register?'At least 12 characters':'Enter your password'} value={form.password} onChange={update('password')}><button type="button" className="input-affordance" aria-label={showPassword?'Hide password':'Show password'} onClick={()=>setShowPassword(s=>!s)}>{showPassword?<EyeOff size={18}/>:<Eye size={18}/>}</button></Field>
      {register && form.role === 'NGO' && <div className="ngo-fields"><div className="ngo-fields-title"><ShieldCheck size={17}/> Organization information</div><Field id="org-name" error={fieldErrors.orgName} label="Registered organization name" required maxLength={200} placeholder="Organization name" value={form.orgName} onChange={update('orgName')}/><Field id="license-no" error={fieldErrors.licenseNo} label="License / registration number" required maxLength={100} placeholder="Registration ID" value={form.licenseNo} onChange={update('licenseNo')}/><Field id="service-area" error={fieldErrors.serviceArea} label="Service area" required maxLength={200} placeholder="City or coverage area" value={form.serviceArea} onChange={update('serviceArea')}/><p className="field-hint">An NGO account starts unverified. Approval is never automatic.</p></div>}
      {register && <p className="field-hint register-hint">{AUTH_ENABLED ? 'Use fictional test accounts only. Do not submit medical records or medicine details.' : 'Do not submit medical records or medicine details. Accounts will be enabled only after further launch review.'}</p>}
      {error && <div className="form-error" role="alert">{error}</div>}
      <button type="submit" className="btn btn-mint btn-block" disabled={loading || !AUTH_ENABLED}>{loading?'Please wait…':register?'Create account':'Sign in'} <ArrowRight size={17}/></button>
    </form>
    <p className="auth-swap">{register?'Already have an account?':'New to MedBridge?'} <Link to={register?'/login':'/register'}>{register?'Log in':'Explore registration'}</Link></p>
    <div className="demo-links">Preview without an account: <Link to="/preview/donor">Donor workspace</Link><span>·</span><Link to="/preview/ngo">NGO workspace</Link></div>
    </div>
  </div></div></main>;
}
