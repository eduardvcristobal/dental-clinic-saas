import { expect,test } from '@playwright/test'

const backend=process.env.M4_BACKEND_URL??'http://127.0.0.1:8081'
const jwks=process.env.M4_JWKS_URL??'http://127.0.0.1:17890'
const origin='http://127.0.0.1:'+(process.env.M4_FRONTEND_PORT??'5174')
const patientA='44000000-0000-4000-8000-000000000031'
const patientB='44000000-0000-4000-8000-000000000032'

async function authenticate(email:string,request:import('@playwright/test').APIRequestContext) {
 const idResp=await request.post(jwks+'/token',{data:{email}})
 expect(idResp.status()).toBe(200)
 const idToken=(await idResp.json()).idToken as string
 const clinicSlug=email==='other-owner@m25.test'?'m4-live-b':'m4-live-a'
 const login=await request.post(backend+'/api/auth/google',{headers:{Origin:origin},
   data:{clinicSlug,idToken}})
 expect(login.status()).toBe(200)
 const s=await login.json()
 const cookie=login.headers()['set-cookie']?.match(/dental_refresh=([^;]+)/)?.[1]
 expect(cookie).toBeTruthy()
 return {token:s.accessToken as string,cookie:cookie as string}
}
function headers(token:string){return {Authorization:'Bearer '+token}}

test('clinician records odontogram findings, immutable corrections and cross-clinic denials against live Spring Boot/PostgreSQL',async({page,request})=>{
 const dentist=await authenticate('dentist@m25.test',request)
 await page.context().addCookies([{
  name:'dental_refresh',value:dentist.cookie,domain:'127.0.0.1',path:'/api/auth',
  httpOnly:true,secure:false,sameSite:'Strict'
 }])
 await page.goto('/workspace/patient-360?patient='+patientA)
 await expect(page.getByRole('heading',{name:'Synthetic Chart Patient'})).toBeVisible()
 await page.getByRole('tab',{name:'Odontogram'}).click()
 await expect(page.getByRole('heading',{name:/Odontogram/})).toBeVisible()
 await page.getByLabel('Dentition').selectOption('mixed')
 await page.getByRole('button',{name:'11'}).click()
 await page.getByLabel('Surface').selectOption('I')
 await page.getByLabel('Finding').selectOption('CARIES')
 await page.getByLabel('Notes').fill('Synthetic chart observation, not patient data')
 await page.getByRole('button',{name:'Record new finding'}).click()
 await expect(page.getByText('OBSERVATION: CARIES')).toBeVisible()

 const path=backend+'/api/odontogram/patients/'+patientA
 const current=await request.get(path,{headers:headers(dentist.token)})
 expect(current.status()).toBe(200)
 const chart=await current.json() as {chartId:string|null;events:Array<{id:string;tooth_number:number;surface:string|null;event_kind:string;condition_code:string;supersedes_event_id:string|null}>}
 expect(chart.chartId).toBeTruthy()
 expect(chart.events).toHaveLength(1)
 expect(chart.events[0].tooth_number).toBe(11)
 expect(chart.events[0].surface).toBe('I')
 await page.getByRole('button',{name:'Correct / retract'}).click()
 await page.getByLabel('Finding').selectOption('RESTORATION')
 await page.getByRole('button',{name:'Record correction'}).click()
 await expect(page.getByText('CORRECTION: RESTORATION')).toBeVisible()
 const revised=await request.get(path,{headers:headers(dentist.token)})
 const events=(await revised.json()).events as Array<{id:string;event_kind:string;supersedes_event_id:string|null}>
 expect(events).toHaveLength(2)
 expect(events[1].event_kind).toBe('CORRECTION')
 expect(events[1].supersedes_event_id).toBe(events[0].id)

 const invalid=await request.post(path+'/events',{headers:headers(dentist.token),
   data:{toothNumber:19,surface:'O',conditionCode:'CARIES',eventKind:'OBSERVATION'}})
 expect(invalid.status()).toBe(400)

 const frontDesk=await authenticate('frontdesk@m25.test',request)
 const denied=await request.get(path,{headers:headers(frontDesk.token)})
 expect(denied.status()).toBe(403)
 const otherClinic=await authenticate('other-owner@m25.test',request)
 const crossClinic=await request.get(path,{headers:headers(otherClinic.token)})
 expect(crossClinic.status()).toBe(404)
 const crossPatient=await request.get(backend+'/api/odontogram/patients/'+patientB,{headers:headers(dentist.token)})
 expect(crossPatient.status()).toBe(404)

 const original=await request.post(path+'/events',{headers:headers(dentist.token),
   data:{toothNumber:12,surface:'I',conditionCode:'CARIES',eventKind:'OBSERVATION',clinicalStatus:'EXISTING'}})
 expect(original.status()).toBe(201)
 const newEvent=await original.json() as {id:string}
 const corrective={toothNumber:12,surface:'I',conditionCode:'RESTORATION',eventKind:'CORRECTION',
   clinicalStatus:'EXISTING',supersedesEventId:newEvent.id}
 const competing=await Promise.all([
  request.post(path+'/events',{headers:headers(dentist.token),data:corrective}),
  request.post(path+'/events',{headers:headers(dentist.token),data:corrective}),
 ])
 expect(competing.map(x=>x.status()).sort()).toEqual([201,409])
})
