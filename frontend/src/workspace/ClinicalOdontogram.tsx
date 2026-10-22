import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { teethFor, surfaceOptions } from './odontogram'
import { Dropdown } from './Dropdown'
import { useToast } from './toastContext'
import { describeError } from './toast'

type Event = {id:string;tooth_number:number;surface:string|null;condition_code:string;event_kind:string;clinical_status:string;notes:string|null;recorded_at:string;supersedes_event_id:string|null}
type Chart = {patientId:string;chartId:string|null;events:Event[]}
const conditions=['CARIES','RESTORATION','MISSING','UNERUPTED','FRACTURE','CROWN','IMPLANT','OTHER']
export function Odontogram({patientId}:{patientId:string}) {
 const [dentition,setDentition]=useState<'permanent'|'primary'|'mixed'>('permanent')
 const [chart,setChart]=useState<Chart|null>(null)
 const [tooth,setTooth]=useState(11)
 const [surface,setSurface]=useState('WHOLE')
 const [condition,setCondition]=useState('CARIES')
 const [status,setStatus]=useState('EXISTING')
 const [notes,setNotes]=useState('')
 const [supersedes,setSupersedes]=useState<string|null>(null)
 const [error,setError]=useState('')
 const toast=useToast()
 const [saving,setSaving]=useState(false)
 const [loading,setLoading]=useState(true)
 useEffect(()=>{
   let live=true
   void api<Chart>(`/api/odontogram/patients/${encodeURIComponent(patientId)}`).then(x=>{if(live){setChart(x);setError('')}}).catch(e=>{if(live){setChart(null);setError(e instanceof Error?e.message:'Chart unavailable')}}).finally(()=>{if(live)setLoading(false)})
   return ()=>{live=false}
 },[patientId])
 const events=chart?.events??[]
 const superseded=new Set(events.map(e=>e.supersedes_event_id).filter(Boolean))
 const active=events.filter(e=>!superseded.has(e.id)&&e.event_kind!=='RETRACTION')
 const related=events.filter(e=>e.tooth_number===tooth)
 async function save(kind:'OBSERVATION'|'CORRECTION'|'RETRACTION') {
   if(kind!=='OBSERVATION'&&!supersedes){setError('Choose a previous finding to correct');return}
   setSaving(true);setError('')
   try {
     await api(`/api/odontogram/patients/${encodeURIComponent(patientId)}/events`,{
       method:'POST',body:JSON.stringify({toothNumber:tooth,surface:surface==='WHOLE'?null:surface,conditionCode:condition,
         clinicalStatus:status,notes,eventKind:kind,supersedesEventId:kind==='OBSERVATION'?null:supersedes})
     })
     const fresh=await api<Chart>(`/api/odontogram/patients/${encodeURIComponent(patientId)}`)
     setChart(fresh);setSupersedes(null);setNotes('')
     toast.success(kind==='OBSERVATION'?'Finding recorded':kind==='CORRECTION'?'Correction recorded':'Finding retracted')
   } catch(e) {toast.error(describeError(e,'Could not record finding'))}
   finally {setSaving(false)}
 }
 return <section className="ws-panel ws-management" aria-label="Clinical odontogram">
   <h3>Odontogram — clinical record</h3>
   <p>FDI tooth notation · observations are retained as immutable events. Correct past entries by adding a new event.</p>
   <Dropdown label="Dentition" value={dentition} options={[{value:'permanent',label:'Permanent teeth'},{value:'primary',label:'Primary teeth'},{value:'mixed',label:'Mixed dentition'}]} onChange={v=>{const d=v as 'permanent'|'primary'|'mixed';setDentition(d);setTooth(d==='permanent'?11:51);setSurface('WHOLE');setSupersedes(null)}}/>
   <div role="group" aria-label="Select tooth" style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(44px,1fr))',gap:7,margin:'18px 0'}}>
     {teethFor(dentition).map(n=><button key={n} type="button" onClick={()=>{setTooth(n);setSurface('WHOLE');setSupersedes(null)}} aria-pressed={tooth===n} style={{border:tooth===n?'2px solid #267d68':'1px solid #a8b2bd',borderRadius:8,padding:'12px 4px',fontWeight:tooth===n?700:400,background:tooth===n?'#d6efe4':'transparent',color:'inherit'}}>{n}{active.some(x=>x.tooth_number===n)?' •':''}</button>)}
   </div>
   {loading?<p>Loading chart…</p>:<p>{active.length} current findings, {events.length} recorded events.</p>}
   {error&&<p role="alert" className="ws-global-warning">{error}</p>}
   {!loading&&chart&&<div className="ws-columns">
     <div>
       <h4>Record tooth {tooth}</h4>
        <Dropdown label="Surface" value={surface} options={surfaceOptions(tooth).map(s=>({value:s,label:s==='WHOLE'?'Whole tooth':s}))} onChange={setSurface}/>
        <Dropdown label="Finding" value={condition} options={conditions.map(c=>({value:c,label:c}))} onChange={setCondition}/>
        <Dropdown label="Clinical status" value={status} options={['EXISTING','PLANNED','COMPLETED'].map(s=>({value:s,label:s}))} onChange={setStatus}/>
       <label>Notes <textarea value={notes} onChange={e=>setNotes(e.target.value)} maxLength={10000} rows={3}/></label>
       <button type="button" disabled={saving} onClick={()=>void save('OBSERVATION')}>Record new finding</button>
       {supersedes&&<><button type="button" disabled={saving} onClick={()=>void save('CORRECTION')}>Record correction</button> <button type="button" disabled={saving} onClick={()=>void save('RETRACTION')}>Retract finding</button></>}
     </div>
     <div>
       <h4>History for tooth {tooth}</h4>
       {!related.length&&<p>No recorded findings for this tooth.</p>}
       <ol>{related.map(e=><li key={e.id} style={{marginBottom:12}}>
         <strong>{e.event_kind}: {e.condition_code}</strong> ({e.surface??'whole tooth'})<div><small>{String(e.recorded_at)} · {e.clinical_status}{superseded.has(e.id)?' · superseded':''}</small></div>
         {e.notes&&<p>{e.notes}</p>}
         {!superseded.has(e.id)&&e.event_kind!=='RETRACTION'&&<button type="button" onClick={()=>{setTooth(e.tooth_number);setSurface(e.surface??'WHOLE');setCondition(conditions.includes(e.condition_code)?e.condition_code:'OTHER');setSupersedes(e.id)}}>Correct / retract</button>}
       </li>)}</ol>
     </div>
   </div>}
 </section>
}
