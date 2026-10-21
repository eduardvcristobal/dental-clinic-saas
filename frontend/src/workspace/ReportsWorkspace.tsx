import {useEffect,useState} from 'react'
import {api} from '../api/client'
type Values=Record<string,number|string>
type Summary={from:string;to:string;currency:string;appointments:Values;revenue:Values;payments:Values;refunds:Values;paymentMethods:Array<{method:string;amount:number}>;patients:Values;firstVisits:Values;production:Values;receivables:Values}
export function ReportsWorkspace(){
 const today=new Date().toISOString().slice(0,10)
 const [from,setFrom]=useState(today.slice(0,8)+'01'),[to,setTo]=useState(today)
 const [data,setData]=useState<Summary|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(false)
 async function load(){
  setLoading(true);setError('')
  try{setData(await api<Summary>('/api/reports/summary?from='+encodeURIComponent(from)+'&to='+encodeURIComponent(to)))}
  catch(e){setData(null);setError(e instanceof Error?e.message:'Reports unavailable')}
  finally{setLoading(false)}
 }
 useEffect(()=>{void load()},[])
 const n=(value:number|string|undefined)=>Number(value??0).toLocaleString()
 const cash=(value:number|string|undefined)=>Number(value??0).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})
 const rows=data?[['Appointments',n(data.appointments.scheduled)],['Completed appointments',n(data.appointments.completed)],['No-shows',n(data.appointments.no_shows)],['No-show rate (excludes cancellations)',Number(data.appointments.eligible_appointments)?(100*Number(data.appointments.no_shows)/Number(data.appointments.eligible_appointments)).toFixed(1)+'%':'N/A'],['Registered patients',n(data.patients.registered)],['Patients with first locked visit',n(data.firstVisits.first_locked_visits)],['Issued invoice amount',cash(data.revenue.invoiced)],['Gross collections',cash(data.payments.gross_collections)],['Refund disbursements',cash(data.refunds.refunds)],['Net collections',cash(Number(data.payments.gross_collections)-Number(data.refunds.refunds))],['Outstanding invoices (as of end date)',cash(data.receivables.outstanding)],['Completed plan items (last updated in range)',n(data.production.completed_items)],['Completed plan item value',cash(data.production.planned_value_completed)]]:[]
 return <section className="ws-panel" aria-label="Reports and analytics">
  <h1>Reports &amp; Analytics</h1>
  <p>Read-only clinic reporting. Date filters use UTC until clinic timezone reporting is certified. Financial figures are recorded-currency amounts; mixed-currency totals are blocked until currency-specific reports are available.</p>
  <div><label>From <input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label> <label>To <input type="date" value={to} onChange={e=>setTo(e.target.value)}/></label> <button disabled={loading||!from||!to} onClick={()=>void load()}>Update reports</button></div>
  {loading&&<p role="status">Loading clinic reports…</p>}
  {error&&<p role="alert">{error}</p>}
  {!loading&&data&&<p>Financial currency: <strong>{data.currency}</strong> (all money values use this currency)</p>}
  {!loading&&data&&<div className="ws-scroll"><table className="ws-table"><caption>Clinic reporting summary, {data.from} through {data.to}</caption><thead><tr><th scope="col">Metric</th><th scope="col">Value</th></tr></thead><tbody>{rows.map(([name,value])=><tr key={name}><th scope="row">{name}</th><td>{value}</td></tr>)}</tbody></table></div>}
  {!loading&&data&&<section><h2>Gross collections by method</h2><ul>{data.paymentMethods.map(item=><li key={item.method}>{item.method}: {cash(item.amount)} {data.currency}</li>)}</ul>{data.paymentMethods.length===0&&<p>No payments in this period.</p>}</section>}
  <p>Outstanding balances are as of the selected end date using current nonvoid payment and refund records. Completed treatment value is based on the latest item status and update timestamp, not a signed historical production ledger. These should not be used as certified accounting or clinical production reports.</p>
 </section>
}
