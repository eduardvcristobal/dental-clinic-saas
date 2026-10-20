import {test,expect,type APIRequestContext} from '@playwright/test'
const backend=process.env.M4_BACKEND_URL??'http://127.0.0.1:8081'
const jwks=process.env.M4_JWKS_URL??'http://127.0.0.1:17890'
const origin='http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174')
async function token(request:APIRequestContext,email:string){
 const provider=await request.post(jwks+'/token',{data:{email}})
 expect(provider.status()).toBe(200)
 const {idToken}=await provider.json()
 const clinicSlug=email==='other-owner@m25.test'?'m4-live-b':'m4-live-a'
 const result=await request.post(backend+'/api/auth/google',{headers:{Origin:origin},data:{clinicSlug,idToken}})
 expect(result.status()).toBe(200)
 return (await result.json()).accessToken as string
}
const h=(t:string)=>({Authorization:'Bearer '+t})
const patient='44000000-0000-4000-8000-000000000031'
test('M8 notifications: disabled provider, tenant policy, consent and recall boundaries',async({request})=>{
 const owner=await token(request,'owner@m25.test')
 const front=await token(request,'frontdesk@m25.test')
 const foreign=await token(request,'other-owner@m25.test')
 const api=backend+'/api/notifications'
 const defaultPolicy=await request.get(api+'/policy',{headers:h(owner)})
 expect(defaultPolicy.status()).toBe(200)
 expect((await defaultPolicy.json()).enabled).toBe(false)
 expect((await request.put(api+'/policy',{headers:h(front),data:{enabled:true,emailEnabled:true,smsEnabled:true,reminderHoursBefore:24}})).status()).toBe(403)
 expect((await request.put(api+'/policy',{headers:h(owner),data:{enabled:true,emailEnabled:true,smsEnabled:false,reminderHoursBefore:24}})).status()).toBe(200)
 expect((await request.put(api+'/consents',{headers:h(foreign),data:{patientId:patient,channel:'EMAIL',consented:true}})).status()).toBe(400)
 expect((await request.put(api+'/consents',{headers:h(owner),data:{patientId:patient,channel:'EMAIL',consented:true}})).status()).toBe(200)
 const consent=await request.get(api+'/consents/'+patient,{headers:h(owner)})
 expect((await consent.json()).some((c:{channel:string;consented:boolean})=>c.channel==='EMAIL'&&c.consented)).toBe(true)
 const recall=await request.post(api+'/recalls',{headers:h(front),data:{patientId:patient,dueDate:'2027-01-20',reason:'Synthetic routine checkup'}})
 expect(recall.status()).toBe(201)
 const {id}=await recall.json()
 expect((await request.post(api+'/recalls/'+id+'/schedule?channel=EMAIL',{headers:h(front)})).status()).toBe(200)
 const list=await request.get(api+'/queue',{headers:h(owner)})
 expect(list.status()).toBe(200)
 const queued=await list.json() as Array<{id:string;status:string;recall_id:string}>
 expect(queued.some(x=>x.recall_id===id&&x.status==='PENDING')).toBe(true)
 expect((await request.get(api+'/queue',{headers:h(foreign)})).status()).toBe(200)
 const other=await request.get(api+'/queue',{headers:h(foreign)})
 expect((await other.json() as Array<{recall_id:string}>).some(x=>x.recall_id===id)).toBe(false)
 expect((await request.put(api+'/consents',{headers:h(owner),data:{patientId:patient,channel:'EMAIL',consented:false}})).status()).toBe(200)
 const latest=await request.get(api+'/queue',{headers:h(owner)})
 expect((await latest.json() as Array<{recall_id:string;status:string}>).some(x=>x.recall_id===id&&x.status==='SUPPRESSED')).toBe(true)
})
