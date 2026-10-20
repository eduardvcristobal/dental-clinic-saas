import { useEffect,useState } from 'react'
import { api } from '../api/client'
type Encounter={id:string;encounter_status:string;started_at:string;dentist_id:string;version:number}
type SOAP={subjective:string;objective:string;assessment:string;plan_notes:string;version:number;updated_at?:string}
type Diagnosis={id:string;code_system:string;diagnosis_code:string;description:string;created_at:string}
type Signature={signed:boolean;note?:SOAP & {signed_at:string;signed_by:string}}
type Amendment={id:string;reason:string;amendment:string;created_at:string}
type Prescription={id:string;medication:string;dosage:string;instructions:string;status:string}
type Chart={events:Array<{id:string;tooth_number:number;condition_code:string;event_kind:string}>}
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
 const [signature,setSignature]=useState<Signature>({signed:false})
 const [amendments,setAmendments]=useState<Amendment[]>([])
 const [prescriptions,setPrescriptions]=useState<Prescription[]>([])
 const [chart,setChart]=useState<Chart|null>(null)
 const [amendReason,setAmendReason]=useState('')
 const [amendText,setAmendText]=useState('')
 const [medication,setMedication]=useState('')
 const [dosage,setDosage]=useState('')
 const [instructions,setInstructions]=useState('')
 const [loading,setLoading]=useState(true)
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const [notice,setNotice]=useState('')
 useEffect(()=>{
  let active=true
  setSelected('');setEncounters([]);setSoap(blank);setDiagnoses([]);setRevisions([]);setChart(null);setSignature({signed:false});setLoading(true);setError('')
  void api<Encounter[]>(`/api/encounters/patients/${encodeURIComponent(patientId)}`).then(rows=>{if(active){setEncounters(rows);setSelected(rows[0]?.id??'')}})
   .catch(e=>{if(active)setError(e instanceof Error?e.message:'Could not load encounters')}).finally(()=>{if(active)setLoading(false)})
  return ()=>{active=false}
 },[patientId])
 useEffect(()=>{
  let active=true
  setSoap(blank);setDiagnoses([]);setRevisions([]);setSignature({signed:false});setAmendments([]);setPrescriptions([]);setError('');setNotice('')
  if(!selected)return
  const url=`/api/encounters/${encodeURIComponent(selected)}/clinical`
  void Promise.all([api<SOAP>(url+'/soap'),api<Diagnosis[]>(url+'/diagnoses'),api<Revision[]>(url+'/revisions'),api<Signature>(`/api/encounters/${encodeURIComponent(selected)}/signature`),api<Amendment[]>(`/api/encounters/${encodeURIComponent(selected)}/signature/amendments`),api<Prescription[]>(`/api/encounters/${encodeURIComponent(selected)}/records/prescriptions`)])
   .then(([s,d,r,sig,amend,rx])=>{if(active){setSoap(s);setDiagnoses(d);setRevisions(r);setSignature(sig);setAmendments(amend);setPrescriptions(rx)}})
   .catch(e=>{if(active)setError(e instanceof Error?e.message:'Unable to load clinical documentation')})
  return ()=>{active=false}
 },[selected])
 useEffect(()=>{let live=true;void api<Chart>(`/api/odontogram/patients/${encodeURIComponent(patientId)}`).then(v=>{if(live)setChart(v)}).catch(()=>{if(live)setChart(null)});return()=>{live=false}},[patientId])
 const current=encounters.find(x=>x.id===selected)
 const editable=current?.encounter_status==='IN_PROGRESS'&&!signature.signed
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
 async function signNote(){
  if(!editable||soap.version<1)return
  setBusy(true);setError('')
  try{
   const result=await api<Signature>(`/api/encounters/${encodeURIComponent(selected)}/signature`,{method:'POST',body:JSON.stringify({draftVersion:soap.version})})
   setSignature(result);setNotice('Clinical note signed and locked. Amendments are separate.')
  }catch(e){setError(e instanceof Error?e.message:'Cannot sign note')}finally{setBusy(false)}
 }
 async function amendNote(){
  setBusy(true);setError('')
  try{
   const base=`/api/encounters/${encodeURIComponent(selected)}/signature/amendments`
   await api(base,{method:'POST',body:JSON.stringify({reason:amendReason,amendment:amendText})})
   setAmendments(await api<Amendment[]>(base));setAmendReason('');setAmendText('');setNotice('Amendment appended; original signed note unchanged.')
  }catch(e){setError(e instanceof Error?e.message:'Cannot amend note')}finally{setBusy(false)}
 }
 async function addPrescription(){
  setBusy(true);setError('')
  try{
   const base=`/api/encounters/${encodeURIComponent(selected)}/records/prescriptions`
   await api(base,{method:'POST',body:JSON.stringify({medication,dosage,instructions})})
   setPrescriptions(await api<Prescription[]>(base));setMedication('');setDosage('');setInstructions('');setNotice('Prescription draft recorded. Not signed or transmitted.')
  }catch(e){setError(e instanceof Error?e.message:'Cannot record prescription draft')}finally{setBusy(false)}
 }
 return <section className="ws-panel ws-management" aria-label="Clinical encounters">
  <h3>Encounters and SOAP documentation</h3>
  <p>Clinical records are retained with version history. Signing locks the SOAP draft; amendments remain separate.</p>
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
   <section aria-label="Clinical note signature"><h4>Clinical signing and amendments</h4>
    <p>{signature.signed?'Signed clinical note — draft locked':'Unsigned clinical draft'}</p>
    {editable&&<button type="button" disabled={busy||soap.version<1} onClick={()=>void signNote()}>Sign and lock clinical note</button>}
    {signature.signed&&<><p>Original signed note retained unchanged.</p>
      <label>Amendment reason <input value={amendReason} maxLength={500} onChange={e=>setAmendReason(e.target.value)}/></label>
      <label>Amendment text <textarea value={amendText} maxLength={20000} onChange={e=>setAmendText(e.target.value)}/></label>
      <button type="button" disabled={busy||amendReason.trim().length<3||!amendText.trim()} onClick={()=>void amendNote()}>Append signed-note amendment</button>
      <ol>{amendments.map(a=><li key={a.id}>{a.reason}: {a.amendment}</li>)}</ol>
    </>}
   </section>
   <section aria-label="Prescription drafts"><h4>Prescription drafts</h4>
     <p>These entries are drafts only. They are not electronically prescribed or dispensed.</p>
     <ul>{prescriptions.map(rx=><li key={rx.id}>{rx.medication} — {rx.dosage}: {rx.instructions} ({rx.status})</li>)}</ul>
     {editable&&<><label>Medication <input value={medication} maxLength={300} onChange={e=>setMedication(e.target.value)}/></label>
      <label>Dosage <input value={dosage} maxLength={500} onChange={e=>setDosage(e.target.value)}/></label>
      <label>Instructions <textarea value={instructions} maxLength={2000} onChange={e=>setInstructions(e.target.value)}/></label>
      <button type="button" disabled={busy||!medication.trim()||!dosage.trim()||!instructions.trim()} onClick={()=>void addPrescription()}>Save prescription draft</button>
     </>}
   </section>
   <section aria-label="Patient odontogram context"><h4>Patient odontogram context</h4>
     <p>Patient-wide tooth findings are shown for reference. They are not automatically attributed to this encounter.</p>
     {chart?<p>{chart.events.length} longitudinal tooth finding events recorded.</p>:<p>Chart context unavailable.</p>}
   </section>
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
