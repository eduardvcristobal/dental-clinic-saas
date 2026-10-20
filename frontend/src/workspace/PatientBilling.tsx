import { useEffect,useState } from 'react'
import { api } from '../api/client'
type Invoice={id:string;invoice_no:string;status:string;currency_code:string;subtotal:number;total:number;version:number;plan_id:string|null;lines?:Line[]}
type Line={id:string;description:string;quantity:number;unit_price:number;discount:number;line_total:number}
type Branch={id:string;name:string}
type Plan={id:string;title:string;status:string}
type Payment={id:string;amount:number;method:string;voided_at:string|null;paid_at:string}
type PlanItem={id:string;procedure_name:string;status:string}
type Installment={id:string;due_date:string;amount:number;paid:boolean}
const fmt=(value:number,currency:string)=>new Intl.NumberFormat('en',{style:'currency',currency:currency||'PHP'}).format(value)
export function PatientBilling({patientId,role}:{patientId:string;role:string}){
 const [invoices,setInvoices]=useState<Invoice[]>([]),[selected,setSelected]=useState('')
 const [detail,setDetail]=useState<Invoice|null>(null),[branches,setBranches]=useState<Branch[]>([])
 const [plans,setPlans]=useState<Plan[]>([]),[payments,setPayments]=useState<Payment[]>([])
 const [branchId,setBranchId]=useState(''),[planId,setPlanId]=useState('')
 const [description,setDescription]=useState(''),[quantity,setQuantity]=useState(1)
 const [unitPrice,setUnitPrice]=useState(0),[discount,setDiscount]=useState(0)
 const [paymentAmount,setPaymentAmount]=useState(0),[paymentMethod,setPaymentMethod]=useState('CASH'),[reference,setReference]=useState('')
 const [balance,setBalance]=useState<{balance:number;paid:number}|null>(null)
 const [error,setError]=useState(''),[busy,setBusy]=useState(false)
 const [planItems,setPlanItems]=useState<PlanItem[]>([]),[installments,setInstallments]=useState<Installment[]>([])
 const [dueDate,setDueDate]=useState(''),[installmentAmount,setInstallmentAmount]=useState(0)
 const [reconciled,setReconciled]=useState<{overdue:number;netCollected:number;installments:Array<{id:string;dueDate:string;allocated:number;remaining:number;status:string}>}|null>(null)
 const [refundPayment,setRefundPayment]=useState(''),[refundAmount,setRefundAmount]=useState(0)
 const [refundMethod,setRefundMethod]=useState('CASH'),[refundReference,setRefundReference]=useState(''),[refundReason,setRefundReason]=useState('')
 async function reload(){const rows=await api<Invoice[]>(`/api/billing/patients/${patientId}`);setInvoices(rows);setSelected(old=>rows.some(x=>x.id===old)?old:rows[0]?.id??'')}
 useEffect(()=>{let live=true;setInvoices([]);setSelected('');setDetail(null);setError('')
  void Promise.all([api<Invoice[]>(`/api/billing/patients/${patientId}`),
   api<Branch[]>('/api/clinic-management/branches'),api<Plan[]>(`/api/billing/patients/${patientId}/plans`)])
   .then(([rows,b,p])=>{if(live){setInvoices(rows);setSelected(rows[0]?.id??'');setBranches(b);setBranchId(b[0]?.id??'');setPlans(p)}})
   .catch(e=>{if(live)setError(String(e))});return()=>{live=false}
 },[patientId])
 useEffect(()=>{let live=true;setDetail(null);setPayments([]);setBalance(null)
  if(!selected)return
  void Promise.all([api<Invoice>(`/api/billing/invoices/${selected}`),
   api<Payment[]>(`/api/billing/invoices/${selected}/payments`),
   api<{balance:number;paid:number}>(`/api/billing/invoices/${selected}/balance`)])
   .then(([d,p,b])=>{if(live){setDetail(d);setPayments(p);setBalance(b)}}).catch(e=>{if(live)setError(String(e))})
  return()=>{live=false}
 },[selected])
 async function refresh(){await reload();if(selected){setDetail(await api<Invoice>(`/api/billing/invoices/${selected}`));setPayments(await api<Payment[]>(`/api/billing/invoices/${selected}/payments`));setBalance(await api(`/api/billing/invoices/${selected}/balance`))}}
 async function act(op:()=>Promise<unknown>){setBusy(true);setError('');try{await op();await refresh()}catch(e){setError(e instanceof Error?e.message:'Billing request failed')}finally{setBusy(false)}}
 useEffect(()=>{let active=true;setPlanItems([]);setInstallments([]);setReconciled(null)
  if(!detail)return
  void Promise.all([api<Installment[]>(`/api/billing/invoices/${detail.id}/installments`),
   api<{overdue:number;netCollected:number;installments:Array<{id:string;dueDate:string;allocated:number;remaining:number;status:string}>}>(`/api/billing/invoices/${detail.id}/installment-reconciliation?asOf=${new Date().toLocaleDateString('en-CA')}`)])
   .then(([v,a])=>{if(active){setInstallments(v);setReconciled(a)}}).catch(e=>{if(active)setError(String(e))})
  if(detail.plan_id)void api<PlanItem[]>(`/api/billing/invoices/${detail.id}/plan-charges`)
   .then(x=>{if(active)setPlanItems(x)}).catch(e=>{if(active)setError(String(e))})
  return()=>{active=false}
 },[detail?.id,detail?.plan_id])
 const currency=detail?.currency_code??'PHP'
 return <section className="ws-panel" aria-label="Patient billing">
  <h3>Patient invoices & collections</h3><p>Real financial records; subject to clinic billing permissions.</p>
  {error&&<p role="alert">{error}</p>}
  <h4>Create invoice draft</h4>
  <label>Clinic branch <select value={branchId} onChange={e=>setBranchId(e.target.value)}><option value="">Select branch</option>{branches.map(b=><option key={b.id} value={b.id}>{b.name}</option>)}</select></label>
  <label>Treatment plan (optional) <select value={planId} onChange={e=>setPlanId(e.target.value)}><option value="">No treatment plan</option>
   {plans.filter(p=>['ACCEPTED','IN_PROGRESS','COMPLETED'].includes(p.status)).map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></label>
  <button disabled={busy||!branchId} onClick={()=>void act(async()=>{
   const created=await api<Invoice>('/api/billing/invoices',{method:'POST',body:JSON.stringify({patientId,branchId,planId:planId||null})})
   setSelected(created.id)
  })}>Create draft invoice</button>
  <h4>Invoices</h4>
  <label>Invoice <select value={selected} onChange={e=>setSelected(e.target.value)}><option value="">Select</option>{invoices.map(x=><option key={x.id} value={x.id}>{x.invoice_no} — {x.status}</option>)}</select></label>
  {detail&&<>
   <p><strong>{detail.invoice_no}</strong> — {detail.status}</p>
   <p>Total: <strong>{fmt(Number(detail.total),currency)}</strong> · Paid: {fmt(Number(balance?.paid??0),currency)} · Outstanding: <strong>{fmt(Number(balance?.balance??0),currency)}</strong></p>
   <table><thead><tr><th>Description</th><th>Quantity</th><th>Amount</th></tr></thead><tbody>{(detail.lines??[]).map(x=>
    <tr key={x.id}><td>{x.description}</td><td>{x.quantity}</td><td>{fmt(Number(x.line_total),currency)}</td></tr>)}</tbody></table>
   {detail.status==='DRAFT'&&<>
    {detail.plan_id&&<section aria-label="Completed plan charges"><h4>Import completed treatments</h4>
      {planItems.filter(x=>x.status==='COMPLETED').map(item=><div key={item.id}>
       <span>{item.procedure_name}</span>
       <button disabled={busy} onClick={()=>void act(()=>api(`/api/billing/invoices/${selected}/plan-charge`,{
        method:'POST',body:JSON.stringify({planItemId:item.id})
       }))}>Import charge</button></div>)}
      </section>}
    <h4>Add charge</h4>
    <label>Description <input value={description} maxLength={255} onChange={e=>setDescription(e.target.value)}/></label>
    <label>Quantity <input type="number" min={1} value={quantity} onChange={e=>setQuantity(Number(e.target.value))}/></label>
    <label>Unit price <input type="number" min={0} step=".01" value={unitPrice} onChange={e=>setUnitPrice(Number(e.target.value))}/></label>
    <label>Discount <input type="number" min={0} step=".01" value={discount} onChange={e=>setDiscount(Number(e.target.value))}/></label>
    <button disabled={busy||!description.trim()||quantity<1||unitPrice<0||discount<0}
     onClick={()=>void act(()=>api(`/api/billing/invoices/${selected}/lines`,{method:'POST',body:JSON.stringify({description,quantity,unitPrice,discount})}))}>Add line</button>
    <button disabled={busy||Number(detail.total)<=0} onClick={()=>void act(()=>api(`/api/billing/invoices/${selected}/issue`,{
     method:'POST',body:JSON.stringify({expectedVersion:detail.version})
    }))}>Issue invoice</button>
   </>}
   {['ISSUED','PARTIALLY_PAID'].includes(detail.status)&&<>
    <h4>Record payment</h4><label>Amount <input type="number" min={0.01} step=".01" value={paymentAmount} onChange={e=>setPaymentAmount(Number(e.target.value))}/></label>
    <label>Method <select value={paymentMethod} onChange={e=>setPaymentMethod(e.target.value)}>{['CASH','CARD','BANK_TRANSFER','EWALLET','HMO','OTHER'].map(m=><option key={m} value={m}>{m}</option>)}</select></label>
    <label>Reference <input value={reference} maxLength={255} onChange={e=>setReference(e.target.value)}/></label>
    <button disabled={busy||paymentAmount<=0||paymentAmount>Number(balance?.balance??0)}
     onClick={()=>void act(()=>api(`/api/billing/invoices/${selected}/payments`,{method:'POST',
      body:JSON.stringify({amount:paymentAmount,method:paymentMethod,reference:reference||null,idempotencyKey:crypto.randomUUID()})
     }))}>Record payment</button>
   </>}
   {['ISSUED','PARTIALLY_PAID'].includes(detail.status)&&<section aria-label="Installment schedule">
     <h4>Payment installments</h4>
     <ul>{installments.map(item=>{const allocation=reconciled?.installments.find(x=>x.id===item.id);return <li key={item.id}>{item.due_date} — {fmt(Number(item.amount),currency)} · {allocation?`${allocation.status}: ${fmt(Number(allocation.remaining),currency)} outstanding`:'Allocation pending'}</li>})}</ul>
     <p>Allocated net collections: {fmt(Number(reconciled?.netCollected??0),currency)} · Past-due installments: {fmt(Number(reconciled?.overdue??0),currency)}. FIFO estimate as of today; does not settle a payment with a provider.</p>
     <label>Due date <input type="date" value={dueDate} onChange={e=>setDueDate(e.target.value)}/></label>
     <label>Installment amount <input type="number" min={0.01} step=".01" value={installmentAmount} onChange={e=>setInstallmentAmount(Number(e.target.value))}/></label>
     <button disabled={busy||!dueDate||installmentAmount<=0} onClick={()=>void act(()=>api(`/api/billing/invoices/${selected}/installments`,{
      method:'POST',body:JSON.stringify({dueDate,amount:installmentAmount})
     }))}>Add installment</button>
    </section>}
   <h4>Payment history</h4>
   <ul>{payments.map(p=><li key={p.id}>{new Date(p.paid_at).toLocaleDateString()} — {fmt(Number(p.amount),currency)} ({p.method}) {p.voided_at?'VOIDED':''}</li>)}</ul>
   {['OWNER','ADMIN'].includes(role)&&payments.some(p=>!p.voided_at)&&<section aria-label="Manual refund evidence">
    <h4>Record an already-disbursed refund</h4>
    <p>This records evidence of money returned outside this system. It does not send funds.</p>
    <label>Original payment <select value={refundPayment} onChange={e=>setRefundPayment(e.target.value)}><option value="">Choose payment</option>
     {payments.filter(p=>!p.voided_at).map(p=><option key={p.id} value={p.id}>{fmt(Number(p.amount),currency)} — {p.method} ({p.id.slice(0,8)})</option>)}</select></label>
    <label>Returned amount <input type="number" min={0.01} step=".01" value={refundAmount} onChange={e=>setRefundAmount(Number(e.target.value))}/></label>
    <label>Disbursement method <select value={refundMethod} onChange={e=>setRefundMethod(e.target.value)}>{['CASH','CARD','BANK_TRANSFER','EWALLET','OTHER'].map(m=><option key={m} value={m}>{m}</option>)}</select></label>
    <label>External evidence reference <input value={refundReference} maxLength={255} onChange={e=>setRefundReference(e.target.value)}/></label>
    <label>Refund reason <input value={refundReason} maxLength={500} onChange={e=>setRefundReason(e.target.value)}/></label>
    <button disabled={busy||!refundPayment||refundAmount<=0||refundReference.trim().length<3||refundReason.trim().length<3}
      onClick={()=>void act(()=>api(`/api/billing/invoices/${selected}/payments/${refundPayment}/refunds`,{method:'POST',body:JSON.stringify({
       amount:refundAmount,method:refundMethod,reference:refundReference,reason:refundReason,idempotencyKey:crypto.randomUUID()
      })}))}>Record external refund evidence</button>
   </section>}
   <p>Payment acknowledgments and billing previews are not official tax receipts or invoices. Fiscal document issuance is disabled pending regulatory approval.</p>
  </>}
 </section>
}
