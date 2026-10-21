import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import { Dropdown } from './Dropdown'
import { useToast } from './toastContext'
import { describeError } from './toast'

type Branch = { id: string; name: string; active: boolean }
type Staff = { id: string; full_name: string; email: string; role: string; active: boolean }
type Invitation = { id: string; email: string; role: string; branch_id: string; created_at: string; expires_at: string; accepted_at: string | null; revoked_at: string | null; status: 'Accepted' | 'Revoked' | 'Expired' | 'Pending' }
type Dentist = { id: string; user_id: string; full_name: string; license_no: string; specialty: string; active: boolean }
type InviteRole = 'DENTIST' | 'FRONT_DESK' | 'ADMIN'
const INVITE_ROLES: InviteRole[] = ['DENTIST', 'FRONT_DESK', 'ADMIN']
const json = (data: unknown) => ({ method: 'POST', body: JSON.stringify(data) })
/** Formats an invitation timestamp for display, e.g. "21 Oct 2026, 18:30". */
/** Builds the "Invited / Expires / Accepted / Revoked" line of an invitation card. */
function invitationMeta(invitation: Invitation): string {
  const parts: string[] = []
  const invited = formatStamp(invitation.created_at)
  if (invited) parts.push(`Invited ${invited}`)
  const expires = formatStamp(invitation.expires_at)
  if (expires) parts.push(`Expires ${expires}`)
  const accepted = formatStamp(invitation.accepted_at)
  if (accepted) parts.push(`Accepted ${accepted}`)
  const revoked = formatStamp(invitation.revoked_at)
  if (revoked) parts.push(`Revoked ${revoked}`)
  return parts.join(' · ')
}
function formatStamp(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
type IconKind='add'|'copy'|'open'|'remove'|'profile'|'renew'|'edit'
function StaffIcon({kind}:{kind:IconKind}) {
 const paths:Record<IconKind,string>={
  add:'M10 4v12M4 10h12',copy:'M7 7h9v10H7zM4 13V3h9',open:'M11 4h5v5M16 4l-7 7M14 11v5H4V6h5',remove:'M4 6h12M8 6V4h4v2m2 0-.7 10H6.7L6 6m2.5 3v4m3-4v4',profile:'M10 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm-6 7c0-3 2.7-5 6-5s6 2 6 5M16 5v5m-2.5-2.5h5',renew:'M16.5 8A7 7 0 0 0 4 6L2.5 8M3 4v4h4m-3.5 4A7 7 0 0 0 16 14l1.5-2M17 16v-4h-4',edit:'m4 13.5-.8 3.3 3.3-.8L16 6.5 13.5 4 4 13.5Zm7.5-7.5L14 8.5'
 }
 return <svg className="ws-staff-icon" aria-hidden="true" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d={paths[kind]}/></svg>
}
export function StaffOnboarding({section='all'}:{section?:'all'|'invitations'|'dentists'}) {
 const [branches, setBranches] = useState<Branch[]>([])
 const [staff, setStaff] = useState<Staff[]>([])
 const [invites, setInvites] = useState<Invitation[]>([])
 const [dentists, setDentists] = useState<Dentist[]>([])
 const [branch, setBranch] = useState('')
 const [email, setEmail] = useState('')
 const [role, setRole] = useState('DENTIST')
 const [name, setName] = useState('')
 const [license, setLicense] = useState('')
 const [specialty, setSpecialty] = useState('')
 const [editingDentist,setEditingDentist]=useState<Dentist|null>(null)
 const [editName,setEditName]=useState('')
 const [editLicense,setEditLicense]=useState('')
 const [editSpecialty,setEditSpecialty]=useState('')
 const [staffId, setStaffId] = useState('')
 const [token, setToken] = useState('')
 const [linkInvitationId, setLinkInvitationId] = useState('')
 const [error, setError] = useState('')
 const [busy, setBusy] = useState(false)
 const toast = useToast()
 async function refresh() {
  const [b,s,i,d] = await Promise.all([
   api<Branch[]>('/api/clinic-management/branches'), api<Staff[]>('/api/clinic-management/staff'),
   api<Invitation[]>('/api/onboarding/invitations'), api<Dentist[]>('/api/onboarding/dentists')])
  const now = Date.now()
  setBranches(b); setStaff(s); setInvites(i.map(invite => ({
   ...invite,
   status: invite.accepted_at ? 'Accepted' : invite.revoked_at ? 'Revoked' : Date.parse(invite.expires_at) <= now ? 'Expired' : 'Pending',
  }))); setDentists(d)
  setBranch(v => b.some(x => x.id === v && x.active) ? v : b.find(x => x.active)?.id ?? '')
 }
 useEffect(() => {
  let active = true
  async function load() {
   try { await refresh() } catch (e) { if (active) setError(describeError(e,'Could not load the staff records')) }
  }
  void load()
  return () => { active = false }
 }, [])
 async function run(action: () => Promise<unknown>, success: string) {
  setBusy(true); setError('')
  try { await action(); await refresh(); toast.success(success) }
  catch (e) { toast.error(describeError(e, 'The request could not be completed')) }
  finally { setBusy(false) }
 }
 async function renewLink(invitation:Invitation){
  setError('');setBusy(true)
  try{const result=await api<{token:string}>(`/api/onboarding/invitations/${invitation.id}/renew`,{method:'POST'});setToken(result.token);setLinkInvitationId(invitation.id);toast.success(`A new invitation link is ready for ${invitation.email}. The previous link is now invalid.`)}
  catch(e){toast.error(describeError(e,'Could not renew the invitation link'))}
  finally{setBusy(false)}
 }
 function editProfile(dentist:Dentist){setEditingDentist(dentist);setEditName(dentist.full_name);setEditLicense(dentist.license_no);setEditSpecialty(dentist.specialty??'');setError('')}
 async function saveProfile(e:FormEvent<HTMLFormElement>){e.preventDefault();if(!editingDentist)return;await run(async()=>{await api(`/api/onboarding/dentists/${editingDentist.id}`,{method:'PUT',body:JSON.stringify({fullName:editName,licenseNo:editLicense,specialty:editSpecialty})});setEditingDentist(null)},'Dentist profile updated')}
 return <div className="ws-panel ws-management ws-staff-onboarding">
  {error&&<p role="alert" className="ws-alert">{error}</p>}
  {(section==='all'||section==='invitations')&&<>
  <header className="ws-config-section-heading"><span className="ws-config-section-icon" aria-hidden="true"><StaffIcon kind="profile"/></span><div><span className="ws-eyebrow">Team / Access</span><h2>Staff invitations</h2><p>Invited staff must accept with the same verified Google email. Share one-time links privately; no email is sent automatically.</p></div></header>
  <form className="ws-staff-form" onSubmit={e => { e.preventDefault(); void run(async () => {
   const result = await api<{id:string;token:string}>('/api/onboarding/invitations', json({email:email.trim(),role,branchId:branch})); setToken(result.token);setLinkInvitationId(result.id); setEmail('')
  }, 'One-time invitation created') }}>
   <label>Email<input type="email" required value={email} maxLength={254} placeholder="name@example.com" onChange={e=>setEmail(e.target.value)} /></label>
    <Dropdown label="Role" required value={role} options={[{value:'DENTIST',label:'Dentist'},{value:'FRONT_DESK',label:'Front Desk'},{value:'ADMIN',label:'Administrator'}]} onChange={value=>{if(INVITE_ROLES.includes(value as InviteRole))setRole(value as InviteRole)}}/>
    <Dropdown label="Branch" required value={branch} placeholder="Select branch" options={branches.filter(x=>x.active).map(x=>({value:x.id,label:x.name}))} onChange={setBranch}/>
   <button className="ws-care-primary" disabled={busy || !branch}><StaffIcon kind="add"/>Create invitation</button>
  </form>
  <header className="ws-config-subheading"><span className="ws-config-section-icon" aria-hidden="true"><StaffIcon kind="copy"/></span><h3>Recent invitations</h3></header>{invites.length===0?<div className="ws-staff-empty"><span className="ws-staff-empty-icon"><StaffIcon kind="copy"/></span><div><strong>No invitations yet</strong><p>Create an invitation above to add a dentist or staff member to your clinic.</p></div></div>:<div className="ws-staff-invitation-list">{invites.map(i=>{const linkReady=Boolean(token&&linkInvitationId===i.id);const canRenew=!i.accepted_at&&!i.revoked_at;return <article className={`ws-staff-invitation${linkReady?' has-link':''}`} key={i.id}><span className="ws-staff-invitation-icon"><StaffIcon kind="profile"/></span><div className="ws-staff-invitation-person"><strong>{i.email}</strong><span>{i.role.replaceAll('_',' ')}</span><span className="ws-staff-invitation-meta">{invitationMeta(i)}</span></div><span className={`ws-staff-status is-${i.status.toLowerCase()}`}>{i.status}</span><div className="ws-staff-invitation-actions">{linkReady?<><span className="ws-staff-link-ready">Link ready</span><button type="button" className="ws-care-secondary" onClick={() => {void navigator.clipboard.writeText(`${window.location.origin}/accept-invitation?token=${encodeURIComponent(token)}`)}}><StaffIcon kind="copy"/>Copy link</button><button type="button" className="ws-care-secondary" onClick={()=>{window.location.assign(`/accept-invitation?token=${encodeURIComponent(token)}`)}}><StaffIcon kind="open"/>Open</button><button type="button" className="ws-icon-button" aria-label="Dismiss invitation link" onClick={()=>setToken('')}><StaffIcon kind="remove"/></button></>:canRenew?<button type="button" className="ws-care-secondary ws-staff-generate" disabled={busy} onClick={()=>void renewLink(i)}><StaffIcon kind="renew"/>Generate link</button>:null}{canRenew&&<button className="ws-care-danger" disabled={busy} onClick={()=>void run(()=>api(`/api/onboarding/invitations/${i.id}/revoke`,{method:'POST'}),'Invitation revoked')}><StaffIcon kind="remove"/>Revoke</button>}</div></article>})}</div>}
  </>}
  {(section==='all'||section==='dentists')&&<>
  <header className="ws-config-section-heading"><span className="ws-config-section-icon" aria-hidden="true"><StaffIcon kind="profile"/></span><div><span className="ws-eyebrow">Clinical team</span><h2>Dentist profiles</h2><p>Link professional details only to an existing active Dentist account in this clinic.</p></div></header>
  <form className="ws-staff-form" onSubmit={e=>{e.preventDefault();void run(async()=>{await api('/api/onboarding/dentists',json({userId:staffId,fullName:name,licenseNo:license,specialty}));setName('');setLicense('');setSpecialty('')},'Dentist profile created')}}>
    <Dropdown label="Dentist account" required value={staffId} placeholder="Select dentist" options={[{value:'',label:'Select dentist'},...staff.filter(s=>s.role==='DENTIST'&&s.active&&!dentists.some(d=>d.user_id===s.id)).map(s=>({value:s.id,label:`${s.full_name} · ${s.email}`}))]} onChange={setStaffId}/>
   <label>Professional full name<input required value={name} placeholder="e.g. Dr. Alex Reyes" onChange={e=>setName(e.target.value)}/></label>
   <label>License number<input required value={license} placeholder="Enter professional license number" onChange={e=>setLicense(e.target.value)}/></label>
   <label>Specialty<input value={specialty} placeholder="e.g. General dentistry" onChange={e=>setSpecialty(e.target.value)}/></label>
   <button className="ws-care-primary" disabled={busy||!staffId}><StaffIcon kind="profile"/>Create dentist profile</button>
  </form>
  {dentists.length>0&&<div className="ws-staff-invitation-list ws-dentist-profile-list">{dentists.map(d=><article key={d.id} className="ws-staff-invitation"><span className="ws-staff-invitation-icon"><StaffIcon kind="profile"/></span><div className="ws-staff-invitation-person"><strong>{d.full_name}</strong><span>{d.license_no} · {d.specialty||'General dentistry'}</span></div><button type="button" className="ws-care-secondary" onClick={()=>editProfile(d)}><StaffIcon kind="edit"/>Edit</button></article>)}</div>}
  </>}
  {editingDentist&&<div className="ws-modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget&&!busy)setEditingDentist(null)}}><section className="ws-modal ws-staff-edit-modal" role="dialog" aria-modal="true" aria-labelledby="staff-edit-dentist-title"><header className="ws-modal-header"><div><span className="ws-eyebrow">Staff &amp; roles</span><h2 id="staff-edit-dentist-title">Edit dentist profile</h2></div><button type="button" className="ws-icon-button" aria-label="Close edit profile dialog" disabled={busy} onClick={()=>setEditingDentist(null)}>×</button></header><p className="ws-staff-edit-account">Linked staff account: <strong>{staff.find(s=>s.id===editingDentist.user_id)?.email??'Account'}</strong></p><form onSubmit={e=>void saveProfile(e)}><label>Professional full name<input required maxLength={160} placeholder="e.g. Dr. Alex Reyes" value={editName} onChange={e=>setEditName(e.target.value)}/></label><label>License number<input required maxLength={100} placeholder="Enter professional license number" value={editLicense} onChange={e=>setEditLicense(e.target.value)}/></label><label>Specialty<input maxLength={150} placeholder="e.g. General dentistry" value={editSpecialty} onChange={e=>setEditSpecialty(e.target.value)}/></label><footer className="ws-modal-actions"><button type="button" className="ws-care-secondary" disabled={busy} onClick={()=>setEditingDentist(null)}>Cancel</button><button type="submit" className="ws-care-primary" disabled={busy}><StaffIcon kind="edit"/>{busy?'Saving…':'Save changes'}</button></footer></form></section></div>}
 </div>
}
