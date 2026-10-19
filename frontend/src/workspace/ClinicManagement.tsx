import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { StaffOnboarding } from './StaffOnboarding'

type Branch = { id:string; name:string; address:string|null; phone:string|null; active:boolean }
type Chair = { id:string; name:string; active:boolean }
type Staff = { id:string; full_name:string; email:string; role:string; active:boolean }
type Hour = { day_of_week:number; opens_at:string; closes_at:string }
type Clinic = { name:string; timezone:string; email:string|null; phone:string|null; address:string|null; slug:string; currency:string }
const path='/api/clinic-management'
const days=['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday']
const json=(value:unknown)=>({headers:{'Content-Type':'application/json'},body:JSON.stringify(value)})
function ErrorLine({error}:{error:string}) {return error?<p role="alert" className="ws-alert">{error}</p>:null}

export function ClinicManagement({section}:{section:'branches'|'chairs'|'staff'|'settings'}) {
 const [branches,setBranches]=useState<Branch[]>([])
 const [selected,setSelected]=useState('')
 const [chairs,setChairs]=useState<Chair[]>([])
 const [staff,setStaff]=useState<Staff[]>([])
 const [assigned,setAssigned]=useState<Staff[]>([])
 const [hours,setHours]=useState<Hour[]>([])
 const [clinic,setClinic]=useState<Clinic|null>(null)
 const [name,setName]=useState('')
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const [notice,setNotice]=useState('')
 const refreshBranches=async()=>{const values=await api<Branch[]>(path+'/branches');setBranches(values);setSelected(previous=>values.some(v=>v.id===previous)?previous:values[0]?.id??'')}
 useEffect(()=>{let active=true;void Promise.all([api<Branch[]>(path+'/branches'),api<Clinic>(path+'/clinic')]).then(([b,c])=>{if(active){setBranches(b);setSelected(b[0]?.id??'');setClinic(c)}}).catch(e=>{if(active)setError(String(e))});return()=>{active=false}},[])
 useEffect(()=>{if(!selected)return;let active=true;void Promise.all([api<Chair[]>(`${path}/branches/${selected}/chairs`),api<Hour[]>(`${path}/branches/${selected}/hours`),api<Staff[]>(`${path}/branches/${selected}/staff`),api<Staff[]>(path+'/staff')]).then(([c,h,a,s])=>{if(active){setChairs(c);setHours(h);setAssigned(a);setStaff(s)}}).catch(e=>{if(active)setError(String(e))});return()=>{active=false}},[selected])
 async function change(action:()=>Promise<unknown>,message:string){setBusy(true);setError('');setNotice('');try{await action();await refreshBranches();setNotice(message)}catch(e){setError(String(e))}finally{setBusy(false)}}
 const branch=branches.find(b=>b.id===selected)
 return <>
  <div className="ws-heading"><div><span className="ws-eyebrow">Administration / Live clinic configuration</span><h1>{section==='settings'?'Clinic Settings':section==='staff'?'Staff assignments':section==='chairs'?'Dental chairs':'Branches & operating hours'}</h1><p>Changes are saved to your clinic. Access restricted to Clinic Owners and Administrators.</p></div><span className="ws-pill">M1 · Live configuration</span></div>
  <ErrorLine error={error}/>{notice&&<p role="status" className="ws-preview-caption">{notice}</p>}
  {section==='settings'&&clinic&&<form className="ws-panel ws-management" onSubmit={e=>{e.preventDefault();void change(()=>api(path+'/clinic',{method:'PUT',...json(clinic)}).then(()=>api<Clinic>(path+'/clinic').then(setClinic)),'Clinic settings saved')}}>
   <label>Clinic name<input required maxLength={160} value={clinic.name} onChange={e=>setClinic({...clinic,name:e.target.value})}/></label>
   <label>IANA timezone<input required value={clinic.timezone} onChange={e=>setClinic({...clinic,timezone:e.target.value})}/></label>
   <label>Email<input value={clinic.email??''} onChange={e=>setClinic({...clinic,email:e.target.value})}/></label>
   <label>Phone<input value={clinic.phone??''} onChange={e=>setClinic({...clinic,phone:e.target.value})}/></label>
   <label>Address<input value={clinic.address??''} onChange={e=>setClinic({...clinic,address:e.target.value})}/></label>
   <p className="ws-subtle">Clinic identifier: {clinic.slug} · Currency: {clinic.currency} (read-only)</p>
   <button disabled={busy} type="submit">Save clinic settings</button>
  </form>}
  {section!=='settings'&&<>
   <div className="ws-panel ws-management"><label>Current branch<select value={selected} onChange={e=>setSelected(e.target.value)}>{branches.map(b=><option key={b.id} value={b.id}>{b.name}{b.active?'':' (inactive)'}</option>)}</select></label>
   {section==='branches'&&<form onSubmit={e=>{e.preventDefault();void change(async()=>{await api(path+'/branches',{method:'POST',...json({name})});setName('')},'Branch created')}}><label>New branch name<input required maxLength={160} value={name} onChange={e=>setName(e.target.value)}/></label><button disabled={busy}>Add branch</button></form>}
   </div>
   {section==='branches'&&branch&&<div className="ws-panel ws-management"><h3>{branch.name} · Branch details</h3><form onSubmit={e=>{e.preventDefault();void change(()=>api(`${path}/branches/${selected}`,{method:'PUT',...json({name:branch.name,address:branch.address,phone:branch.phone})}),'Branch details saved')}}><label>Branch name<input required value={branch.name} onChange={e=>setBranches(branches.map(b=>b.id===selected?{...b,name:e.target.value}:b))}/></label><label>Address<input value={branch.address??''} onChange={e=>setBranches(branches.map(b=>b.id===selected?{...b,address:e.target.value}:b))}/></label><label>Phone<input value={branch.phone??''} onChange={e=>setBranches(branches.map(b=>b.id===selected?{...b,phone:e.target.value}:b))}/></label><button disabled={busy} type="submit">Save branch</button></form><button disabled={busy} onClick={()=>void change(()=>api(`${path}/branches/${selected}/active?active=${!branch.active}`,{method:'PATCH'}),branch.active?'Branch deactivated':'Branch activated')}>{branch.active?'Deactivate branch':'Activate branch'}</button></div>}
   {section==='branches'&&branch&&<div className="ws-panel ws-management"><h3>{branch.name} · Opening hours</h3><p className="ws-subtle">Times are local to the clinic timezone. Closed days are omitted.</p>{hours.map(h=><div className="ws-management-row" key={h.day_of_week}><b>{days[h.day_of_week-1]}</b><input aria-label="Opening time" type="time" value={h.opens_at.slice(0,5)} onChange={e=>setHours(hours.map(v=>v.day_of_week===h.day_of_week?{...v,opens_at:e.target.value}:v))}/><input aria-label="Closing time" type="time" value={h.closes_at.slice(0,5)} onChange={e=>setHours(hours.map(v=>v.day_of_week===h.day_of_week?{...v,closes_at:e.target.value}:v))}/><button onClick={()=>setHours(hours.filter(v=>v.day_of_week!==h.day_of_week))}>Remove</button></div>)}<div className="ws-management-row"><select aria-label="Add weekday" defaultValue="" onChange={e=>{const n=Number(e.target.value);if(n&&!hours.some(h=>h.day_of_week===n))setHours([...hours,{day_of_week:n,opens_at:'09:00',closes_at:'17:00'}].sort((a,b)=>a.day_of_week-b.day_of_week));e.target.value=''}}><option value="">Add opening day</option>{days.map((d,i)=><option disabled={hours.some(h=>h.day_of_week===i+1)} key={d} value={i+1}>{d}</option>)}</select><button disabled={busy} onClick={()=>void change(()=>api(`${path}/branches/${selected}/hours`,{method:'PUT',...json(hours)}),'Hours saved')}>Save hours</button></div></div>}
   {section==='chairs'&&branch&&<div className="ws-panel ws-management"><h3>Chairs at {branch.name}</h3>{chairs.map(c=><div className="ws-management-row" key={c.id}><b>{c.name}</b><span>{c.active?'Active':'Inactive'}</span><button disabled={busy} onClick={()=>void change(()=>api(`${path}/branches/${selected}/chairs/${c.id}/active?active=${!c.active}`,{method:'PATCH'}).then(()=>api<Chair[]>(`${path}/branches/${selected}/chairs`).then(setChairs)),'Chair status updated')}>{c.active?'Deactivate':'Activate'}</button></div>)}<form onSubmit={e=>{e.preventDefault();void change(()=>api(`${path}/branches/${selected}/chairs`,{method:'POST',...json({name})}).then(()=>api<Chair[]>(`${path}/branches/${selected}/chairs`).then(setChairs)).then(()=>setName('')),'Chair added')}}><label>Chair name<input required value={name} onChange={e=>setName(e.target.value)}/></label><button disabled={busy}>Add chair</button></form></div>}
   {section==='staff'&&branch&&<div className="ws-panel ws-management"><h3>Assigned staff · {branch.name}</h3>{assigned.map(s=><div key={s.id} className="ws-management-row"><b>{s.full_name}</b><span>{s.role}</span><button disabled={busy} onClick={()=>void change(()=>api(`${path}/branches/${selected}/staff/${s.id}`,{method:'DELETE'}).then(()=>api<Staff[]>(`${path}/branches/${selected}/staff`).then(setAssigned)),'Staff unassigned')}>Remove</button></div>)}<label>Assign existing active staff<select defaultValue="" onChange={e=>{if(!e.target.value)return;const id=e.target.value;void change(()=>api(`${path}/branches/${selected}/staff`,{method:'POST',...json({userId:id})}).then(()=>api<Staff[]>(`${path}/branches/${selected}/staff`).then(setAssigned)),'Staff assigned');e.target.value=''}}><option value="">Select staff</option>{staff.filter(s=>s.active&&!assigned.some(a=>a.id===s.id)).map(s=><option key={s.id} value={s.id}>{s.full_name} · {s.role}</option>)}</select></label><p className="ws-subtle">Existing staff can be assigned to active branches here.</p></div>}
  </>}
  {section==='staff'&&<StaffOnboarding/>}
 </>
}
