import { test,expect,type APIRequestContext } from '@playwright/test'
const backend=process.env.M4_BACKEND_URL??'http://127.0.0.1:8081'
const jwks=process.env.M4_JWKS_URL??'http://127.0.0.1:17890'
const origin='http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174')
const patient='44000000-0000-4000-8000-000000000031'
const branch='44000000-0000-4000-8000-000000000011'
async function login(request:APIRequestContext,email:string){
 const provider=await request.post(jwks+'/token',{data:{email}})
 expect(provider.status()).toBe(200)
 const {idToken}=await provider.json()
 const clinicSlug=email==='other-owner@m25.test'?'m4-live-b':'m4-live-a'
 const response=await request.post(backend+'/api/auth/google',{headers:{Origin:origin},data:{clinicSlug,idToken}})
 expect(response.status()).toBe(200)
 const body=await response.json()
 return body.accessToken as string
}
const auth=(token:string)=>({Authorization:'Bearer '+token})
test('M7 real-backend invoice/collections/void/security and reconciliation on PostgreSQL',async({request})=>{
 const front=await login(request,'frontdesk@m25.test')
 const dentist=await login(request,'dentist@m25.test')
 const foreign=await login(request,'other-owner@m25.test')
 const api=backend+'/api/billing'
 expect((await request.get(api+'/dashboard',{headers:auth(dentist)})).status()).toBe(403)
 const create=await request.post(api+'/invoices',{headers:auth(front),data:{patientId:patient,branchId:branch}})
 expect(create.status()).toBe(201)
 let bill=await create.json() as {id:string;version:number;status:string;total:number}
 const url=api+'/invoices/'+bill.id
 expect((await request.get(url,{headers:auth(foreign)})).status()).toBe(404)
 expect((await request.post(url+'/lines',{headers:auth(front),data:{
  description:'Negative charge rejected',quantity:1,unitPrice:-100,discount:0
 }})).status()).toBe(400)
 const line=await request.post(url+'/lines',{headers:auth(front),data:{
  description:'Synthetic dental treatment',quantity:1,unitPrice:100,discount:0
 }})
 expect(line.status()).toBe(201)
 bill=await line.json()
 expect(Number(bill.total)).toBe(100)
 expect((await request.post(url+'/issue',{headers:auth(front),data:{expectedVersion:bill.version}})).status()).toBe(200)
 expect((await request.post(url+'/issue',{headers:auth(front),data:{expectedVersion:bill.version}})).status()).toBe(409)
 const firstKey='77777777-7777-4777-8777-777777777701'
 const over=await request.post(url+'/payments',{headers:auth(front),data:{amount:101,method:'CASH',idempotencyKey:firstKey}})
 expect(over.status()).toBe(409)
 const first=await request.post(url+'/payments',{headers:auth(front),data:{amount:20,method:'CASH',idempotencyKey:firstKey}})
 expect(first.status()).toBe(201)
 expect(Number((await first.json()).balance)).toBe(80)
 const retry=await request.post(url+'/payments',{headers:auth(front),data:{amount:20,method:'CASH',idempotencyKey:firstKey}})
 expect(retry.status()).toBe(201)
 expect(Number((await retry.json()).paid)).toBe(20)
 const second=await request.post(url+'/payments',{headers:auth(front),data:{
  amount:80,method:'EWALLET',reference:'synthetic-ref',idempotencyKey:'77777777-7777-4777-8777-777777777702'
 }})
 expect(second.status()).toBe(201)
 expect(Number((await second.json()).balance)).toBe(0)
 const dashboard=await request.get(api+'/dashboard',{headers:auth(front)})
 expect(dashboard.status()).toBe(200)
 expect(Number((await dashboard.json()).received)).toBeGreaterThanOrEqual(100)
 const history=await request.get(url+'/payments',{headers:auth(front)})
 expect(history.status()).toBe(200)
 const payments=await history.json() as Array<{id:string;amount:number}>
 expect(payments).toHaveLength(2)
 const receipt=await request.get(url+'/receipts/'+payments[0].id,{headers:auth(front)})
 expect(receipt.status()).toBe(200)
 expect((await receipt.json()).notice).toContain('Not an official tax')
 expect((await request.post(url+'/void',{headers:auth(front),data:{reason:'Synthetic correction'}})).status()).toBe(403)
 expect((await request.post(url+'/void',{headers:auth(foreign),data:{reason:'Synthetic correction'}})).status()).toBe(404)
 const statement=await request.get(api+'/patients/'+patient+'/statement',{headers:auth(front)})
 expect(statement.status()).toBe(200)
 expect((await statement.json()).length).toBeGreaterThanOrEqual(1)
 // Financial closure: a recorded external disbursement must reduce net collections.
 const owner=await login(request,'owner@m25.test')
 const deniedRefund=await request.post(url+'/payments/'+payments[0].id+'/refunds',{headers:auth(front),data:{
   amount:10,method:'CASH',reference:'SYNTHETIC-RETURN-0001',reason:'Synthetic correction',idempotencyKey:'77777777-7777-4777-8777-777777777703'
 }})
 expect(deniedRefund.status()).toBe(403)
 const refundBody={amount:10,method:'CASH',reference:'SYNTHETIC-RETURN-0001',reason:'Synthetic correction',
    idempotencyKey:'77777777-7777-4777-8777-777777777703'}
 const refunded=await request.post(url+'/payments/'+payments[0].id+'/refunds',{headers:auth(owner),data:refundBody})
 expect(refunded.status()).toBe(201)
 const replay=await request.post(url+'/payments/'+payments[0].id+'/refunds',{headers:auth(owner),data:refundBody})
 expect(replay.status()).toBe(201)
 expect((await replay.json()).id).toBe((await refunded.json()).id)
 expect((await request.post(url+'/payments/'+payments[0].id+'/refunds',{headers:auth(owner),data:{...refundBody,amount:21,
   idempotencyKey:'77777777-7777-4777-8777-777777777704'}})).status()).toBe(409)
 expect((await request.post(url+'/payments/'+payments[0].id+'/void',{headers:auth(owner),data:{reason:'Not allowed after refund'}})).status()).toBe(409)
 const netBalance=await request.get(url+'/balance',{headers:auth(front)})
 expect(netBalance.status()).toBe(200)
 expect(Number((await netBalance.json()).paid)).toBe(90)
 const scheduled=await request.post(url+'/installments',{headers:auth(front),data:{dueDate:'2026-11-14',amount:10}})
 expect(scheduled.status()).toBe(201)
 const reconciliation=await request.get(url+'/installment-reconciliation?asOf=2026-11-15',{headers:auth(front)})
 expect(reconciliation.status()).toBe(200)
 expect(Number((await reconciliation.json()).netCollected)).toBe(90)
 const fiscal=await request.get(api+'/fiscal-readiness',{headers:auth(owner)})
 expect(fiscal.status()).toBe(200)
 expect((await fiscal.json()).officialReceiptEnabled).toBe(false)
 const fiscalUpdate=await request.put(api+'/fiscal-readiness',{headers:auth(owner),data:{
  jurisdiction:'PH',registeredName:'Synthetic Clinic',registeredAddress:'Synthetic Test Address 100',
  taxRegistrationNo:'SYNTHETIC-TAX-NO',taxCategory:'NON_VAT',permitReference:null
 }})
 expect(fiscalUpdate.status()).toBe(200)
 expect((await fiscalUpdate.json()).documentStatus).toBe('NOT_AUTHORIZED')
})
