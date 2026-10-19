import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { JOURNEY_STEPS, nextJourneyIndex, previousJourneyIndex } from './journey'
import { canSeeModule, type WorkspaceRole } from './catalog'

export function JourneyPreview({ role }: { role: WorkspaceRole }) {
  const [index, setIndex] = useState(0)
  const [checked, setChecked] = useState<Record<string, boolean>>({})
  const step = JOURNEY_STEPS[index]
  const completed = step.checkpoints.filter((_, i) => checked[`${step.id}-${i}`]).length
  return <>
    <div className="ws-heading"><div><span className="ws-eyebrow">Design workshop / M0.6</span><h1>Patient journey simulator <span className="ws-pill">Preview</span></h1><p>Explore the complete fictional patient flow without changing records.</p></div></div>
    <div className="ws-alert">DESIGN SIMULATION — Maria Santos is fictional. Checkboxes are for discussion only; no patient data, appointment, clinical note or payment is saved.</div>
    <div className="ws-journey-rail" aria-label="Patient journey steps">{JOURNEY_STEPS.map((s,i)=><button type="button" key={s.id} aria-current={i===index?'step':undefined} className={i===index?'active':''} onClick={()=>setIndex(i)}><small>{i+1} · {s.milestone}</small><b>{s.title}</b></button>)}</div>
    <div className="ws-columns"><section className="ws-panel ws-journey-content"><span className="ws-eyebrow">STEP {index+1} OF {JOURNEY_STEPS.length} · {step.owner}</span><h2>{step.title}</h2><p>{step.description}</p><h3>Review checklist</h3><p className="ws-subtle">Use these prompts to discuss the intended workflow. Ticking them does not complete any real task.</p><div className="ws-journey-checks">{step.checkpoints.map((c,i)=><label key={c}><input type="checkbox" checked={!!checked[`${step.id}-${i}`]} onChange={e=>setChecked(p=>({...p,[`${step.id}-${i}`]:e.target.checked}))}/><span>{c}</span></label>)}</div><p className="ws-subtle">Reviewed in this step: {completed} / {step.checkpoints.length} discussion points</p><div className="ws-journey-actions"><button disabled={index===0} onClick={()=>setIndex(previousJourneyIndex(index))}>← Previous</button><button disabled={index===JOURNEY_STEPS.length-1} className="ws-journey-next" onClick={()=>setIndex(nextJourneyIndex(index))}>Next step →</button></div></section><aside className="ws-panel ws-sidecard"><h3>Context and boundaries</h3><p><b>Demo patient:</b> Maria Santos</p><p><b>Planned delivery:</b> {step.milestone}</p><p><b>Responsible team:</b> {step.owner}</p><p><b>Current workflow:</b> Preview only</p>{canSeeModule(step.module,role)?<NavLink className="ws-primary-link" to={`/workspace/${step.module}`}>View related screen →</NavLink>:<p className="ws-subtle">Related screen is hidden from this role. No access is granted by the simulator.</p>}<hr/><h3>Workflow exceptions to review</h3><p className="ws-subtle">Walk-ins may start at check-in; consultation does not require a treatment plan; invoices are not mandatory for every visit; patient refusal and emergency care must be supported.</p></aside></div>
  </>
}
