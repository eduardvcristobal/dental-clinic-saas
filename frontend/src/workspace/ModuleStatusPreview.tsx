import { useState } from 'react'
import { FEATURE_REGISTRY, moduleDecision } from './governance'
import { DISPLAY_ROLES, type WorkspaceRole } from './catalog'

/** Demo-only governance inspector; it does not configure real permissions or plans. */
export function ModuleStatusPreview({ role }: { role: WorkspaceRole }) {
  const [query, setQuery] = useState('')
  const [group, setGroup] = useState('All')
  const groups = ['All', ...new Set(FEATURE_REGISTRY.map(m => m.group))]
  const modules = FEATURE_REGISTRY.filter(m => (group === 'All' || group === m.group) && `${m.title} ${m.description}`.toLowerCase().includes(query.toLowerCase()))
  return <>
    <div className="ws-heading"><div><span className="ws-eyebrow">Administration / M0.7</span><h1>Module readiness & permissions</h1><p>Preview-only audit of planned screens, role visibility, and functional readiness.</p></div><span className="ws-pill">Design preview</span></div>
    <div className="ws-panel ws-sidecard"><h3>Governance rules</h3><p>Role: {DISPLAY_ROLES[role]}. “Preview” means interactive fictional data, not a functional API. “Coming soon” means unimplemented. Clinic subscription entitlements are not yet available. Backend access must always be checked separately.</p><p><strong>Platform administrators</strong> use a separate console; cross-clinic clinical access must not be granted by this registry.</p></div>
    <div className="ws-panel ws-sidecard"><div className="ws-panel-head"><h3>Module inventory ({modules.length})</h3></div><div className="ws-governance-controls"><label>Search modules <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search modules" /></label><label>Group <select value={group} onChange={e=>setGroup(e.target.value)}>{groups.map(g=><option key={g}>{g}</option>)}</select></label></div><div className="ws-scroll"><table className="ws-table"><thead><tr><th>Module</th><th>UI readiness</th><th>Visible to</th><th>Demo view</th><th>Production action</th></tr></thead><tbody>{modules.map(m=><tr key={m.id}><td><b>{m.title}</b><small>{m.description}</small></td><td><span className="ws-status">{m.state === 'coming-soon' ? 'Coming soon' : m.state === 'preview' ? 'Preview' : 'Active'}</span></td><td>{m.roles.map(r=>DISPLAY_ROLES[r]).join(', ')}</td><td>{moduleDecision(m.id,role,'view-demo').allowed?'Allowed':'Unavailable'}</td><td>Not enabled</td></tr>)}</tbody></table></div></div>
  </>
}
