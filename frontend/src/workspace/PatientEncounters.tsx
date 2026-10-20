import { useEffect,useState } from 'react'
import { api } from '../api/client'
type Encounter={id:string;encounter_status:string;started_at:string;dentist_id:string;version:number}
type SOAP={subjective:string;objective:string;assessment:string;plan_notes:string;version:number;updated_at?:string}
type Diagnosis={id:string;code_system:string;diagnosis_code:string;description:string;created_at:string}
type Revision=SOAP & {id:string;draft_version:number;recorded_at:string}
const blank:SOAP={subjective:'',objective:'',assessment:'',plan_notes:'',version:0}
export function PatientEncounters({patientId}:{patientId:string}) {
 const [encounters,setEncounters]=useState<Encounter[]>([])
 const [selected,setSelected]=useState('')
 const [soap,setSoap]=useState<SOAP>(blank)
 const [diagnoses,setDiagnoses]=useState<Diagnosis[]>([])
 const [revisions,setRevisions]=useState<Revision[]>([])
 const [codeSystem,setCodeSystem]=useState('ICD10')
 const [diagnosisCode,setDiagnosisCode]=useState('')
 const [description,setDescription]=useState('')
 const [loading,setLoading]=useState(true)
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const [notice,setNotice]=useState('')
 useEffect(()=>{
  let active=true
  setSelected('');setEncounters([]);setSoap(blank);setDiagnoses([]);setRevisions([]);setLoading(true);setError('')
  void api<Encounter[]>(`/api/encounters/patients/${encodeURIComponent(patientId)}`).then(rows=>{if(active){setEncounters(rows);setSelected(rows[0]?.id??'')}})
   .catch(e=>{if(active)setError(e instanceof Error?e.message:'Could not load encounters')}).finally(()=>{if(active)setLoading(false)})
  return ()=>{active=false}
 },[patientId])
 useEffect(()=>{
  let active=true
  setSoap(blank);setDiagnoses([]);setRevisions([]);setError('');setNotice('')
  if(!selected)return
  const url=`/api/encounters/${encodeURIComponent(selected)}/clinical`
  void Promise.all([api<SOAP>(url+'/soap'),api<Diagnosis[]>(url+'/diagnoses'),api<Revision[]>(url+'/revisions')])
   .then(([s,d,r])=>{if(active){setSoap(s);setDiagnoses(d);setRevisions(r)}})
   .catch(e=>{if(active)setError(e instanceof Error?e.message:'Unable to load clinical documentation')})
  return ()=>{active=false}
 },[selected])
 const current=encounters.find(x=>x.id===selected)
 const editable=current?.encounter_status==='IN_PROGRESS'
 async function saveDraft(){
  if(!current||!editable)return
  setBusy(true);setError('');setNotice('')
  try {
   const url=`/api/encounters/${encodeURIComponent(selected)}/clinical`
   const saved=await api<SOAP>(url+'/soap',{method:'PUT',body:JSON.stringify({
    subjective:soap.subjective,objective:soap.objective,assessment:soap.assessment,
    planNotes:soap.plan_notes,expectedVersion:soap.version
   })})
   setSoap(saved);setRevisions(await api<Revision[]>(url+'/revisions'))
   setNotice('Draft saved. This note is not signed.')
  }catch(e){setError(e instanceof Error?e.message:'Unable to save draft. Reload and review concurrent changes.')}
  finally{setBusy(false)}
 }
 async function addDiagnosis(){
  if(!current||!editable)return
  setBusy(true);setError('');setNotice('')
  try{
   const url=`/api/encounters/${encodeURIComponent(selected)}/clinical/diagnoses`
   await api(url,{method:'POST',body:JSON.stringify({codeSystem,code:diagnosisCode,description})})
   setDiagnoses(await api<Diagnosis[]>(url));setDiagnosisCode('');setDescription('')
   setNotice('Diagnosis recorded in encounter history.')
  }catch(e){setError(e instanceof Error?e.message:'Could not record diagnosis')}
  finally{setBusy(false)}
 }
 return <section className="ws-panel ws-management" aria-label="Clinical encounters">
  <h3>Encounters and SOAP documentation</h3>
  <p>Clinical draft only. Signing and amendments will be available in M5.3.</p>
  {loading&&<p>Loading patient encounters…</p>}
  {error&&<p role="alert" className="ws-global-warning">{error}</p>}
  {notice&&<p role="status">{notice}</p>}
  {!loading&&!encounters.length&&<p>No encounters have been recorded for this patient. Encounters are created through the clinical encounter API; no sample clinical notes are shown.</p>}
  {!!encounters.length&&<>
   <label>Encounter <select value={selected} onChange={e=>setSelected(e.target.value)}>
    {encounters.map(e=><option key={e.id} value={e.id}>{new Date(e.started_at).toLocaleString()} — {e.encounter_status}</option>)}
   </select></label>
   {current&&<p>State: <strong>{current.encounter_status}</strong>. {editable?'Draft editing permitted.':'This encounter is not editable.'}</p>}
   <div className="ws-info-grid">
    {([['subjective','Subjective'],['objective','Objective'],['assessment','Assessment'],['plan_notes','Plan']] as const).map(([key,label])=>
     <label key={key}>{label}<textarea rows={4} value={soap[key]??''} maxLength={20000} disabled={!editable||busy}
      onChange={e=>setSoap(prev=>({...prev,[key]:e.target.value}))}/></label>)}
   </div>
   <p>Draft version {soap.version}. Prior revisions are retained.</p>
   <button type="button" disabled={!editable||busy} onClick={()=>void saveDraft()}>Save SOAP draft</button>
   <h4>Clinical diagnoses</h4>
   {!diagnoses.length&&<p>No diagnoses recorded.</p>}
   <ul>{diagnoses.map(d=><li key={d.id}>{d.code_system} {d.diagnosis_code}: {d.description}</li>)}</ul>
   {editable&&<div><label>Code system <select value={codeSystem} onChange={e=>setCodeSystem(e.target.value)}>
    {['ICD10','SNODENT','LOCAL'].map(c=><option key={c}>{c}</option>)}</select></label>
    <label>Code <input value={diagnosisCode} maxLength={80} onChange={e=>setDiagnosisCode(e.target.value)}/></label>
    <label>Clinical description <input value={description} maxLength={500} onChange={e=>setDescription(e.target.value)}/></label>
    <button type="button" disabled={busy||!diagnosisCode.trim()||!description.trim()} onClick={()=>void addDiagnosis()}>Record diagnosis</button>
   </div>}
   <h4>SOAP draft revision history</h4>
   {!revisions.length&&<p>No revisions saved yet.</p>}
   <ol>{revisions.map(r=><li key={r.id}>Version {r.draft_version} — {new Date(r.recorded_at).toLocaleString()}
    <details><summary>Review recorded revision</summary>
      <p><strong>Subjective:</strong> {r.subjective}</p><p><strong>Objective:</strong> {r.objective}</p>
      <p><strong>Assessment:</strong> {r.assessment}</p><p><strong>Plan:</strong> {r.plan_notes}</p>
     </details></li>)}</ol>
  </>}
 </section>
}
