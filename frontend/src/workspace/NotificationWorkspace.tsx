import {useEffect,useState} from 'react'
import {api} from '../api/client'
type Delivery={id:string;patient_id:string;category:string;channel:string;status:string;scheduled_for:string;attempts:number;failure_reason:string|null}
type Recall={id:string;patient_id:string;due_date:string;completed:boolean}
type Policy={enabled:boolean;email_enabled:boolean;sms_enabled:boolean;reminder_hours_before:number}
export function NotificationWorkspace({role}:{role:string}){
 const admin=['OWNER','ADMIN'].includes(role)
 const [policy,setPolicy]=useState<Policy|null>(null),[queue,setQueue]=useState<Delivery[]>([]),[recalls,setRecalls]=useState<Recall[]>([])
 const [patientId,setPatientId]=useState(''),[appointmentId,setAppointmentId]=useState(''),[recallDate,setRecallDate]=useState('')
 const [channel,setChannel]=useState('EMAIL'),[consent,setConsent]=useState(false),[error,setError]=useState(''),[busy,setBusy]=useState(false)
 async function load(){const [p,q,r]=await Promise.all([api<Policy>('/api/notifications/policy'),api<Delivery[]>('/api/notifications/queue'),api<Recall[]>('/api/notifications/recalls')]);setPolicy(p);setQueue(q);setRecalls(r)}
 useEffect(()=>{void load().catch(e=>setError(String(e)))},[])
 async function submit(url:string,method:string,body?:unknown){
  setBusy(true);setError('')
  try{await api(url,{method, ...(body===undefined?{}:{body:JSON.stringify(body)})});await load()}
  catch(e){setError(e instanceof Error?e.message:'Notification request failed')}finally{setBusy(false)}
 }
 return <section className="ws-panel" aria-label="Reminders and recalls">
  <h1>Reminders & Recalls</h1>
  <p>Opt-in scheduling and clinic review. <strong>External email/SMS sending is not configured</strong>; queue entries do not mean a message was sent.</p>
  {error&&<p role="alert">{error}</p>}
  {policy&&<section><h2>Delivery policy</h2><p>Queue active: {policy.enabled?'Yes':'No'} · Email: {policy.email_enabled?'Enabled':'Disabled'} · SMS: {policy.sms_enabled?'Enabled':'Disabled'}</p>
   {admin&&<><label><input type="checkbox" checked={policy.enabled} onChange={e=>setPolicy({...policy,enabled:e.target.checked})}/> Enable queue</label>
   <label><input type="checkbox" checked={policy.email_enabled} onChange={e=>setPolicy({...policy,email_enabled:e.target.checked})}/> Email opt-in queue</label>
   <label><input type="checkbox" checked={policy.sms_enabled} onChange={e=>setPolicy({...policy,sms_enabled:e.target.checked})}/> SMS opt-in queue</label>
   <label>Hours before appointment <input type="number" min={1} max={720} value={policy.reminder_hours_before} onChange={e=>setPolicy({...policy,reminder_hours_before:Number(e.target.value)})}/></label>
   <button disabled={busy||policy.reminder_hours_before<1||policy.reminder_hours_before>720} onClick={()=>void submit('/api/notifications/policy','PUT',{
    enabled:policy.enabled,emailEnabled:policy.email_enabled,smsEnabled:policy.sms_enabled,reminderHoursBefore:policy.reminder_hours_before
   })}>Save policy</button></>}</section>}
  <section><h2>Consent & scheduling</h2>
   <label>Patient UUID <input value={patientId} onChange={e=>setPatientId(e.target.value)} placeholder="Patient ID"/></label>
   <label>Channel <select value={channel} onChange={e=>setChannel(e.target.value)}><option>EMAIL</option><option>SMS</option></select></label>
   {admin&&<><label><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/> Consent obtained</label>
   <button disabled={busy||!patientId} onClick={()=>void submit('/api/notifications/consents','PUT',{patientId,channel,consented:consent})}>Save consent</button></>}
   <label>Appointment UUID <input value={appointmentId} onChange={e=>setAppointmentId(e.target.value)}/></label>
   <button disabled={busy||!appointmentId} onClick={()=>void submit('/api/notifications/appointments/'+encodeURIComponent(appointmentId)+'/schedule?channel='+channel,'POST')}>Queue appointment reminder</button>
   <label>Recall date <input type="date" value={recallDate} onChange={e=>setRecallDate(e.target.value)}/></label>
   <button disabled={busy||!patientId||!recallDate} onClick={()=>void submit('/api/notifications/recalls','POST',{patientId,dueDate:recallDate,reason:'Routine dental checkup'})}>Create recall</button>
  </section>
  <section><h2>Pending recalls</h2><ul>{recalls.map(r=><li key={r.id}>{r.due_date} — patient {r.patient_id.slice(0,8)} <button disabled={busy} onClick={()=>void submit('/api/notifications/recalls/'+r.id+'/schedule?channel='+channel,'POST')}>Queue {channel}</button></li>)}</ul></section>
  <section><h2>Notification center</h2><table><thead><tr><th>Type</th><th>Channel</th><th>Scheduled</th><th>Status</th></tr></thead><tbody>
   {queue.map(d=><tr key={d.id}><td>{d.category}</td><td>{d.channel}</td><td>{new Date(d.scheduled_for).toLocaleString()}</td><td>{d.status}</td></tr>)}
  </tbody></table><button disabled={busy} onClick={()=>void load().catch(e=>setError(String(e)))}>Refresh queue</button></section>
 </section>
}
