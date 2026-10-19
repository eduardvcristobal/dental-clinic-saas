import { useEffect, useState } from 'react'
import { api } from '../api/client'

type Branch = { id: string; name: string; active: boolean }
type Staff = { id: string; full_name: string; email: string; role: string; active: boolean }
type Invitation = { id: string; email: string; role: string; branch_id: string; expires_at: string; accepted_at: string | null; revoked_at: string | null; status: 'Accepted' | 'Revoked' | 'Expired' | 'Pending' }
type Dentist = { id: string; user_id: string; full_name: string; license_no: string; specialty: string; active: boolean }
const json = (data: unknown) => ({ method: 'POST', body: JSON.stringify(data) })
export function StaffOnboarding() {
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
 const [staffId, setStaffId] = useState('')
 const [token, setToken] = useState('')
 const [error, setError] = useState('')
 const [notice, setNotice] = useState('')
 const [busy, setBusy] = useState(false)
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
   try { await refresh() } catch (e) { if (active) setError(String(e)) }
  }
  void load()
  return () => { active = false }
 }, [])
 async function run(action: () => Promise<unknown>, success: string) {
  setBusy(true); setError(''); setNotice('')
  try { await action(); await refresh(); setNotice(success) }
  catch (e) { setError(String(e)) }
  finally { setBusy(false) }
 }
 return <div className="ws-panel ws-management">
  <h3>Staff invitations</h3><p className="ws-subtle">Invited staff must accept using the same verified Google email. Invite tokens are shown once; share them privately. No invitation email is sent automatically.</p>
  {error && <p role="alert" className="ws-alert">{error}</p>}{notice && <p role="status">{notice}</p>}
  <form onSubmit={e => { e.preventDefault(); void run(async () => {
   const result = await api<{token:string}>('/api/onboarding/invitations', json({email,role,branchId:branch})); setToken(result.token); setEmail('')
  }, 'One-time invitation created') }}>
   <label>Email<input type="email" required value={email} maxLength={254} onChange={e=>setEmail(e.target.value)} /></label>
   <label>Role<select value={role} onChange={e=>setRole(e.target.value)}><option value="DENTIST">Dentist</option><option value="FRONT_DESK">Front Desk</option><option value="ADMIN">Administrator</option></select></label>
   <label>Branch<select required value={branch} onChange={e=>setBranch(e.target.value)}>{branches.filter(x=>x.active).map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></label>
   <button disabled={busy || !branch}>Create invitation</button>
  </form>
  {token && <div className="ws-panel"><strong>Copy this token now</strong><p className="ws-subtle">It will not be displayed again. Send the recipient the private acceptance link. Google must verify the matching email. Treat the token as a credential.</p><textarea readOnly aria-label="One-time invitation token" value={`${window.location.origin}/accept-invitation?token=${encodeURIComponent(token)}`} rows={3}/><button type="button" onClick={() => {void navigator.clipboard.writeText(`${window.location.origin}/accept-invitation?token=${encodeURIComponent(token)}`)}}>Copy token</button><button type="button" onClick={()=>setToken('')}>Dismiss</button></div>}
  <h3>Recent invitations</h3>{invites.map(i=><div className="ws-management-row" key={i.id}><span>{i.email} · {i.role}</span><span>{i.status}</span>{!i.accepted_at&&!i.revoked_at&&i.status!=='Expired'&&<button disabled={busy} onClick={()=>void run(()=>api(`/api/onboarding/invitations/${i.id}/revoke`,{method:'POST'}),'Invitation revoked')}>Revoke</button>}</div>)}
  <h3>Dentist profiles</h3><p className="ws-subtle">Link a professional profile only to an existing active Dentist staff account in this clinic.</p>
  <form onSubmit={e=>{e.preventDefault();void run(async()=>{await api('/api/onboarding/dentists',json({userId:staffId,fullName:name,licenseNo:license,specialty}));setName('');setLicense('');setSpecialty('')},'Dentist profile created')}}>
   <label>Dentist account<select required value={staffId} onChange={e=>setStaffId(e.target.value)}><option value="">Select dentist</option>{staff.filter(s=>s.role==='DENTIST'&&s.active&&!dentists.some(d=>d.user_id===s.id)).map(s=><option key={s.id} value={s.id}>{s.full_name} · {s.email}</option>)}</select></label>
   <label>Professional full name<input required value={name} onChange={e=>setName(e.target.value)}/></label>
   <label>License number<input required value={license} onChange={e=>setLicense(e.target.value)}/></label>
   <label>Specialty<input value={specialty} onChange={e=>setSpecialty(e.target.value)}/></label>
   <button disabled={busy||!staffId}>Create dentist profile</button>
  </form>
  {dentists.map(d=><div key={d.id} className="ws-management-row"><strong>{d.full_name}</strong><span>{d.license_no} · {d.specialty||'General dentistry'}</span></div>)}
 </div>
}
