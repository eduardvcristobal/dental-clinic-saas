import { SchedulingWorkspace } from './SchedulingWorkspace'
import { Odontogram } from './Odontogram'
import { PatientEncounters } from './PatientEncounters'
import { PatientTreatmentPlans } from './PatientTreatmentPlans'
import { PatientRegistry, PatientCare, type Patient } from './PatientRegistry'
import { api } from '../api/client'
import { ClinicManagement } from './ClinicManagement'
import { useEffect, useState } from 'react'
import { NavLink, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { WORKSPACE_MODULES, DISPLAY_ROLES, canSeeModule, type WorkspaceRole } from './catalog'
import './workspace.css'
import { JourneyPreview } from './JourneyPreview'
import { ModuleStatusPreview } from './ModuleStatusPreview'
const schedule = [
  { time: '09:00', patient: 'Maria Santos', activity: 'Cleaning & examination', dentist: 'Dr. Reyes', chair: 'Chair 1', status: 'Arrived' },
  { time: '10:30', patient: 'Jose Cruz', activity: 'Composite restoration', dentist: 'Dr. Lim', chair: 'Chair 2', status: 'Confirmed' },
  { time: '11:15', patient: 'Ana Dela Cruz', activity: 'Treatment consultation', dentist: 'Dr. Reyes', chair: 'Chair 1', status: 'Scheduled' },
  { time: '14:00', patient: 'Paolo Garcia', activity: 'Follow-up', dentist: 'Dr. Lim', chair: 'Chair 2', status: 'Scheduled' },
]
const money = (n: number) => new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', maximumFractionDigits: 0 }).format(n)
const DEMO_NOTICE = 'Demonstration only — fictional data. No patient records, invoices or clinical notes are saved.'

function DemoPill({ text = 'Preview' }: { text?: string }) { return <span className="ws-pill">{text}</span> }
function ComingSoon({ title, description }: { title: string; description: string }) { return <section className="ws-empty"><span className="ws-empty-icon">◇</span><DemoPill text="Coming soon"/><h2>{title}</h2><p>{description}</p><p>This module is planned but is not available yet. No real workflow is connected.</p></section> }
function Metrics({ values }: { values: { label: string; value: string; detail: string }[] }) { return <div className="ws-metrics">{values.map(v => <div className="ws-panel ws-metric" key={v.label}><small>{v.label}</small><strong>{v.value}</strong><span>{v.detail}</span></div>)}</div> }
function Schedule({ subset = schedule }: { subset?: typeof schedule }) { return <div className="ws-panel"><div className="ws-panel-head"><h3>Today's schedule <DemoPill/></h3><span>Monday · Example day</span></div><div className="ws-scroll"><table className="ws-table"><thead><tr><th>Time</th><th>Patient</th><th>Visit</th><th>Provider / Chair</th><th>Status</th></tr></thead><tbody>{subset.map(e => <tr key={e.time}><td><b>{e.time}</b></td><td>{e.patient}</td><td>{e.activity}</td><td>{e.dentist}<small>{e.chair}</small></td><td><span className="ws-status">{e.status}</span></td></tr>)}</tbody></table></div></div> }
function CommandCenter({ role }: { role: WorkspaceRole }) {
  const messages: Record<WorkspaceRole, string> = { OWNER: 'Your practice at a glance', ADMIN: 'Clinic operations and configuration', DENTIST: 'Your clinical day, organized', FRONT_DESK: 'Keep appointments and arrivals moving' }
  const tiles: Record<WorkspaceRole, { label: string; value: string; detail: string }[]> = {
    OWNER: [{label:'Appointments today',value:'24',detail:'2 chairs in service'},{label:'Estimated collections',value:money(28600),detail:'Fictional amount'},{label:'Open balances',value:money(17400),detail:'Example outstanding'},{label:'Recalls due',value:'12',detail:'Require outreach'}],
    ADMIN: [{label:'Appointments today',value:'24',detail:'All providers'},{label:'Active staff',value:'6',detail:'Example staff'},{label:'Chairs in use',value:'2 / 3',detail:'One available'},{label:'Follow-ups',value:'8',detail:'Require action'}],
    DENTIST: [{label:'My visits',value:'7',detail:'Today’s patients'},{label:'Waiting',value:'2',detail:'Check-in complete'},{label:'Planned procedures',value:'4',detail:'For review'},{label:'Clinical notes',value:'3',detail:'Drafts in this demo'}],
    FRONT_DESK: [{label:'Scheduled',value:'24',detail:'All chairs'},{label:'Arrived',value:'5',detail:'Waiting / in chair'},{label:'Unconfirmed',value:'4',detail:'Contact patients'},{label:'Recalls due',value:'12',detail:'Outreach queue'}],
  }
  return <><div className="ws-heading"><div><span className="ws-eyebrow">Command Center / {DISPLAY_ROLES[role]}</span><h1>{messages[role]}</h1><p>Book → Remind → Arrive → Treat → Bill → Recall</p></div><DemoPill/></div><Metrics values={tiles[role]}/><div className="ws-columns"><div><Schedule subset={role === 'DENTIST' ? schedule.filter(s => s.dentist === 'Dr. Reyes') : schedule}/></div><div className="ws-panel ws-sidecard"><h3>Needs attention</h3><ul><li>4 appointments awaiting confirmation</li><li>2 patients waiting for a chair</li><li>12 patients approaching recall date</li></ul><NavLink to="/workspace/patient-360" className="ws-primary-link">Explore Patient 360° →</NavLink><NavLink to="/workspace/journey" className="ws-primary-link">Simulate patient journey →</NavLink></div></div></>
}
function Patient360({ role }: { role: WorkspaceRole }) {
  const [params,setParams] = useSearchParams()
  const [query,setQuery] = useState('')
  const [rows,setRows] = useState<Patient[]>([])
  const [loaded,setLoaded] = useState<{id:string;patient:Patient}|null>(null)
  const [error,setError] = useState('')
  const [loading,setLoading] = useState(false)
  const [tab,setTab] = useState('overview')
  const selectedId = params.get('patient')
  useEffect(() => {
    let live=true
    if(!selectedId)return
    void api<Patient>(`/api/patients/${encodeURIComponent(selectedId)}`).then(p=>{if(live){setLoaded({id:selectedId,patient:p})}}).catch(e=>{if(live){setLoaded(null);setError(e instanceof Error?e.message:'Patient not available')}})
    return ()=>{live=false}
  },[selectedId])
  useEffect(()=>{
    if(selectedId)return
    let live=true
    const timer=setTimeout(()=>{
      setLoading(true)
      void api<Patient[]>(`/api/patients?q=${encodeURIComponent(query)}&limit=20`).then(r=>{if(live){setRows(r);setError('')}}).catch(e=>{if(live)setError(e instanceof Error?e.message:'Search failed')}).finally(()=>{if(live)setLoading(false)})
    },250)
    return ()=>{live=false;clearTimeout(timer)}
  },[query,selectedId])
  const open=(id:string)=>{setTab('overview');setParams({patient:id})}
  return <>
    <div className="ws-heading"><div><span className="ws-eyebrow">Clinical / Patient 360° · M2.3</span><h1>Patient 360°</h1><p>Verified clinic patient identities and their recorded care foundation. Future workflows remain unavailable.</p></div></div>
    {error&&(!selectedId||loaded?.id===selectedId)&&<p role="alert" className="ws-global-warning">{error}</p>}
    {!selectedId&&<div className="ws-panel ws-management"><h3>Select a patient</h3><label>Search clinic registry<input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Name, chart number or phone"/></label>{loading&&<p>Loading patient records…</p>}<div className="ws-directory">{rows.map(p=><button key={p.id} onClick={()=>open(p.id)}><b>{p.first_name} {p.last_name}</b><small>{p.chart_no}</small></button>)}</div><p className="ws-subtle">Only patients belonging to your authenticated clinic can be opened. No fictional patient is selected automatically.</p></div>}
    {selectedId&&<><button onClick={()=>{setParams({});setLoaded(null)}}>← Return to patient search</button>{loaded?.id===selectedId&&(()=>{const patient=loaded.patient;return <>
      <div className="ws-panel ws-patient-head"><div className="ws-avatar">{patient.first_name.slice(0,1)}{patient.last_name.slice(0,1)}</div><div><h2>{patient.first_name} {patient.last_name}</h2><p>{patient.chart_no} · Date of birth: {patient.birth_date??'Not recorded'} · {patient.phone??'No phone listed'}</p></div><span className="ws-demo-chip">CLINIC RECORD · TEST DATA ONLY</span></div>
      <div className="ws-tabs" role="tablist" aria-label="Patient sections">{[['overview','Overview'],['care','History, guardians & consent'],['chart','Odontogram'],['visits','Visits & SOAP'],['plans','Treatment plans'],['billing','Billing']].map(([id,label])=><button key={id} role="tab" aria-selected={tab===id} className={tab===id?'selected':''} onClick={()=>setTab(id)}>{label}</button>)}</div>
      {tab==='overview'&&<div className="ws-panel ws-management"><h3>Patient details</h3><div className="ws-info-grid"><div><small>Name</small><strong>{patient.first_name} {patient.last_name}</strong></div><div><small>Chart number</small><strong>{patient.chart_no}</strong></div><div><small>Birth date</small><strong>{patient.birth_date??'Not recorded'}</strong></div><div><small>Contact</small><strong>{patient.phone??'Not recorded'}</strong></div><div><small>Email</small><strong>{patient.email??'Not recorded'}</strong></div></div><p>Medical history and consent records are available under History, guardians & consent, subject to your access level.</p><NavLink to="/workspace/patients">Edit demographics in Patient Registry →</NavLink></div>}
      {tab==='care'&&<PatientCare key={patient.id} patient={patient} role={role}/>}
      {tab==='visits'&&(['OWNER','ADMIN','DENTIST'].includes(role)?<PatientEncounters key={patient.id} patientId={patient.id}/>:<p role="alert">Clinical encounter access is restricted.</p>)}
      {tab==='chart'&&(['OWNER','ADMIN','DENTIST'].includes(role)?<Odontogram key={patient.id} patientId={patient.id}/>:<p role="alert">Clinical chart access is restricted.</p>)}
      {tab==='plans'&&(['OWNER','ADMIN','DENTIST'].includes(role)?<PatientTreatmentPlans key={patient.id} patientId={patient.id}/>:<p role="alert">Treatment planning access is restricted.</p>)}
      {tab==='billing'&&<ComingSoon title={{chart:'Odontogram',visits:'Visits and clinical notes',plans:'Treatment plans',billing:'Patient billing'}[tab]??'Clinical workflow'} description="This feature is not yet connected to this patient's clinical record. No sample findings, SOAP notes, invoices or payments are shown as though they belong to the patient."/>}
    </>})()}</>}
  </>
}
function BillingPreview(){return <><div className="ws-heading"><div><span className="ws-eyebrow">Revenue / Demo</span><h1>Billing overview <DemoPill/></h1><p>Fictional amounts — not connected to a ledger</p></div></div><Metrics values={[{label:'Total invoiced',value:money(37500),detail:'Demo values'},{label:'Collected',value:money(27800),detail:'Demo values'},{label:'Outstanding',value:money(9700),detail:'Demo values'}]}/><ComingSoon title="Real invoicing & collections" description="Invoice posting, payment handling, voids and receipts require audited backend workflows."/></>}
function ReportsPreview(){return <><div className="ws-heading"><div><span className="ws-eyebrow">Practice insights</span><h1>Reports <DemoPill/></h1><p>Illustrative metrics only</p></div></div><Metrics values={[{label:'Monthly visits',value:'142',detail:'Fictional'},{label:'No-show rate',value:'5.2%',detail:'Fictional'},{label:'Collections',value:money(118400),detail:'Fictional'}]}/><ComingSoon title="Production reports" description="Trusted analytics will only be activated after real scheduling, care and financial modules are implemented."/></>}
export function ClinicWorkspace(){const { session,signOut }=useAuth();const { moduleId='command-center' }=useParams();const [drawer,setDrawer]=useState(false);const [theme,setTheme]=useState<'light'|'dark'>(()=>window.localStorage.getItem('dental-theme')==='dark'?'dark':'light');useEffect(()=>{window.localStorage.setItem('dental-theme',theme)},[theme]);if(!session)return null;if(session.user.platformAdmin||!session.clinic)return <Navigate to="/platform-admin" replace/>;const role=session.user.role as WorkspaceRole;const mod=WORKSPACE_MODULES.find(m=>m.id===moduleId);if(!mod||!canSeeModule(moduleId,role))return <Navigate to="/workspace/command-center" replace/>;const groups=[...new Set(WORKSPACE_MODULES.map(m=>m.group))];return <div className="ws-app" data-theme={theme}><aside className={'ws-sidebar '+(drawer?'open':'')}><div className="ws-brand"><span className="ws-brand-icon">✳</span><div><strong>Dental Clinic SaaS</strong><small>Practice workspace</small></div><button className="ws-close" onClick={()=>setDrawer(false)} aria-label="Close menu">×</button></div><div className="ws-clinic"><small>CLINIC</small><strong>{session.clinic.name}</strong><span>Branch selection · configuration in Branches</span></div><nav aria-label="Clinic workspace">{groups.map(g=><div key={g} className="ws-navgroup"><span>{g}</span>{WORKSPACE_MODULES.filter(m=>m.group===g&&m.roles.includes(role)).map(m=><NavLink key={m.id} onClick={()=>setDrawer(false)} to={'/workspace/'+m.id} className={({isActive})=>isActive?'current':''}><span>{m.title}</span>{<small>{m.state==='coming-soon'?'Soon':m.state==='preview'?'Demo':'Active'}</small>}</NavLink>)}</div>)}</nav></aside><div className="ws-main"><header className="ws-topbar"><button className="ws-menu" onClick={()=>setDrawer(true)} aria-label="Open navigation">☰</button><div className="ws-top-context"><b>{session.clinic.name}</b><span> / {DISPLAY_ROLES[role]}</span></div><div className="ws-top-actions"><span className="ws-demo-top">DESIGN PREVIEW</span><button onClick={()=>setTheme(theme==='light'?'dark':'light')} aria-label="Toggle theme">{theme==='light'?'☾':'☀'}</button><button className="ws-signout" onClick={()=>void signOut()}>Sign out</button></div></header><main className="ws-body"><p className="ws-global-warning">{['patients','patient-360','odontogram'].includes(moduleId)?'This workspace accesses real clinic-scoped records. Use synthetic patient information only until production approval. Unimplemented clinical workflows are clearly marked Coming soon.':DEMO_NOTICE}</p>{moduleId==='command-center'?<CommandCenter role={role}/>:moduleId==='patient-360'?<Patient360 role={role}/>:moduleId==='odontogram'?<Navigate to="/workspace/patient-360" replace/>:moduleId==='journey'?<JourneyPreview role={role}/>:moduleId==='calendar'?<SchedulingWorkspace role={role}/>:moduleId==='check-in'?<SchedulingWorkspace role={role} mode="check-in"/>:moduleId==='patients'?<PatientRegistry role={role}/>:moduleId==='billing'?<BillingPreview/>:moduleId==='reports'?<ReportsPreview/>:moduleId==='module-status'?<ModuleStatusPreview role={role}/>:['branches','chairs','staff','settings'].includes(moduleId)?<ClinicManagement section={moduleId as 'branches'|'chairs'|'staff'|'settings'}/>:<ComingSoon title={mod.title} description={mod.description}/>}</main></div>{drawer&&<button className="ws-backdrop" onClick={()=>setDrawer(false)} aria-label="Close navigation"/>}</div>}
