import { Link } from 'react-router-dom';
import { ArrowRight, ArrowUpRight, CheckCircle2, CircleCheck, ClipboardCheck, HandHeart, HeartHandshake, LockKeyhole, PackageCheck, ShieldCheck, ShieldHalf, Sparkles, UsersRound, Workflow } from 'lucide-react';

const steps = [
  { number: '01', icon: HandHeart, title: 'A donor starts the process', text: 'Once the platform is approved for operation, donors will be able to provide medicine details for a safety review.' },
  { number: '02', icon: ClipboardCheck, title: 'Safety comes first', text: 'Licensing, storage history, expiry, package integrity and legal eligibility must be assessed before any listing can advance.' },
  { number: '03', icon: UsersRound, title: 'Verified NGOs can participate', text: 'Only approved organizations will be eligible for restricted workflows; public sign-up never grants verification.' },
];

function PathwayCard({ className = '', icon: Icon, eyebrow, title, subtitle }) {
  return <div className={`pathway-card ${className}`}>
    <div className="pathway-icon"><Icon size={20}/></div>
    <div className="pathway-text"><span>{eyebrow}</span><strong>{title}</strong><small>{subtitle}</small></div>
    <CheckCircle2 className="pathway-outline" size={18}/>
  </div>;
}

export default function Home() {
  return <main>
    <section className="hero">
      <div className="hero-aura hero-aura-a"/><div className="hero-aura hero-aura-b"/>
      <div className="container hero-inner">
        <div className="hero-copy">
          <div className="eyebrow hero-eyebrow"><span className="live-dot"/> Introducing a more responsible pathway</div>
          <h1>Care is meant<br/>to <span className="hero-highlight">go further.</span></h1>
          <p>We're building a safer, donation-only bridge between people with eligible unused medicines and the organizations equipped to help communities.</p>
          <div className="hero-actions"><Link to="/register" className="btn btn-mint">Explore MedBridge <ArrowRight size={18}/></Link><a className="hero-secondary" href="#how-it-works">See how it will work <ArrowUpRight size={16}/></a></div>
          <div className="hero-disclaimer"><ShieldCheck size={20}/><span>Safety-first design. No medicine donations are being accepted through this preview.</span></div>
        </div>
        <div className="hero-art" aria-label="Illustration of the three steps in the proposed MedBridge process" role="img">
          <div className="hero-art-glow"/>
          <div className="diagram-background"/>
          <div className="pathway-shell">
            <div className="pathway-top"><div><span className="pathway-mini-label">The MedBridge approach</span><h2>Every connection<br/>starts with trust.</h2></div><span className="pathway-orbit"><HeartHandshake size={30}/></span></div>
            <div className="pathway-list">
              <PathwayCard icon={HandHeart} eyebrow="STEP 01" title="Donor information" subtitle="Document and review"/>
              <div className="pathway-connector"/>
              <PathwayCard className="pathway-card-highlight" icon={ShieldCheck} eyebrow="STEP 02" title="Safety & verification" subtitle="Professional oversight required"/>
              <div className="pathway-connector"/>
              <PathwayCard icon={UsersRound} eyebrow="STEP 03" title="NGO eligibility" subtitle="Only verified organizations"/>
            </div>
            <div className="pathway-bottom"><span><CircleCheck size={15}/> Donation-only model</span><span>Concept preview <ArrowUpRight size={15}/></span></div>
          </div>
          <div className="floating-sticker"><Sparkles size={17}/> Designed around trust</div>
        </div>
      </div>
    </section>

    <section className="trust-strip" aria-label="MedBridge principles"><div className="container trust-strip-inner">
      <div><ShieldCheck size={23}/><span>Safety-led by design</span></div><div><LockKeyhole size={23}/><span>Restricted NGO access</span></div><div><HandHeart size={23}/><span>Donation-only purpose</span></div><div><Workflow size={23}/><span>Built for accountability</span></div>
    </div></section>

    <section className="section how-section" id="how-it-works"><div className="container">
      <div className="section-head"><div><div className="eyebrow">A thoughtful process</div><h2>Simple to understand.<br/><span>Serious about safety.</span></h2></div><p>Medicine redistribution is not ordinary sharing. The proposed MedBridge workflow places verification and professional oversight ahead of access.</p></div>
      <div className="step-grid">{steps.map(({number,icon: Icon,title,text})=><article className="step-card" key={number}><div className="step-icon"><Icon size={27}/></div><span className="step-number">{number} / 03</span><h3>{title}</h3><p>{text}</p><span className="step-line"/></article>)}</div>
    </div></section>

    <section className="section approach-section" id="safety"><div className="container approach-grid">
      <div className="approach-visual"><div className="approach-grid-bg"/><div className="shield-emblem"><ShieldHalf size={68}/></div><div className="approach-mini-card"><span className="tiny-label">PRIORITY ONE</span><b>People before processes.</b><span>Every proposed workflow begins with eligibility and care.</span></div><div className="approach-stamp"><LockKeyhole size={18}/> Verified access only</div></div>
      <div className="approach-copy"><div className="eyebrow">Why we're building this</div><h2>Technology should make care <span>more responsible.</span></h2><p className="section-intro">A helpful connection is only valuable when it is also safe. That principle is built into how MedBridge is being developed.</p>
      <div className="principle-list">
        <div><span className="principle-icon"><PackageCheck size={20}/></span><div><h3>Proper checks, not instant listings</h3><p>Medicine submissions and matching remain disabled until legal and clinical safeguards are established.</p></div></div>
        <div><span className="principle-icon"><UsersRound size={20}/></span><div><h3>Verified organizations first</h3><p>Signing up as an NGO never automatically unlocks organization-only access.</p></div></div>
        <div><span className="principle-icon"><LockKeyhole size={20}/></span><div><h3>Privacy by intention</h3><p>No patient information is requested in this preview; authentication runs through the existing secured backend.</p></div></div>
      </div></div>
    </div></section>

    <section className="section audience-section"><div className="container"><div className="center-head"><div className="eyebrow">Designed for connection</div><h2>One mission. Two meaningful roles.</h2><p>Explore how the experience will be tailored to each participant.</p></div>
      <div className="audience-grid"><article className="audience-card donor-audience"><span className="audience-icon"><HandHeart size={27}/></span><h3>For donors</h3><p>A clear, guided workspace built to help donors understand requirements and track future screening steps.</p><Link to="/preview/donor">Explore donor dashboard <ArrowRight size={17}/></Link></article>
      <article className="audience-card ngo-audience"><span className="audience-icon"><UsersRound size={27}/></span><h3>For NGOs</h3><p>A dedicated place for organizations to see their verification status and prepare for approved workflows.</p><Link to="/preview/ngo">Explore NGO dashboard <ArrowRight size={17}/></Link></article></div>
    </div></section>

    <section className="cta-section"><div className="container"><div className="cta-inner"><div className="eyebrow light-eyebrow">The next step in connected care</div><h2>Let's build a bridge<br/>that makes a difference.</h2><p>Explore the platform preview while we continue the security, compliance and product work required for a real launch.</p><div className="cta-buttons"><Link className="btn btn-mint" to="/register">Explore registration <ArrowRight size={18}/></Link><Link className="btn btn-outline-light" to="/preview/donor">Preview a workspace</Link></div><div className="cta-small"><ShieldCheck size={16}/> Preview only · No real medicine handling or distribution</div></div></div></section>
  </main>;
}
