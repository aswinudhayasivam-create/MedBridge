import { useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { ArrowRight, HeartHandshake, Menu, X, ShieldCheck, ExternalLink } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';

export function Brand({ light = false }) {
  return <Link aria-label="MedBridge homepage" to="/" className={`brand ${light ? 'brand-light' : ''}`}>
    <span className="brand-mark"><HeartHandshake size={24} strokeWidth={2.1} /></span>
    <span>Med<span className="brand-bridge">Bridge</span></span>
  </Link>;
}

export function Header() {
  const [open, setOpen] = useState(false);
  const { session, logout } = useAuth();
  const close = () => setOpen(false);
  return <>
    <div className="development-banner"><ShieldCheck size={14} /> Platform preview · No medicine submissions or transfers are available</div>
    <header className="site-header">
      <div className="container nav-content">
        <Brand />
        <button className="mobile-nav-toggle" type="button" aria-label={open ? 'Close navigation' : 'Open navigation'} aria-expanded={open} onClick={() => setOpen(x => !x)}>{open ? <X size={23} /> : <Menu size={23} />}</button>
        <nav className={`nav-links ${open ? 'nav-open' : ''}`} aria-label="Main navigation">
          <NavLink onClick={close} to="/" end>Home</NavLink>
          <Link onClick={close} to="/#how-it-works">How it works</Link>
          <Link onClick={close} to="/#safety">Our approach</Link>
          {session ? <>
            <Link onClick={close} to="/dashboard" className="btn btn-dark btn-sm">Dashboard <ArrowRight size={15} /></Link>
            <button onClick={() => { logout(); close(); }} className="nav-logout">Sign out</button>
          </> : <>
            <Link onClick={close} to="/login" className="nav-login">Log in</Link>
            <Link onClick={close} to="/register" className="btn btn-dark btn-sm">Get started <ArrowRight size={15} /></Link>
          </>}
        </nav>
      </div>
    </header>
  </>;
}

export function Footer() {
  return <footer className="site-footer">
    <div className="container footer-main">
      <div><Brand light/><p>Connecting care, responsibly. A donation-only platform under development, designed around verification and safety.</p></div>
      <div className="footer-links"><Link to="/">Home</Link><Link to="/register">Join the preview</Link><Link to="/preview/donor">Donor preview</Link><Link to="/preview/ngo">NGO preview</Link></div>
      <div className="footer-note"><ShieldCheck size={22}/><span>No sales or payments. Do not send medicines, medical records, or personal health information through this preview.</span></div>
    </div>
    <div className="container footer-bottom"><span>© {new Date().getFullYear()} MedBridge · In development</span><span>Built for a safer way to connect care <ExternalLink size={13}/></span></div>
  </footer>;
}

export function PageLayout({ children }) { return <><Header/>{children}<Footer/></>; }
