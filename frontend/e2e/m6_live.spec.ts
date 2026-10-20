import { test,expect,type APIRequestContext } from '@playwright/test'
const backend=process.env.M4_BACKEND_URL??'http://127.0.0.1:8081'
const jwks=process.env.M4_JWKS_URL??'http://127.0.0.1:17890'
const origin='http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174')
const patient='44000000-0000-4000-8000-000000000031'
const dentist='44000000-0000-4000-8000-000000000041'
const procedure='44000000-0000-4000-8000-000000000051'
async function login(request:APIRequestContext,email:string){
 const provider=await request.post(jwks+'/token',{data:{email}})
 expect(provider.status()).toBe(200)
 const {idToken}=await provider.json()
 const clinicSlug=email==='other-owner@m25.test'?'m4-live-b':'m4-live-a'
 const response=await request.post(backend+'/api/auth/google',{headers:{Origin:origin},data:{clinicSlug,idToken}})
 expect(response.status()).toBe(200)
 const body=await response.json()
 return {token:body.accessToken as string,cookie:response.headers()['set-cookie']?.match(/dental_refresh=([^;]+)/)?.[1]??''}
}
const h=(token:string)=>({Authorization:'Bearer '+token})
test('M6 plan builder, estimates, consent, completion evidence and isolation on Spring Boot + PostgreSQL',async({page,request})=>{
 const d=await login(request,'dentist@m25.test')
 const front=await login(request,'frontdesk@m25.test')
 const other=await login(request,'other-owner@m25.test')
 const base=backend+'/api/treatment-plans'
 expect((await request.get(base+'/catalog',{headers:h(front.token)})).status()).toBe(403)
 const created=await request.post(base,{headers:h(d.token),data:{patientId:patient,dentistId:dentist,title:'Synthetic restorative treatment plan'}})
 expect(created.status()).toBe(201)
 const plan=await created.json() as {id:string}
 const url=base+'/'+plan.id
 expect((await request.get(url,{headers:h(other.token)})).status()).toBe(404)
 const negative=await request.post(url+'/items',{headers:h(d.token),data:{
  procedureId:procedure,toothNumber:16,surface:null,phase:1,quantity:1,unitPrice:-1,discount:0
 }})
 expect(negative.status()).toBe(400)
 const added=await request.post(url+'/items',{headers:h(d.token),data:{
  procedureId:procedure,toothNumber:16,surface:null,phase:1,quantity:2,unitPrice:1500,discount:200
 }})
 expect(added.status()).toBe(201)
 let detail=await added.json() as {items:Array<{id:string;status:string}>;estimate_total:number;version:number;status:string}
 expect(Number(detail.estimate_total)).toBe(2800)
 expect(detail.items).toHaveLength(1)
 const item=detail.items[0].id
 const preview=await request.get(url+'/billing-preview',{headers:h(d.token)})
 expect(preview.status()).toBe(200)
 expect((await preview.json()).invoiceCreated).toBe(false)
 const proposed=await request.post(url+'/transition',{headers:h(d.token),data:{status:'PROPOSED',expectedVersion:detail.version}})
 expect(proposed.status()).toBe(200)
 detail=await proposed.json()
 const missingConsent=await request.post(url+'/transition',{headers:h(d.token),data:{status:'ACCEPTED',expectedVersion:detail.version}})
 expect(missingConsent.status()).toBe(400)
 const accepted=await request.post(url+'/transition',{headers:h(d.token),data:{
  status:'ACCEPTED',expectedVersion:detail.version,consentReference:'SYNTHETIC-CONSENT-001'
 }})
 expect(accepted.status()).toBe(200)
 detail=await accepted.json()
 expect(detail.status).toBe('ACCEPTED')
 const started=await request.post(url+'/transition',{headers:h(d.token),data:{status:'IN_PROGRESS',expectedVersion:detail.version}})
 expect(started.status()).toBe(200)
 detail=await started.json()
 const premature=await request.post(url+'/items/'+item+'/progress',{headers:h(d.token),data:{status:'COMPLETED'}})
 expect(premature.status()).toBe(400)
 const encounter=await request.post(backend+'/api/encounters',{headers:h(d.token),data:{patientId:patient,dentistId:dentist}})
 expect(encounter.status()).toBe(201)
 const e=await encounter.json() as {id:string;version:number}
 const completedEncounter=await request.post(backend+'/api/encounters/'+e.id+'/transition',{headers:h(d.token),data:{
  status:'COMPLETED',expectedVersion:e.version
 }})
 expect(completedEncounter.status()).toBe(200)
 const progress=await request.post(url+'/items/'+item+'/progress',{headers:h(d.token),data:{
  status:'COMPLETED',visitId:e.id
 }})
 expect(progress.status()).toBe(200)
 detail=await progress.json()
 expect(detail.items[0].status).toBe('COMPLETED')
 const done=await request.post(url+'/transition',{headers:h(d.token),data:{status:'COMPLETED',expectedVersion:detail.version}})
 expect(done.status()).toBe(200)
 expect((await done.json()).status).toBe('COMPLETED')
 const audit=await request.get(url+'/history',{headers:h(d.token)})
 expect(audit.status()).toBe(200)
 expect((await audit.json()).length).toBeGreaterThanOrEqual(5)
 await page.context().addCookies([{name:'dental_refresh',value:d.cookie,domain:'127.0.0.1',path:'/api/auth',httpOnly:true,secure:false,sameSite:'Strict'}])
 await page.goto('/workspace/patient-360?patient='+patient)
 await page.getByRole('tab',{name:/Treatment plans/i}).click()
 await expect(page.getByRole('heading',{name:/Synthetic restorative treatment plan/})).toBeVisible()
})
