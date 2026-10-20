import { test, expect, type Page } from '@playwright/test'

const patientId='11111111-1111-4111-8111-111111111118'
const clinic={id:'11111111-1111-4111-8111-111111111111',slug:'synthetic-clinic',name:'Synthetic Clinical Test Clinic'}
const patient={id:patientId,first_name:'Synthetic',last_name:'Patient',chart_no:'M4-TEST',birth_date:null,phone:null,email:null}
type E={id:string;tooth_number:number;surface:string|null;condition_code:string;event_kind:string;clinical_status:string;notes:string|null;recorded_at:string;recorded_by:string;supersedes_event_id:string|null}

async function setup(page:Page,role:'DENTIST'|'FRONT_DESK') {
 let loggedIn=false
 const events:E[]=[]
 let chartRequests=0
 await page.addInitScript(()=>{
   const w=window as unknown as {google?:unknown;__gsiCredential?:string;__callback?: (x:{credential?:string})=>void}
   w.google={accounts:{id:{
      initialize:(c:{callback:(x:{credential?:string})=>void})=>{w.__callback=c.callback},
      renderButton:(root:HTMLElement)=>{
        const b=document.createElement('button');b.type='button';b.textContent='Sign in with Google'
        b.onclick=()=>w.__callback?.({credential:w.__gsiCredential??'synthetic:DEFAULT'})
        root.replaceChildren(b)
      },
      disableAutoSelect:()=>{}
   }}}
 })
 await page.route('https://accounts.google.com/gsi/client**',r=>r.fulfill({status:200,contentType:'application/javascript',body:'/* test double */'}))
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const p=new URL(route.request().url()).pathname
  let status=200;let payload:unknown=[]
  if(p==='/api/auth/refresh'){status=loggedIn?200:401;payload=loggedIn?{accessToken:'synthetic-token',user:{id:'clinician-1',fullName:'Clinician',email:'fake@test.invalid',role},clinic}:{}}
  else if(p==='/api/auth/google'){loggedIn=true;payload={accessToken:'synthetic-token',user:{id:'clinician-1',fullName:'Clinician',email:'fake@test.invalid',role},clinic}}
  else if(p==='/api/auth/logout'){loggedIn=false;await route.fulfill({status:204,body:''});return}
  else if(p===`/api/patients/${patientId}`)payload=patient
  else if(p===`/api/odontogram/patients/${patientId}`){
   chartRequests++
   payload={patientId,chartId:events.length?'chart-test-1':null,events}
  } else if(p===`/api/odontogram/patients/${patientId}/events` && route.request().method()==='POST'){
    const data=route.request().postDataJSON()
    const ev:E={id:'event-'+(events.length+1),tooth_number:data.toothNumber,surface:data.surface??null,
      condition_code:data.conditionCode,event_kind:data.eventKind,clinical_status:data.clinicalStatus,
      notes:data.notes,recorded_at:'2026-10-20T05:00:00Z',recorded_by:'clinician-1',supersedes_event_id:data.supersedesEventId??null}
    events.push(ev);payload=ev;status=201
  }
  await route.fulfill({status,contentType:'application/json',body:JSON.stringify(payload)})
 })
 await page.goto('/login')
 await page.getByLabel('Clinic code').fill(clinic.slug)
 await page.evaluate((r)=>{(window as unknown as {__gsiCredential?:string}).__gsiCredential='synthetic-id-token:'+r},role)
 await page.getByRole('button',{name:'Sign in with Google'}).click()
 await expect(page).toHaveURL(/\/workspace\//)
 await page.goto(`/workspace/patient-360?patient=${patientId}`)
 await expect(page.getByText('Synthetic Patient').first()).toBeVisible()
 return {events,getChartRequests:()=>chartRequests}
}

test('dentist selects permanent tooth and records immutable correction',async({page})=>{
 const {events}=await setup(page,'DENTIST')
 await page.getByRole('tab',{name:'Odontogram'}).click()
 await expect(page.getByRole('heading',{name:/Odontogram/})).toBeVisible()
 await page.getByLabel('Dentition').selectOption('mixed')
 await page.getByRole('button',{name:'11'}).click()
 await page.getByLabel('Surface').selectOption('I')
 await page.getByLabel('Finding').selectOption('CARIES')
 await page.getByRole('button',{name:'Record new finding'}).click()
 await expect(page.getByText('OBSERVATION: CARIES')).toBeVisible()
 await page.getByRole('button',{name:'Correct / retract'}).click()
 await page.getByLabel('Finding').selectOption('RESTORATION')
 await page.getByRole('button',{name:'Record correction'}).click()
 await expect(page.getByText('CORRECTION: RESTORATION')).toBeVisible()
 expect(events).toHaveLength(2)
 expect(events[1].supersedes_event_id).toBe(events[0].id)
 expect(events[1].tooth_number).toBe(11)
 expect(events[1].surface).toBe('I')
})

test('front desk is not permitted to load or edit protected odontogram data',async({page})=>{
 const {getChartRequests}=await setup(page,'FRONT_DESK')
 await page.getByRole('tab',{name:'Odontogram'}).click()
 await expect(page.getByRole('alert').getByText(/Clinical chart access is restricted/)).toBeVisible()
 expect(getChartRequests()).toBe(0)
 await expect(page.getByRole('button',{name:'Record new finding'})).toHaveCount(0)
})
