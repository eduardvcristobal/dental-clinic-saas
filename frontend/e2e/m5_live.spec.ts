import {test,expect,type APIRequestContext} from '@playwright/test'

const backend=process.env.M4_BACKEND_URL??'http://127.0.0.1:8081'
const jwks=process.env.M4_JWKS_URL??'http://127.0.0.1:17890'
const origin='http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174')
const patient='44000000-0000-4000-8000-000000000031'
const dentist='44000000-0000-4000-8000-000000000041'
async function signIn(request:APIRequestContext,email:string) {
 const tokenResponse=await request.post(jwks+'/token',{data:{email}})
 expect(tokenResponse.ok()).toBeTruthy()
 const idToken=(await tokenResponse.json()).idToken as string
 const clinicSlug=email==='other-owner@m25.test'?'m4-live-b':'m4-live-a'
 const login=await request.post(backend+'/api/auth/google',{headers:{Origin:origin},data:{clinicSlug,idToken}})
 expect(login.status()).toBe(200)
 const body=await login.json()
 return {token:body.accessToken as string,cookie:login.headers()['set-cookie']?.match(/dental_refresh=([^;]+)/)?.[1]??''}
}
const headers=(token:string)=>({Authorization:'Bearer '+token})

test('M5 real-backend clinical workflow: encounter, SOAP, diagnosis, prescriptions, signing, amendments and tenancy',async({page,request})=>{
 const d=await signIn(request,'dentist@m25.test')
 const created=await request.post(backend+'/api/encounters',{headers:headers(d.token),data:{patientId:patient,dentistId:dentist}})
 expect(created.status()).toBe(201)
 const encounter=await created.json() as {id:string}
 const base=backend+'/api/encounters/'+encounter.id
 const clinical=base+'/clinical'

 const soap=await request.put(clinical+'/soap',{headers:headers(d.token),data:{
   subjective:'Synthetic pain history',objective:'Synthetic examination only',assessment:'Synthetic assessment',
   planNotes:'Synthetic plan',expectedVersion:0
 }})
 expect(soap.status()).toBe(200)
 expect((await soap.json()).version).toBe(1)

 const diagnosis=await request.post(clinical+'/diagnoses',{headers:headers(d.token),data:{
   codeSystem:'LOCAL',code:'TEST',description:'Synthetic local diagnosis'
 }})
 expect(diagnosis.status()).toBe(201)

 const rx=await request.post(base+'/records/prescriptions',{headers:headers(d.token),data:{
   medication:'Synthetic medication (test only)',dosage:'Synthetic dose',instructions:'Synthetic instructions'
 }})
 expect(rx.status()).toBe(201)
 expect((await rx.json()).status).toBe('DRAFT')

 const frontDesk=await signIn(request,'frontdesk@m25.test')
 expect((await request.get(clinical+'/soap',{headers:headers(frontDesk.token)})).status()).toBe(403)
 const other=await signIn(request,'other-owner@m25.test')
 expect((await request.get(clinical+'/soap',{headers:headers(other.token)})).status()).toBe(404)

 // A second tab/session reading the same clinical draft sees the saved version.
 await page.context().addCookies([{name:'dental_refresh',value:d.cookie,domain:'127.0.0.1',path:'/api/auth',httpOnly:true,secure:false,sameSite:'Strict'}])
 await page.goto('/workspace/patient-360?patient='+patient)
 await page.getByRole('tab',{name:/Visits|Encounters/}).click()
 await expect(page.getByText('Synthetic pain history')).not.toBeVisible()
 await expect(page.getByLabel('Subjective')).toHaveValue('Synthetic pain history')

 const sign=await request.post(base+'/signature',{headers:headers(d.token),data:{draftVersion:1}})
 expect(sign.status()).toBe(201)
 expect((await sign.json()).signed).toBe(true)
 const locked=await request.put(clinical+'/soap',{headers:headers(d.token),data:{
   subjective:'Malicious overwrite',objective:'',assessment:'',planNotes:'',expectedVersion:1
 }})
 expect(locked.status()).toBe(409)
 const wrongRx=await request.post(base+'/records/prescriptions',{headers:headers(d.token),data:{
   medication:'Another',dosage:'Synthetic',instructions:'Not after signing'
 }})
 expect(wrongRx.status()).toBe(409)

 const amend=await request.post(base+'/signature/amendments',{headers:headers(d.token),data:{
   reason:'Synthetic correction',amendment:'Clarification kept separate from signed note'
 }})
 expect(amend.status()).toBe(201)
 const signed=await request.get(base+'/signature',{headers:headers(d.token)})
 expect((await signed.json()).note.subjective).toBe('Synthetic pain history')
 const amends=await request.get(base+'/signature/amendments',{headers:headers(d.token)})
 expect((await amends.json()).length).toBe(1)

 // Patient-wide read does not prove a finding belongs to this encounter.
 const odontogram=await request.get(backend+'/api/odontogram/patients/'+patient,{headers:headers(d.token)})
 expect(odontogram.status()).toBe(200)
})
