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
test('M9 clinic reports are read-only, scoped, and role restricted',async({request})=>{
 const owner=await token(request,'owner@m25.test')
 const other=await token(request,'other-owner@m25.test')
 const receptionist=await token(request,'frontdesk@m25.test')
 const url=backend+'/api/reports/summary?from=2026-01-01&to=2026-12-31'
 const h=(t:string)=>({Authorization:'Bearer '+t})
 const first=await request.get(url,{headers:h(owner)})
 expect(first.status()).toBe(200)
 const body=await first.json()
 expect(body).toHaveProperty('appointments.scheduled')
 expect(body).toHaveProperty('payments.gross_collections')
 expect(body).toHaveProperty('receivables.outstanding')
 expect(body).toHaveProperty('firstVisits.first_locked_visits')
 expect(body).toHaveProperty('appointments.eligible_appointments')
 expect(Array.isArray(body.paymentMethods)).toBe(true)
 expect(body).not.toHaveProperty('clinicId')
 expect((await request.get(url,{headers:h(other)})).status()).toBe(200)
 expect((await request.get(url,{headers:h(receptionist)})).status()).toBe(403)
 expect((await request.get(url)).status()).toBe(401)
 expect((await request.get(backend+'/api/reports/summary?from=invalid&to=2026-12-31',{headers:h(owner)})).status()).toBe(400)
})
