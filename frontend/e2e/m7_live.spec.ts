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
})
