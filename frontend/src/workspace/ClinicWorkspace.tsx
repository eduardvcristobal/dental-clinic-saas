import { SchedulingWorkspace } from './SchedulingWorkspace'
import { describeError } from './toast'
import { NotificationWorkspace } from './NotificationWorkspace'
import { ReportsWorkspace } from './ReportsWorkspace'
import { Odontogram } from './ClinicalOdontogram'
import { PatientEncounters } from './PatientEncounters'
import { PatientTreatmentPlans } from './PatientTreatmentPlans'
import { PatientBilling } from './PatientBilling'
import { PatientRegistry, PatientCare, PatientSectionIcon, type Patient } from './PatientRegistry'
import { PatientFinder } from './PatientFinder'
import { api } from '../api/client'
import { ClinicManagement } from './ClinicManagement'
import { useCallback, useEffect, useRef, useState } from 'react'
import { NavLink, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { WORKSPACE_MODULES, DISPLAY_ROLES, canSeeModule, type WorkspaceRole } from './catalog'
import { SeedDataControls } from './SeedDataControls'
import { ToastProvider } from './ToastProvider'
import { Dropdown } from './Dropdown'
import { ClinicBranchProvider, useClinicBranch } from './ClinicBranchContext'
import './workspace.css'
import { JourneyPreview } from './JourneyPreview'
import { DayTimeline } from './DayTimeline'

function WorkspaceNavIcon({id}:{id:string}){return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{id==='command-center'?<><rect x="2.5" y="2.5" width="6" height="6" rx="1"/><rect x="11.5" y="2.5" width="6" height="6" rx="1"/><rect x="2.5" y="11.5" width="6" height="6" rx="1"/><rect x="11.5" y="11.5" width="6" height="6" rx="1"/></>:id==='journey'?<><circle cx="4" cy="10" r="2"/><circle cx="15.5" cy="4" r="2"/><circle cx="15.5" cy="16" r="2"/><path d="m6 9 7.5-4M6 11l7.5 4"/></>:id==='calendar'?<><rect x="2.5" y="4" width="15" height="13" rx="2"/><path d="M6 2.5v3M14 2.5v3M2.5 8h15m-10 3h2m-2 3h5"/></>:id==='patients'||id==='staff'||id==='patient-360'?<><circle cx="8" cy="6" r="3"/><path d="M2.5 17a5.5 5.5 0 0 1 11 0m2-9v6m-3-3h6"/></>:id==='check-in'?<><path d="M11 2.5H4.5v15H11M9 10h8m-3-3 3 3-3 3"/></>:id==='odontogram'?<><path d="M6 3c-2 0-3 2-2 5l1.5 7c.4 2 2 2.5 3 0L10 12l1.5 3c1 2.5 2.6 2 3 0L16 8c1-3-1-5-3-5-1.2 0-2 1-3 1S7.2 3 6 3Z"/></>:id==='encounters'?<><path d="M5 3h10v14H5zM8 2h4v3H8zM7.5 9h5m-5 3h5"/></>:id==='treatment-plans'?<><path d="M4 4h12M4 9h8M4 14h6m5-2v5m-2.5-2.5h5"/></>:id==='imaging'?<><rect x="2.5" y="3.5" width="15" height="13" rx="2"/><circle cx="7" cy="8" r="1.5"/><path d="m4 14 4-3 2.5 2 2-2 3.5 3"/></>:id==='prescriptions'?<><path d="M5 4a4 4 0 0 1 5.7 0l5.3 5.3a4 4 0 0 1-5.7 5.7L5 9.7A4 4 0 0 1 5 4Z"/><path d="m7 12 5-5"/></>:id==='billing'||id==='installments'?<><rect x="2.5" y="4" width="15" height="12" rx="2"/><path d="M2.5 8h15m-11 4h3"/></>:id==='price-list'?<><path d="m3 6 7-3 7 7-7 7-7-7z"/><circle cx="7" cy="7" r="1"/></>:id==='reminders'?<><path d="M15 8a5 5 0 0 0-10 0c0 6-2 6-2 7h14c0-1-2-1-2-7m-7 3h4m-3 7h2"/></>:id==='reports'?<><path d="M3 17V9h4v8m3 0V4h4v13m3 0v-6h3v6"/></>:id==='branches'||id==='chairs'?<><path d="M3 17h14M5 17V7l6-4 6 4v10m-7 0v-5h4v5"/></>:id==='settings'?<><circle cx="10" cy="10" r="3"/><path d="m8.5 2.5.5-1h2l.5 1 1.5.7 1-.4 1.4 1.4-.4 1 .7 1.5 1 .5v2l-1 .5-.7 1.5.4 1-1.4 1.4-1-.4-1.5.7-.5 1h-2l-.5-1-1.5-.7-1 .4-1.4-1.4.4-1-.7-1.5-1-.5v-2l1-.5.7-1.5-.4-1L6 3.2l1 .4z"/></>:id==='module-status'?<><rect x="3" y="3" width="5" height="5" rx="1"/><rect x="12" y="3" width="5" height="5" rx="1"/><rect x="3" y="12" width="5" height="5" rx="1"/><path d="M12 14.5h5m-2.5-2.5v5"/></>:id==='audit-log'?<><path d="M5 2.5h7l4 4V17H5zM12 2.5v4h4m-8 4h6m-6 3h6"/></>:<><circle cx="10" cy="10" r="7"/><path d="M10 6v4l2.5 1.5"/></>}</svg>}
import { ModuleStatusPreview } from './ModuleStatusPreview'

function DemoPill({ text = 'Preview' }: { text?: string }) { return <span className="ws-pill">{text}</span> }
function ComingSoon({ title, description }: { title: string; description: string }) { return <section className="ws-empty"><span className="ws-empty-icon">◇</span><DemoPill text="Coming soon"/><h2>{title}</h2><p>{description}</p><p>This module is planned but is not available yet. No real workflow is connected.</p></section> }
function Metrics({ values }: { values: { label: string; value: string; detail: string }[] }) { return <div className="ws-metrics">{values.map(v => <div className="ws-panel ws-metric" key={v.label}><small>{v.label}</small><strong>{v.value}</strong><span>{v.detail}</span></div>)}</div> }
type DashboardData = {
  appointments_today:number; patients_registered:number; appointments_awaiting_confirmation:number;
  patients_waiting:number; recalls_due:number; active_chairs:number; clinicDate:string
}
type ReadinessState={hours:boolean;chair:boolean;dentist:boolean}
function ClinicReadiness({role,data}:{role:WorkspaceRole;data:DashboardData|null}){
  const [setup,setSetup]=useState<ReadinessState|null>(null)
  const [hidden,setHidden]=useState(()=>window.localStorage.getItem('clinic.readiness.hidden')==='true')
  useEffect(()=>{
    if(!['OWNER','ADMIN'].includes(role)){setSetup(null);return}
    let active=true
    const load=async()=>{
      const [branchesResult,dentistsResult]=await Promise.allSettled([
        api<Array<{id:string;active:boolean}>>('/api/clinic-management/branches'),
        api<Array<{id:string;active:boolean}>>('/api/onboarding/dentists')
      ])
      const branches=branchesResult.status==='fulfilled'?branchesResult.value.filter(x=>x.active):[]
      const dentists=dentistsResult.status==='fulfilled'?dentistsResult.value.some(x=>x.active):false
      const resources=await Promise.allSettled(branches.map(async branch=>{
        const [hours,chairs]=await Promise.all([api<Array<unknown>>(`/api/clinic-management/branches/${branch.id}/hours`),api<Array<{active:boolean}>>(`/api/clinic-management/branches/${branch.id}/chairs`)
        ])
        return {hours:hours.length>0,chair:chairs.some(x=>x.active)}
      }))
      if(active)setSetup({hours:resources.some(x=>x.status==='fulfilled'&&x.value.hours),chair:resources.some(x=>x.status==='fulfilled'&&x.value.chair),dentist:dentists})
    }
    void load()
    const refresh=()=>void load()
    window.addEventListener('clinic-seed-data',refresh)
    return()=>{active=false;window.removeEventListener('clinic-seed-data',refresh)}
  },[role,data?.active_chairs])
  useEffect(()=>{const show=()=>{window.localStorage.removeItem('clinic.readiness.hidden');setHidden(false)};window.addEventListener('clinic:show-readiness',show);return()=>window.removeEventListener('clinic:show-readiness',show)},[])
  if(!['OWNER','ADMIN'].includes(role))return null
  if(hidden)return <section className="ws-panel ws-readiness-restored"><span className="ws-readiness-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m9 12 2 2 4-4M7 3h10a2 2 0 0 1 2 2v14H5V5a2 2 0 0 1 2-2Z"/></svg></span><span><strong>Clinic setup checklist is hidden</strong><small>You can bring the readiness steps back at any time.</small></span><button type="button" onClick={()=>{window.localStorage.removeItem('clinic.readiness.hidden');setHidden(false)}}>Show checklist</button></section>
  const tasks=[
    {label:'Register your first patient',done:(data?.patients_registered??0)>0,to:'/workspace/patients',kind:'patient'},
    {label:'Add an active dental chair',done:setup?.chair??false,to:'/workspace/chairs',kind:'chair'},
    {label:'Set branch opening hours',done:setup?.hours??false,to:'/workspace/branches',kind:'hours'},
    {label:'Create a dentist profile',done:setup?.dentist??false,to:'/workspace/staff',kind:'dentist'},
    {label:'Book your first appointment',done:(data?.appointments_today??0)>0,to:'/workspace/calendar',kind:'appointment'}
  ]
  const complete=tasks.filter(x=>x.done).length
  return <section className="ws-panel ws-readiness-card" aria-label="Clinic setup readiness">
    <header className="ws-readiness-heading"><span className="ws-readiness-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m9 12 2 2 4-4M7 3h10a2 2 0 0 1 2 2v14H5V5a2 2 0 0 1 2-2Z"/><path d="M9 3V2h6v1"/></svg></span><div><span className="ws-eyebrow">Getting started</span><h2>Set up your clinic for daily use</h2><p>Complete these essentials to get your practice ready.</p></div><span className="ws-readiness-progress"><strong>{complete}/{tasks.length}</strong><span>{Math.round(complete/tasks.length*100)}%</span></span></header>
    <div className="ws-readiness-meter" role="progressbar" aria-label="Clinic setup completion" aria-valuemin={0} aria-valuemax={tasks.length} aria-valuenow={complete}><i style={{width:`${complete/tasks.length*100}%`}}/></div>
    <div className="ws-readiness-tasks">{tasks.map(task=><NavLink key={task.kind} to={task.to} className={task.done?'is-done':''}><span className="ws-readiness-check">{task.done?<svg viewBox="0 0 20 20" fill="none"><path d="m4 10 4 4 8-8"/></svg>:<i/>}</span><span>{task.label}</span><svg className="ws-readiness-arrow" viewBox="0 0 20 20" fill="none"><path d="M4 10h11m-4-4 4 4-4 4"/></svg></NavLink>)}</div>
    <footer className="ws-readiness-footer"><span>{complete===tasks.length?'Your core setup is complete.':'Work through the steps in any order; your progress updates automatically.'}</span><button type="button" onClick={()=>{window.localStorage.setItem('clinic.readiness.hidden','true');setHidden(true)}}>Hide checklist</button></footer>
  </section>
}
type WeatherData={current:{temperature_2m:number;apparent_temperature:number;relative_humidity_2m:number;weather_code:number;wind_speed_10m:number};timezone:string}
function ClinicWeather(){
  const [weather,setWeather]=useState<WeatherData|null>(null),[loading,setLoading]=useState(false),[error,setError]=useState(''),[requested,setRequested]=useState(false)
  const load=useCallback(()=>{
    setRequested(true);setError('')
    if(!navigator.geolocation){setError('Location access is not available in this browser.');return}
    setLoading(true)
    navigator.geolocation.getCurrentPosition(async position=>{
      try{
        const query=new URLSearchParams({latitude:String(position.coords.latitude),longitude:String(position.coords.longitude),current:'temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m',timezone:'auto',temperature_unit:'celsius',wind_speed_unit:'kmh'})
        const response=await fetch(`https://api.open-meteo.com/v1/forecast?${query}`)
        if(!response.ok)throw new Error('Weather service is unavailable right now.')
        const result=await response.json() as WeatherData
        setWeather(result)
      }catch(e){setError(e instanceof Error?e.message:'Could not load local weather.')}
      finally{setLoading(false)}
    },reason=>{setError(reason.code===reason.PERMISSION_DENIED?'Allow location access in your browser to show local weather.':'Could not determine your location. Try again.');setLoading(false)},{enableHighAccuracy:false,timeout:10000,maximumAge:10*60*1000})
  },[])
  useEffect(()=>{load()},[load])
  const weatherLabel=(code:number)=>code===0?'Clear':code<=2?'Partly cloudy':code===3?'Cloudy':[45,48].includes(code)?'Foggy':[51,53,55,56,57].includes(code)?'Drizzle':[61,63,65,66,67,80,81,82].includes(code)?'Rain':[95,96,99].includes(code)?'Thunderstorm':'Showers'
  return <section className="ws-panel ws-weather-card" aria-label="Local weather"><header className="ws-weather-heading"><span className="ws-weather-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><circle cx="8" cy="8" r="3"/><path d="M8 1v2m0 10v2M1 8h2m10 0h2m-9.1-5.1 1.4 1.4m5.4 5.4 1.4 1.4m0-8.2-1.4 1.4M8 8h8a4 4 0 0 1 0 8H7a4 4 0 1 1 1-7.9"/></svg></span><div><span className="ws-eyebrow">Local weather</span><h2>Your day, outside</h2></div>{weather&&<button type="button" className="ws-weather-refresh" onClick={load} disabled={loading} aria-label="Refresh weather">↻</button>}</header>
    {weather?<><div className="ws-weather-current"><strong>{Math.round(weather.current.temperature_2m)}°</strong><span>{weatherLabel(weather.current.weather_code)}<small>Feels like {Math.round(weather.current.apparent_temperature)}° · {weather.timezone.replaceAll('_',' ')}</small></span></div><div className="ws-weather-facts"><span><small>Humidity</small><strong>{Math.round(weather.current.relative_humidity_2m)}%</strong></span><span><small>Wind</small><strong>{Math.round(weather.current.wind_speed_10m)} km/h</strong></span></div></>:<div className="ws-weather-empty"><p>{loading?'Checking local conditions…':error||'See current conditions for your location.'}</p>{!loading&&<button type="button" className="ws-care-secondary" onClick={load}>{requested?'Try again':'Use my location'}</button>}</div>}
    <small className="ws-weather-privacy">Your browser asks for location access when this page opens. Coordinates go to Open-Meteo and aren’t saved.</small>
  </section>
}
function CommandQuickActions({role}:{role:WorkspaceRole}){
  const actions=[
    {to:'/workspace/patients',label:'Register patient',detail:'Add a clinic record',icon:'M12 5v14m7-7H5m14 1a9 9 0 1 0-18 0'},
    {to:'/workspace/calendar',label:'Book appointment',detail:'Schedule a patient visit',icon:'M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13H4V6a1 1 0 0 1 1-1Z'},
    {to:role==='OWNER'||role==='ADMIN'?'/workspace/branches':'/workspace/patient-360',label:role==='OWNER'||role==='ADMIN'?'Clinic setup':'Patient records',detail:role==='OWNER'||role==='ADMIN'?'Manage hours and resources':'Open Patient 360°',icon:'M3 20h18M5 20V8l7-4 7 4v12M9 20v-6h6v6'}
  ]
  return <nav className="ws-command-actions" aria-label="Quick actions">{actions.map((action,i)=><NavLink to={action.to} key={action.label} className={`ws-command-action tone-${i}`}><span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={action.icon}/></svg></span><div><strong>{action.label}</strong><small>{action.detail}</small></div><svg className="ws-command-action-arrow" viewBox="0 0 20 20" fill="none"><path d="M4 10h11m-4-4 4 4-4 4"/></svg></NavLink>)}</nav>
}
function CommandMetrics({data,loading}:{data:DashboardData|null;loading:boolean}){
  const cards=[
    {label:'Appointments today',value:data?.appointments_today??0,detail:'Clinic schedule',to:'/workspace/calendar',tone:'teal',icon:'M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13H4V6a1 1 0 0 1 1-1Z'},
    {label:'Registered patients',value:data?.patients_registered??0,detail:'Active clinic records',to:'/workspace/patients',tone:'blue',icon:'M7 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm-5 7v-1a5 5 0 0 1 10 0v1m4-9v6m-3-3h6'},
    {label:'Patients waiting',value:data?.patients_waiting??0,detail:'Checked in today',to:'/workspace/check-in',tone:'amber',icon:'M5 4v6a4 4 0 0 0 4 4h8m-9 0v5m8-5v5M5 10h12a2 2 0 0 1 2 2v2'},
    {label:'Active chairs',value:data?.active_chairs??0,detail:'Clinic resources',to:'/workspace/chairs',tone:'green',icon:'M5 4v6a3 3 0 0 0 3 3h7m-8 0v4m8-4v4M5 9h10a2 2 0 0 1 2 2v2'}
  ]
  return <div className="ws-command-metrics">{cards.map(card=><NavLink to={card.to} key={card.label} className={`ws-command-metric tone-${card.tone}`}><span className="ws-command-metric-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={card.icon}/></svg></span><span className="ws-command-metric-copy"><small>{card.label}</small><strong>{loading?'…':card.value.toLocaleString()}</strong><span>{card.detail}</span></span><svg className="ws-command-metric-arrow" viewBox="0 0 20 20" fill="none"><path d="M4 10h11m-4-4 4 4-4 4"/></svg></NavLink>)}</div>
}
function DailyFocus({data}:{data:DashboardData|null}){
  const items=[['Confirm appointments',data?.appointments_awaiting_confirmation??0,'/workspace/calendar'],['Seat checked-in patients',data?.patients_waiting??0,'/workspace/check-in'],['Review recalls due',data?.recalls_due??0,'/workspace/patient-360']] as const
  return <section className="ws-panel ws-daily-focus"><header><span className="ws-eyebrow">Today at a glance</span><h2>Daily focus</h2><p>Live items that may need your attention.</p></header>{items.map(([label,count,to])=><NavLink to={to} key={label}><span className={count?'has-items':''}>{count}</span><strong>{label}</strong><svg viewBox="0 0 20 20" fill="none"><path d="M4 10h11m-4-4 4 4-4 4"/></svg></NavLink>)}</section>
}
function CommandCenter({ role }: { role: WorkspaceRole }) {
  const messages: Record<WorkspaceRole, string> = { OWNER: 'Your practice at a glance', ADMIN: 'Clinic operations and configuration', DENTIST: 'Your clinical day, organized', FRONT_DESK: 'Keep appointments and arrivals moving' }
  const {session}=useAuth()
  const [data,setData]=useState<DashboardData|null>(null)
  const [error,setError]=useState('')
  const [loading,setLoading]=useState(true)
  useEffect(()=>{
    let active=true
    const load=()=>{setLoading(true);void api<DashboardData>('/api/workspace/dashboard').then(result=>{if(active){setData(result);setError('')}}).catch(e=>{if(active)setError(e instanceof Error?e.message:'Could not load clinic dashboard')}).finally(()=>{if(active)setLoading(false)})}
    load()
    window.addEventListener('clinic-seed-data',load)
    return ()=>{active=false;window.removeEventListener('clinic-seed-data',load)}
  },[])
  const value=(amount:number|undefined)=>loading?'…':String(amount??0)
  const hour=new Date().getHours(),greeting=hour<12?'Good morning':hour<18?'Good afternoon':'Good evening'
  const dateLabel=data?.clinicDate?new Intl.DateTimeFormat(undefined,{weekday:'long',month:'long',day:'numeric'}).format(new Date(`${data.clinicDate}T12:00:00`)):'Your clinic overview'
  return <><div className="ws-heading ws-command-heading"><div><span className="ws-eyebrow">Command Center / {DISPLAY_ROLES[role]}</span><h1>{greeting}{session?.user.fullName?`, ${session.user.fullName.split(' ')[0]}`:''} 👋</h1><p>{dateLabel} · {messages[role]}</p></div><span className="ws-pill">Live clinic overview</span></div>
    {error&&<p role="alert" className="ws-global-warning">{error}</p>}
    {['OWNER','ADMIN'].includes(role)&&<ClinicReadiness role={role} data={data}/>}
    <CommandQuickActions role={role}/>
    <div className="ws-command-overview-grid">{['OWNER','ADMIN'].includes(role)?<div className="ws-command-attention-slot"><div className="ws-panel ws-sidecard ws-needs-attention"><header><span className="ws-eyebrow">Practice operations</span><h2>Needs attention</h2><p>Items to review across today's clinic.</p></header><NavLink to="/workspace/calendar"><span className="ws-attention-count is-booked">{value(data?.appointments_awaiting_confirmation)}</span><span><strong>Appointments awaiting confirmation</strong><small>Review the schedule and confirm visits</small></span><b aria-hidden="true">›</b></NavLink><NavLink to="/workspace/check-in"><span className="ws-attention-count is-arrived">{value(data?.patients_waiting)}</span><span><strong>Patients waiting</strong><small>Move checked-in patients to a chair</small></span><b aria-hidden="true">›</b></NavLink><NavLink to="/workspace/patient-360"><span className="ws-attention-count is-recall">{value(data?.recalls_due)}</span><span><strong>Recalls due</strong><small>Follow up with patients due for care</small></span><b aria-hidden="true">›</b></NavLink><NavLink to="/workspace/patient-360" className="ws-primary-link">Open Patient 360° →</NavLink></div></div>:<DailyFocus data={data}/>}<ClinicWeather/></div>
    <CommandMetrics data={data} loading={loading}/>
    <div className="ws-columns ws-command-lower"><div><DayTimeline/></div></div>
  </>
}
async function compressPatientPhoto(file:File) {
  const image=await createImageBitmap(file)
  try {
    let scale=Math.min(1,512/Math.max(image.width,image.height))
    for(let resize=0;resize<10;resize++,scale*=.8){
      const canvas=document.createElement('canvas')
      canvas.width=Math.max(48,Math.round(image.width*scale));canvas.height=Math.max(48,Math.round(image.height*scale))
      const context=canvas.getContext('2d')
      if(!context)throw new Error('Could not prepare this image')
      context.fillStyle='#fff';context.fillRect(0,0,canvas.width,canvas.height);context.drawImage(image,0,0,canvas.width,canvas.height)
      for(const quality of [.86,.76,.66,.56,.46,.36]){
        const blob=await new Promise<Blob|null>(resolve=>canvas.toBlob(resolve,'image/jpeg',quality))
        if(blob&&blob.size<100*1024){
          const dataUrl=await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>typeof reader.result==='string'?resolve(reader.result):reject(new Error('Could not read the resized image'));reader.onerror=()=>reject(new Error('Could not read the resized image'));reader.readAsDataURL(blob)})
          return dataUrl
        }
      }
    }
    throw new Error('Could not reduce this image below 100 KB')
  } finally { image.close() }
}

function PatientProfilePhoto({patientId,firstName,lastName,editable}:{patientId:string;firstName:string;lastName:string;editable:boolean}) {
  const [photo,setPhoto]=useState('')
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  useEffect(()=>{let live=true;void api<{dataUrl:string}>(`/api/patients/${encodeURIComponent(patientId)}/photo`).then(result=>{if(live)setPhoto(result.dataUrl)}).catch(e=>{if(live)setError(e instanceof Error?e.message:'Could not load patient photo')});return()=>{live=false}},[patientId])
  const upload=async(file:File)=>{
    if(!['image/png','image/jpeg'].includes(file.type)){setError('Choose a PNG or JPEG image');return}
    if(file.size>20*1024*1024){setError('Choose an image smaller than 20 MB');return}
    setBusy(true);setError('')
    try{const dataUrl=await compressPatientPhoto(file);const result=await api<{dataUrl:string}>(`/api/patients/${encodeURIComponent(patientId)}/photo`,{method:'PUT',body:JSON.stringify({dataUrl})});setPhoto(result.dataUrl)}
    catch(e){setError(e instanceof Error?e.message:'Could not save patient photo')}
    finally{setBusy(false)}
  }
  const remove=()=>{setBusy(true);setError('');void api(`/api/patients/${encodeURIComponent(patientId)}/photo`,{method:'DELETE'}).then(()=>setPhoto('')).catch(e=>setError(e instanceof Error?e.message:'Could not remove patient photo')).finally(()=>setBusy(false))}
  return <div className="ws-patient-photo-control"><div className="ws-avatar ws-patient-profile-photo">{photo?<img src={photo} alt={`${firstName} ${lastName}`} />:<span aria-hidden="true">{firstName.slice(0,1)}{lastName.slice(0,1)}</span>}</div>{editable&&<div className="ws-patient-photo-actions"><label className="ws-patient-photo-icon-button" title={photo?'Change photo':'Add photo'} aria-label={photo?'Change patient photo':'Add patient photo'}><svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M3 6.5h3l1.3-2h5.4l1.3 2h3v9H3z"/><circle cx="10" cy="11" r="3"/></svg><input type="file" accept="image/png,image/jpeg" disabled={busy} onChange={event=>{const file=event.currentTarget.files?.[0];if(file)void upload(file);event.currentTarget.value=''}}/></label>{photo&&<button type="button" className="ws-patient-photo-icon-button is-remove" title="Remove photo" aria-label="Remove patient photo" disabled={busy} onClick={remove}><svg viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="M3.5 5.5h13m-11.5 0 .8 11h8.4l.8-11M8 5.5V3.7h4v1.8m-3 3v5m2-5v5"/></svg></button>}</div>}{error&&<small role="alert" className="ws-patient-photo-error">{error}</small>}</div>
}

function Patient360({ role }: { role: WorkspaceRole }) {
  const [params,setParams] = useSearchParams()
  const query = ''
  const [rows,setRows] = useState<Patient[]>([])
  const [page,setPage] = useState(0)
  const [total,setTotal] = useState(0)
  const [view,setView] = useState<'card'|'list'>(()=>window.localStorage.getItem('patient-360-view')==='list'?'list':'card')
  const [loaded,setLoaded] = useState<{id:string;patient:Patient}|null>(null)
  const [error,setError] = useState('')
  const [loading,setLoading] = useState(false)
  const [tab,setTab] = useState('overview')
  const requestVersion = useRef(0)
  const pageSize=12
  const selectedId = params.get('patient')
  useEffect(() => {
    let live=true
    if(!selectedId)return
    void api<Patient>(`/api/patients/${encodeURIComponent(selectedId)}`).then(p=>{if(live){setLoaded({id:selectedId,patient:p})}}).catch(e=>{if(live){setLoaded(null);setError(e instanceof Error?e.message:'Patient not available')}})
    return ()=>{live=false}
  },[selectedId])
  useEffect(()=>{window.localStorage.setItem('patient-360-view',view)},[view])
  useEffect(()=>{
    if(selectedId)return
    const version=++requestVersion.current
    const timer=setTimeout(()=>{
      setLoading(true)
      void (async()=>{const [result,count]=await Promise.all([api<Patient[]>(`/api/patients?q=${encodeURIComponent(query)}&limit=${pageSize}&offset=${page*pageSize}`),api<{count:number}>(`/api/patients/count?q=${encodeURIComponent(query)}`)]);if(version!==requestVersion.current)return;setRows(result);setTotal(count.count);setError('')})().catch(e=>{if(version===requestVersion.current)setError(e instanceof Error?e.message:'Search failed')}).finally(()=>{if(version===requestVersion.current)setLoading(false)})
    },250)
    return ()=>{clearTimeout(timer)}
  },[query,page,selectedId])
  useEffect(()=>{const refresh=()=>{const version=++requestVersion.current;void Promise.all([api<Patient[]>(`/api/patients?q=${encodeURIComponent(query)}&limit=${pageSize}&offset=${page*pageSize}`),api<{count:number}>(`/api/patients/count?q=${encodeURIComponent(query)}`)]).then(([result,count])=>{if(version===requestVersion.current){setRows(result);setTotal(count.count)}}).catch(e=>{if(version===requestVersion.current)setError(e instanceof Error?e.message:'Search failed')})};window.addEventListener('clinic-seed-data',refresh);return()=>window.removeEventListener('clinic-seed-data',refresh)},[query,page])
  const open=(id:string)=>{setTab('overview');setParams({patient:id})}
  const back=()=>{setParams({});setLoaded(null);setError('')}
  return <>
    <div className="ws-heading ws-patient-page-heading"><div className="ws-patient-page-title"><PatientSectionIcon kind="patients"/><div><span className="ws-eyebrow">Clinical / Patient 360° · M2.3</span><h1>Patient 360°</h1></div></div>{selectedId&&<div className="ws-patient-360-actions"><button type="button" className="ws-care-secondary" onClick={back}><span className="ws-action-icon-tile is-back" aria-hidden="true"><svg className="ws-action-icon" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5 7 10l5.5 5.5M7.5 10h9"/></svg></span>Return to patient search</button><NavLink className="ws-care-secondary" to="/workspace/patients"><span className="ws-action-icon-tile is-patients" aria-hidden="true"><svg className="ws-action-icon" viewBox="0 0 20 20" fill="none"><circle cx="8" cy="6.5" r="3"/><path d="M2.8 16c.5-2.5 2.4-3.8 5.2-3.8s4.7 1.3 5.2 3.8M15 6v5m-2.5-2.5h5"/></svg></span>Patient registry</NavLink></div>}</div>
    {error&&(!selectedId||loaded?.id===selectedId)&&<p role="alert" className="ws-global-warning">{error}</p>}
    {!selectedId&&<section className="ws-panel ws-patient-browser"><div className="ws-patient-toolbar"><div className="ws-patient-toolbar-finder"><PatientFinder label="Find a patient" onSelect={patient=>open(patient.id)}/></div><div className="ws-view-switch" role="group" aria-label="Patient display"><button type="button" aria-pressed={view==='card'} className={view==='card'?'active':''} onClick={()=>setView('card')}><svg aria-hidden="true" viewBox="0 0 20 20" fill="none"><rect x="2.5" y="2.5" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.5"/><rect x="11.5" y="2.5" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.5"/><rect x="2.5" y="11.5" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.5"/><rect x="11.5" y="11.5" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.5"/></svg><span>Cards</span></button><button type="button" aria-pressed={view==='list'} className={view==='list'?'active':''} onClick={()=>setView('list')}><svg aria-hidden="true" viewBox="0 0 20 20" fill="none"><path d="M7 4h10M7 10h10M7 16h10M3 4h.01M3 10h.01M3 16h.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg><span>List</span></button></div></div><div className="ws-patient-results"><p className="ws-subtle" aria-live="polite">{loading?'Loading patients…':`${total} patient(s) · page ${page+1}`}</p>{loading?<p className="ws-list-empty">Loading patients…</p>:rows.length===0?<p className="ws-list-empty">No patients found in this clinic.</p>:<div className={`ws-patient-cards ${view==='list'?'list-view':''}`}>{rows.map(p=><button type="button" className="ws-patient-card" key={p.id} onClick={()=>open(p.id)}><span className="ws-patient-avatar" aria-hidden="true">{p.first_name.slice(0,1)}{p.last_name.slice(0,1)}</span><span className="ws-patient-card-main"><strong>{p.first_name} {p.last_name}</strong><small>{p.chart_no}</small></span><span className="ws-patient-card-meta"><small>{p.phone??'No phone recorded'}</small><small>{p.birth_date??'Date of birth not recorded'}</small></span><span className="ws-patient-card-arrow" aria-hidden="true">›</span></button>)}</div>}</div><div className="ws-pagination"><span>Showing {rows.length?`${page*pageSize+1}–${page*pageSize+rows.length}`:'0'}{total>page*pageSize+rows.length?` of at least ${total}`:` of ${total}`}</span><div><button type="button" disabled={page===0||loading} onClick={()=>setPage(p=>Math.max(0,p-1))}>Previous</button><span>Page {page+1}</span><button type="button" disabled={rows.length<pageSize||loading} onClick={()=>setPage(p=>p+1)}>Next</button></div></div></section>}
    {selectedId&&<>{loaded?.id===selectedId&&(()=>{const patient=loaded.patient;return <>
      <div className="ws-panel ws-patient-head"><PatientProfilePhoto patientId={patient.id} firstName={patient.first_name} lastName={patient.last_name} editable={['OWNER','ADMIN','DENTIST','FRONT_DESK'].includes(role)}/><div><h2>{patient.first_name} {patient.last_name}</h2><p>{patient.chart_no} · Date of birth: {patient.birth_date??'Not recorded'} · {patient.phone??'No phone listed'}</p></div></div>
      <div className="ws-tabs" role="tablist" aria-label="Patient sections">{[['overview','Overview'],['care','History, guardians & consent'],['chart','Odontogram'],['visits','Visits & SOAP'],['plans','Treatment plans'],['billing','Billing']].map(([id,label])=><button key={id} role="tab" aria-selected={tab===id} className={tab===id?'selected':''} onClick={()=>setTab(id)}>{label}</button>)}</div>
      {tab==='overview'&&<div className="ws-panel ws-management"><header className="ws-care-subheading"><PatientSectionIcon kind="patients"/><h3>Patient details</h3></header><div className="ws-info-grid"><div><small>Name</small><strong>{patient.first_name} {patient.last_name}</strong></div><div><small>Chart number</small><strong>{patient.chart_no}</strong></div><div><small>Birth date</small><strong>{patient.birth_date??'Not recorded'}</strong></div><div><small>Contact</small><strong>{patient.phone??'Not recorded'}</strong></div><div><small>Email</small><strong>{patient.email??'Not recorded'}</strong></div></div><p>Medical history and consent records are available under History, guardians & consent, subject to your access level.</p><NavLink to="/workspace/patients">Edit demographics in Patient Registry →</NavLink></div>}
      {tab==='care'&&<PatientCare key={patient.id} patient={patient} role={role}/>}
      {tab==='visits'&&(['OWNER','ADMIN','DENTIST'].includes(role)?<PatientEncounters key={patient.id} patientId={patient.id}/>:<p role="alert">Clinical encounter access is restricted.</p>)}
      {tab==='chart'&&(['OWNER','ADMIN','DENTIST'].includes(role)?<Odontogram key={patient.id} patientId={patient.id}/>:<p role="alert">Clinical chart access is restricted.</p>)}
      {tab==='plans'&&(['OWNER','ADMIN','DENTIST'].includes(role)?<PatientTreatmentPlans key={patient.id} patientId={patient.id} role={role}/>:<p role="alert">Treatment planning access is restricted.</p>)}
      {tab==='billing'&&(['OWNER','ADMIN','FRONT_DESK'].includes(role)?<PatientBilling key={patient.id} patientId={patient.id} role={role}/>:<p role="alert">Billing access is restricted.</p>)}
    </>})()}</>}
  </>
}
function BillingPreview({role}:{role:WorkspaceRole}){
 const [metrics,setMetrics]=useState<{currency:string;invoiced:number;received:number;outstanding:number;collectionsToday:number;paymentMethods:Array<{method:string;amount:number}>}|null>(null)
 const [error,setError]=useState('')
 useEffect(()=>{let active=true;if(!['OWNER','ADMIN','FRONT_DESK'].includes(role))return
  void api<{currency:string;invoiced:number;received:number;outstanding:number;collectionsToday:number;paymentMethods:Array<{method:string;amount:number}>}>('/api/billing/dashboard')
   .then(x=>{if(active)setMetrics(x)}).catch(e=>{if(active)setError(describeError(e,'Could not load the dashboard metrics'))});return()=>{active=false}
 },[role])
 if(!['OWNER','ADMIN','FRONT_DESK'].includes(role))return <p role="alert">Financial metrics are restricted to authorized billing staff.</p>
 const amount=(n:number)=>new Intl.NumberFormat('en',{style:'currency',currency:metrics?.currency||'PHP'}).format(Number(n))
 return <><div className="ws-heading"><div><span className="ws-eyebrow">Revenue / Actual</span><h1>Billing overview</h1>
 <p>Financial ledger totals from the active clinic, excluding voided invoices and payments.</p></div></div>
 {error&&<p role="alert">{error}</p>}
 {metrics?<><Metrics values={[{label:'Total invoiced',value:amount(metrics.invoiced),detail:'Issued invoices'},
 {label:'Collected',value:amount(metrics.received),detail:'Non-void payments'},
 {label:'Outstanding',value:amount(metrics.outstanding),detail:'Unpaid clinic balances'},
 {label:'Collections today',value:amount(metrics.collectionsToday),detail:'Server calendar day'}]}/>
 <section className="ws-panel"><h3>Collections by payment method</h3><ul>{metrics.paymentMethods.map(p=><li key={p.method}>{p.method}: {amount(p.amount)}</li>)}</ul></section></>:
 !error?<p>Loading current billing ledger…</p>:null}
 <p>Select a patient in <NavLink to="/workspace/patient-360">Patient 360°</NavLink> and open the Billing tab to manage invoices and payments.</p>
 </>
}
function WorkspaceTopbar({clinicName,fullName,email,role,theme,navOpen,onMenu,onThemeToggle,onSignOut}:{clinicName:string;fullName:string;email:string;role:WorkspaceRole;theme:'light'|'dark';navOpen:boolean;onMenu:()=>void;onThemeToggle:()=>void;onSignOut:()=>void}){
 const {branches,branchId,setBranchId}=useClinicBranch()
 const [profileOpen,setProfileOpen]=useState(false)
 const [profileDetailsOpen,setProfileDetailsOpen]=useState(false)
  const initials=(fullName||'').trim().split(/\s+/).filter(Boolean).slice(0,2).map(part=>part[0]).join('').toUpperCase()||'U'
 const branchOptions=branches.map(branch=>({value:branch.id,label:`${branch.name}${branch.active?'':' (inactive)'}`}))
 return <header className="ws-topbar">
  <button className="ws-menu" onClick={onMenu} aria-label={navOpen?'Close navigation':'Open navigation'} aria-expanded={navOpen}>☰</button>
  <div className="ws-top-context"><b>{clinicName}</b><span> / {DISPLAY_ROLES[role]}</span></div>
  <div className="ws-top-actions">
   <SeedDataControls/>
   <div className="ws-global-branch-inline"><Dropdown label="Active branch" visuallyHidden value={branchId} placeholder="Select a branch" options={branchOptions} onChange={setBranchId} disabled={!branches.length}/></div>
   <button type="button" onClick={onThemeToggle} aria-label="Toggle theme">{theme==='light'?'☾':'☀'}</button>
   <div className={`ws-profile-menu${profileOpen?' is-open':''}`}>
    <button type="button" className="ws-profile-trigger" aria-label="Open account menu" aria-expanded={profileOpen} onClick={()=>setProfileOpen(open=>!open)}><span className="ws-profile-avatar">{initials.slice(0,1)}</span><span className="ws-profile-chevron" aria-hidden="true">⌄</span></button>
    {profileOpen&&<div className="ws-profile-popover"><div className="ws-profile-summary"><span className="ws-profile-avatar">{initials.slice(0,1)}</span><div><strong>{fullName||''}</strong><span>{email||''}</span><small>{DISPLAY_ROLES[role]}</small></div></div><div className="ws-profile-mobile-branch"><span>Active branch</span><Dropdown label="Active branch" visuallyHidden value={branchId} placeholder="Select a branch" options={branchOptions} onChange={id=>{setBranchId(id);setProfileOpen(false)}} disabled={!branches.length}/></div><button type="button" className="ws-profile-item" onClick={()=>{setProfileOpen(false);setProfileDetailsOpen(true)}}><span aria-hidden="true">♙</span><span><strong>My Profile</strong><small>Account and contact details</small></span></button><NavLink to="/workspace/billing" onClick={()=>setProfileOpen(false)}><span aria-hidden="true">▣</span><span><strong>Plans &amp; billing</strong><small>Subscription and payment options</small></span></NavLink><NavLink to="/workspace/settings" onClick={()=>setProfileOpen(false)}><span aria-hidden="true">⚙</span><span><strong>Clinic settings</strong><small>Practice details and configuration</small></span></NavLink><button type="button" className="ws-profile-signout" onClick={onSignOut}><span aria-hidden="true">↪</span><span><strong>Sign out</strong><small>End this session</small></span></button></div>}
   </div>
  </div>
  {profileDetailsOpen&&<div className="ws-profile-modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setProfileDetailsOpen(false)}}><section className="ws-profile-modal" role="dialog" aria-modal="true" aria-labelledby="ws-profile-title"><header><div><span className="ws-eyebrow">Account</span><h2 id="ws-profile-title">My Profile</h2></div><button type="button" className="ws-icon-button" aria-label="Close profile" onClick={()=>setProfileDetailsOpen(false)}>×</button></header><div className="ws-profile-modal-details"><span className="ws-profile-avatar">{initials.slice(0,1)}</span><div><small>Name</small><strong>{fullName||''}</strong></div><div><small>Email</small><strong>{email||''}</strong></div><div><small>Role</small><strong>{DISPLAY_ROLES[role]}</strong></div><div><small>Clinic</small><strong>{clinicName}</strong></div></div></section></div>}
 </header>
}

export function ClinicWorkspace(){const {session,signOut}=useAuth();const {moduleId='command-center'}=useParams();const [drawer,setDrawer]=useState(()=>window.innerWidth>780&&window.innerWidth<=1100);const [theme,setTheme]=useState<'light'|'dark'>(()=>window.localStorage.getItem('dental-theme')==='dark'?'dark':'light');useEffect(()=>{window.localStorage.setItem('dental-theme',theme)},[theme]);if(!session)return null;if(session.user.platformAdmin||!session.clinic)return <Navigate to="/platform-admin" replace/>;const role=session.user.role as WorkspaceRole;const mod=WORKSPACE_MODULES.find(m=>m.id===moduleId);if(!mod||!canSeeModule(moduleId,role))return <Navigate to="/workspace/command-center" replace/>;const groups=[...new Set(WORKSPACE_MODULES.map(m=>m.group))];return <div className="ws-app" data-theme={theme}><ToastProvider><ClinicBranchProvider><aside className={'ws-sidebar '+(drawer?'open':'closed')}><div className="ws-brand"><span className="ws-brand-icon">✳</span><div><strong>Dental Clinic SaaS</strong><small>Practice workspace</small></div><button className="ws-close" onClick={()=>setDrawer(false)} aria-label="Close menu">×</button></div><nav aria-label="Clinic workspace">{groups.map(g=><div key={g} className="ws-navgroup"><span>{g}</span>{WORKSPACE_MODULES.filter(m=>m.group===g&&m.roles.includes(role)).map(m=><NavLink key={m.id} onClick={()=>setDrawer(false)} to={'/workspace/'+m.id} className={({isActive})=>isActive?'current':''}><span className="ws-nav-link-main"><WorkspaceNavIcon id={m.id}/><span>{m.title}</span></span>{m.id!=='command-center'&&<small>{m.state==='coming-soon'?'Soon':m.state==='preview'?'Demo':'Active'}</small>}</NavLink>)}</div>)}</nav></aside><div className="ws-main"><WorkspaceTopbar clinicName={session.clinic.name} fullName={session.user.fullName} email={session.user.email} role={role} theme={theme} navOpen={drawer} onMenu={()=>setDrawer(open=>!open)} onThemeToggle={()=>setTheme(theme==='light'?'dark':'light')} onSignOut={()=>void signOut()}/><main className="ws-body">{moduleId==='reports'&&<p className="ws-global-warning">Reports use real clinic-scoped records. Figures are operational summaries and not certified financial statements. Use synthetic test records until production approval.</p>}{moduleId==='command-center'?<CommandCenter role={role}/>:moduleId==='patient-360'?<Patient360 role={role}/>:moduleId==='odontogram'?<Navigate to="/workspace/patient-360" replace/>:moduleId==='journey'?<JourneyPreview role={role}/>:moduleId==='calendar'?<SchedulingWorkspace role={role}/>:moduleId==='check-in'?<SchedulingWorkspace role={role} mode="check-in"/>:moduleId==='patients'?<PatientRegistry role={role}/>:moduleId==='billing'?<BillingPreview role={role}/>:moduleId==='reminders'?<NotificationWorkspace role={role}/>:moduleId==='reports'?<ReportsWorkspace/>:moduleId==='module-status'?<ModuleStatusPreview role={role}/>:['branches','chairs','staff','settings'].includes(moduleId)?<ClinicManagement section={moduleId as 'branches'|'chairs'|'staff'|'settings'}/>:<ComingSoon title={mod.title} description={mod.description}/>}</main></div>{drawer&&window.innerWidth<=780&&<button className="ws-backdrop" onClick={()=>setDrawer(false)} aria-label="Close navigation"/>}</ClinicBranchProvider></ToastProvider></div>}
