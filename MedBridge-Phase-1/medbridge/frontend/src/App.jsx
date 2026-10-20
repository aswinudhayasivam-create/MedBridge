import { useEffect } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { PageLayout } from './components/Layout.jsx';
import Home from './pages/Home.jsx';
import { AuthPage } from './pages/Auth.jsx';
import { Dashboard, DashboardPreview } from './pages/Dashboard.jsx';

function ScrollManager() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (hash) {
      const id = hash.slice(1);
      requestAnimationFrame(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    } else { window.scrollTo?.({ top: 0 }); }
  }, [pathname, hash]);
  return null;
}

function NotFound() { return <main className="not-found container"><p className="eyebrow">404 · Page not found</p><h1>This bridge doesn't lead anywhere yet.</h1><p>Let's take you back to the beginning.</p><Link to="/" className="btn btn-dark"><ArrowLeft size={16}/> Return home</Link></main>; }

export default function App() {
  return <PageLayout><ScrollManager/><Routes>
    <Route path="/" element={<Home/>}/>
    <Route path="/login" element={<AuthPage/>}/>
    <Route path="/register" element={<AuthPage register/>}/>
    <Route path="/dashboard" element={<Dashboard/>}/>
    <Route path="/dashboard/:role" element={<Dashboard/>}/>
    <Route path="/preview/:role" element={<DashboardPreview/>}/>
    <Route path="*" element={<NotFound/>}/>
  </Routes></PageLayout>;
}
