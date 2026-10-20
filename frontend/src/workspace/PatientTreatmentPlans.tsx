import { useEffect,useState } from 'react'
import { api } from '../api/client'
type Plan={id:string;title:string;status:string;version:number;estimate_total?:number;items?:Item[];consent_reference?:string}
type Item={id:string;procedure_name:string;tooth_number:number|null;phase:number;quantity:number;unit_price:number;discount:number;status:string}
type Procedure={id:string;name:string;code:string;default_price:number;requires_tooth:boolean;requires_surface:boolean}
type Dentist={id:string;full_name:string}
type Encounter={id:string;encounter_status:string;started_at:string}
const money=(value:number,currency:string)=>new Intl.NumberFormat('en',{style:'currency',currency}).format(value)
export function PatientTreatmentPlans({patientId,role}:{patientId:string;role:string}){
 const [plans,setPlans]=useState<Plan[]>([])
 const [currency,setCurrency]=useState('PHP')
 const [selected,setSelected]=useState('')
 const [detail,setDetail]=useState<Plan|null>(null)
 const [catalog,setCatalog]=useState<Procedure[]>([])
 const [dentists,setDentists]=useState<Dentist[]>([])
 const [encounters,setEncounters]=useState<Encounter[]>([])
 const [visitId,setVisitId]=useState('')
 const [title,setTitle]=useState('');const [dentistId,setDentistId]=useState('')
 const [newCode,setNewCode]=useState('');const [newName,setNewName]=useState('');const [newPrice,setNewPrice]=useState(0);const [requiresTooth,setRequiresTooth]=useState(false)
 const [procedure,setProcedure]=useState('');const [tooth,setTooth]=useState('')
 const [qty,setQty]=useState(1);const [price,setPrice]=useState(0);const [discount,setDiscount]=useState(0)
 const [consent,setConsent]=useState('');const [error,setError]=useState('');const [busy,setBusy]=useState(false)
 const [events,setEvents]=useState<Array<{id:string;event_type:string;event_at:string}>>([])
 const load=async()=>{
  const rows=await api<Plan[]>(`/api/treatment-plans/patients/${encodeURIComponent(patientId)}`)
  setPlans(rows);setSelected(current=>rows.some(p=>p.id===current)?current:(rows[0]?.id??''))
 }
 useEffect(()=>{let alive=true;setPlans([]);setSelected('');setDetail(null);setError('')
  void Promise.all([api<Plan[]>(`/api/treatment-plans/patients/${encodeURIComponent(patientId)}`),
   api<Procedure[]>('/api/treatment-plans/catalog'),
   api<Dentist[]>('/api/treatment-plans/dentists'),
   api<Encounter[]>(`/api/encounters/patients/${encodeURIComponent(patientId)}`)]).then(([p,c,r,e])=>{
    if(alive){setPlans(p);setSelected(p[0]?.id??'');setCatalog(c);setDentists(Array.isArray(r)?r:[]);setEncounters(e)}
   }).catch(e=>{if(alive)setError(String(e))});return()=>{alive=false}
 },[patientId])
 useEffect(()=>{let alive=true;setDetail(null);setEvents([])
  if(!selected)return
  void Promise.all([api<Plan>(`/api/treatment-plans/${selected}`),
   api<Array<{id:string;event_type:string;event_at:string}>>(`/api/treatment-plans/${selected}/history`),
   api<{currency:string}>(`/api/treatment-plans/${selected}/billing-preview`)]).then(([d,h,preview])=>{if(alive){setDetail(d);setEvents(h);setCurrency(preview.currency)}}).catch(e=>{if(alive)setError(String(e))})
  return()=>{alive=false}
 },[selected])
 const refresh=async()=>{await load();if(selected){setDetail(await api<Plan>(`/api/treatment-plans/${selected}`));setEvents(await api(`/api/treatment-plans/${selected}/history`))}}
 const execute=async(action:()=>Promise<unknown>)=>{setBusy(true);setError('')
  try{await action();await refresh()}catch(e){setError(e instanceof Error?e.message:'Request failed')}finally{setBusy(false)}
 }
 async function createProcedure(){
  setBusy(true);setError('')
  try{
   await api('/api/treatment-plans/catalog',{method:'POST',body:JSON.stringify({
     code:newCode,name:newName,category:'General',defaultPrice:newPrice,requiresTooth,requiresSurface:false
   })})
   setCatalog(await api<Procedure[]>('/api/treatment-plans/catalog'))
   setNewCode('');setNewName('');setNewPrice(0);setRequiresTooth(false)
  }catch(e){setError(e instanceof Error?e.message:'Could not add procedure')}
  finally{setBusy(false)}
 }
 const chosen=catalog.find(p=>p.id===procedure)
 return <section className="ws-panel ws-management" aria-label="Dental treatment planning">
  <h3>Dental Treatment Plans</h3><p>Clinical estimates only. No invoice or payment is created here.</p>
  {error&&<p role="alert">{error}</p>}
  {['OWNER','ADMIN'].includes(role)&&<section aria-label="Manage procedure catalog">
   <h4>Procedure catalog</h4>
   <label>Procedure code <input value={newCode} maxLength={80} onChange={e=>setNewCode(e.target.value)}/></label>
   <label>Procedure name <input value={newName} maxLength={255} onChange={e=>setNewName(e.target.value)}/></label>
   <label>Default estimate price <input type="number" min={0} step=".01" value={newPrice} onChange={e=>setNewPrice(Number(e.target.value))}/></label>
   <label><input type="checkbox" checked={requiresTooth} onChange={e=>setRequiresTooth(e.target.checked)}/> Requires a tooth</label>
   <button disabled={busy||!newCode.trim()||!newName.trim()||newPrice<0} onClick={()=>void createProcedure()}>Add clinic procedure</button>
  </section>}
  <h4>Start treatment plan</h4>
  <label>Plan title <input value={title} maxLength={255} onChange={e=>setTitle(e.target.value)}/></label>
  <label>Dentist <select value={dentistId} onChange={e=>setDentistId(e.target.value)}><option value="">Choose dentist</option>
   {dentists.map(d=><option key={d.id} value={d.id}>{d.full_name}</option>)}</select></label>
  <button disabled={busy||!title.trim()||!dentistId} onClick={()=>void execute(async()=>{
   const created=await api<Plan>('/api/treatment-plans',{method:'POST',body:JSON.stringify({patientId,dentistId,title})})
   setSelected(created.id);setTitle('')
  })}>Create draft plan</button>
  <h4>Existing plans</h4>
  {!plans.length&&<p>No treatment plans yet.</p>}
  <label>Selected plan <select value={selected} onChange={e=>setSelected(e.target.value)}>
   <option value="">Choose plan</option>{plans.map(p=><option value={p.id} key={p.id}>{p.title} — {p.status}</option>)}
  </select></label>
  {detail&&<>
   <h4>{detail.title} — {detail.status}</h4>
   <p><strong>Estimated total: {money(Number(detail.estimate_total??0),currency)}</strong></p>
   <p>This is a proposed estimate, not a charge or invoice.</p>
   <table><thead><tr><th>Procedure</th><th>Tooth</th><th>Qty</th><th>Net estimate</th><th>Progress</th></tr></thead>
   <tbody>{(detail.items??[]).map(i=><tr key={i.id}><td>{i.procedure_name}</td><td>{i.tooth_number??'—'}</td>
    <td>{i.quantity}</td><td>{money(Number(i.unit_price)*i.quantity-Number(i.discount),currency)}</td><td>
     {i.status}
     {detail.status==='IN_PROGRESS'&&i.status==='PLANNED'&&
      <button type="button" disabled={busy} onClick={()=>void execute(()=>api(`/api/treatment-plans/${selected}/items/${i.id}/progress`,{
        method:'POST',body:JSON.stringify({status:'SCHEDULED'})
      }))}>Mark planned scheduling status (not a booking)</button>}
     {detail.status==='IN_PROGRESS'&&['PLANNED','SCHEDULED'].includes(i.status)&&
      <button type="button" disabled={busy||!visitId} onClick={()=>void execute(()=>api(`/api/treatment-plans/${selected}/items/${i.id}/progress`,{
        method:'POST',body:JSON.stringify({status:'COMPLETED',visitId})
      }))}>Record completed treatment</button>}
    </td></tr>)}</tbody></table>
   {detail.status==='IN_PROGRESS'&&<label>Completed patient encounter for item evidence
      <select value={visitId} onChange={e=>setVisitId(e.target.value)}><option value="">Choose completed encounter</option>
       {encounters.filter(e=>e.encounter_status==='COMPLETED').map(e=><option key={e.id} value={e.id}>{new Date(e.started_at).toLocaleString()}</option>)}
      </select></label>}
   {detail.status==='DRAFT'&&<div>
    <h4>Add procedure</h4><label>Procedure <select value={procedure} onChange={e=>{
      setProcedure(e.target.value);setPrice(Number(catalog.find(p=>p.id===e.target.value)?.default_price??0))
     }}><option value="">Select</option>{catalog.map(p=><option key={p.id} value={p.id}>{p.name} ({p.code})</option>)}</select></label>
    {chosen?.requires_tooth&&<label>FDI tooth <input value={tooth} onChange={e=>setTooth(e.target.value)} inputMode="numeric"/></label>}
    <label>Quantity <input type="number" min={1} max={1000} value={qty} onChange={e=>setQty(Number(e.target.value))}/></label>
    <label>Unit price <input type="number" min={0} step=".01" value={price} onChange={e=>setPrice(Number(e.target.value))}/></label>
    <label>Discount per item (total) <input type="number" min={0} step=".01" value={discount} onChange={e=>setDiscount(Number(e.target.value))}/></label>
    <button disabled={busy||!procedure||qty<1||price<0||discount<0||(!!chosen?.requires_tooth&&!tooth)}
     onClick={()=>void execute(()=>api(`/api/treatment-plans/${selected}/items`,{method:'POST',body:JSON.stringify({
      procedureId:procedure,toothNumber:tooth?Number(tooth):null,surface:null,phase:1,quantity:qty,unitPrice:price,discount
     })}))}>Add treatment item</button>
   </div>}
   <div>
    {detail.status==='PROPOSED'&&<label>Consent record reference <input value={consent} maxLength={256} onChange={e=>setConsent(e.target.value)}/></label>}
    {(['DRAFT','PROPOSED','ACCEPTED','IN_PROGRESS'].includes(detail.status))&&<button disabled={busy||(detail.status==='PROPOSED'&&consent.trim().length<3)}
      onClick={()=>void execute(()=>api(`/api/treatment-plans/${selected}/transition`,{method:'POST',body:JSON.stringify({
       status:{DRAFT:'PROPOSED',PROPOSED:'ACCEPTED',ACCEPTED:'IN_PROGRESS',IN_PROGRESS:'COMPLETED'}[detail.status],
       expectedVersion:detail.version,consentReference:detail.status==='PROPOSED'?consent:null
      })}))}>Move to {({DRAFT:'PROPOSED',PROPOSED:'ACCEPTED',ACCEPTED:'IN_PROGRESS',IN_PROGRESS:'COMPLETED'} as Record<string,string>)[detail.status]}</button>}
    {detail.status==='PROPOSED'&&<button disabled={busy} onClick={()=>void execute(()=>api(`/api/treatment-plans/${selected}/transition`,{
     method:'POST',body:JSON.stringify({status:'DECLINED',expectedVersion:detail.version})
    }))}>Record decline</button>}
   </div>
   <h4>Audit history</h4><ol>{events.map(e=><li key={e.id}>{e.event_type} — {new Date(e.event_at).toLocaleString()}</li>)}</ol>
  </>}
 </section>
}
