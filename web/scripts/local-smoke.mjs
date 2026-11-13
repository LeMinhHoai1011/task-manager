import assert from 'node:assert/strict';
import { chromium } from '../../.tools/browser/node_modules/playwright/index.mjs';
const base=process.env.SMOKE_BASE || 'http://127.0.0.1:5173';
const suffix=Date.now().toString(36);
const username=`local_smoke_${suffix}`;
const password=`Smoke-${crypto.randomUUID()}!`;
const passed=[];
function pass(name){passed.push(name);console.log(`PASS ${name}`);}
async function request(path,{token,method='GET',body,status=200,headers={}}={}){
  const response=await fetch(`${base}/api/v1${path}`,{method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:body?JSON.stringify(body):undefined});
  assert.equal(response.status,status,`${method} ${path}: ${response.status}`);
  if(status!==200)return;
  return (await response.json()).data;
}
await request('/health/liveness');pass('Vite API proxy');
await request('/workspaces',{status:401});pass('unauthenticated API rejected');
await request('/auth/register',{method:'POST',body:{username,email:`${username}@example.test`,password,display_name:'Local smoke test'}});
let login=await request('/auth/login',{method:'POST',body:{username,password,client_type:'WEB'}});
let token=login.access_token;assert.ok(token);pass('registration and login');
const workspace=await request('/workspaces',{token,method:'POST',body:{name:`LOCAL SMOKE ${suffix}`,type:'TEAM'}});
const project=await request('/projects',{token,method:'POST',body:{workspace_id:workspace.id,name:'LOCAL SMOKE Project'}});
const task=await request('/tasks',{token,method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:{project_id:project.id,title:'LOCAL SMOKE Task'}});
assert.ok(task.id);pass('workspace, project and task creation');
const settings=await request(`/projects/${project.id}/settings`,{token});
await request(`/projects/${project.id}/activate`,{token,method:'POST',headers:{'If-Match':String(settings.version)}});
const started=await request(`/tasks/${task.id}/workflow/start`,{token,method:'POST',headers:{'If-Match':String(task.version),'Idempotency-Key':crypto.randomUUID()}});
assert.equal(started.to_state,'IN_PROGRESS');pass('Kanban workflow start');
const bytes=new TextEncoder().encode('%PDF-1.4\nLOCAL SMOKE PDF BYTES\n%%EOF');
const upload=await request('/files/upload',{token,method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:{task_id:task.id,file_name:'local-smoke.pdf',mime_type:'application/pdf',declared_size:bytes.length}});
const form=new FormData();form.append('file',new Blob([bytes],{type:'application/pdf'}),'local-smoke.pdf');
const stage=await fetch(`${base}/api/v1/files/upload/${upload.upload_session_id}/content`,{method:'POST',headers:{Authorization:`Bearer ${token}`,'Idempotency-Key':crypto.randomUUID()},body:form});
assert.equal(stage.status,200,'stage local file');
await request(`/files/upload/${upload.upload_session_id}/complete`,{token,method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:{actual_bytes:bytes.length}});
const attachment=await request(`/files/upload/${upload.upload_session_id}/commit`,{token,method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:{}});
const download=await request(`/files/${attachment.attachment_id}/download`,{token});
const binary=await fetch(`${base}/api/v1/files/stream/${download.ticket}`);
assert.equal(binary.status,200);assert.deepEqual(new Uint8Array(await binary.arrayBuffer()),bytes);pass('local file upload, commit and byte-exact download');
const personal=await request('/me/tasks',{token,method:'POST',headers:{'Idempotency-Key':crypto.randomUUID()},body:{title:'LOCAL SMOKE Personal'}});
assert.ok(personal.id);pass('personal task creation');
const note=await request('/notes',{token,method:'POST',body:{scope:'PERSONAL',title:'LOCAL SMOKE Note',content:'Local verification'}});
assert.ok(note.id);pass('note creation');
for(const path of ['/calendar','/dashboard','/reports/dashboard','/notifications','/me/tasks','/notes']){await request(path,{token});pass(`read ${path}`);}
const ticket=await request('/realtime/ticket',{token,method:'POST',body:{}});
assert.ok(ticket.ticket);
const stream=await fetch(`${base}/api/v1/realtime/stream?ticket=${ticket.ticket}`);
assert.equal(stream.status,200);await stream.body.cancel();
await request(`/realtime/stream?ticket=${ticket.ticket}`,{status:401});pass('SSE connection and single-use ticket');
await request('/auth/register',{method:'POST',body:{username:`${username}_other`,email:`${username}_other@example.test`,password,display_name:'Other local tester'}});
const other=await request('/auth/login',{method:'POST',body:{username:`${username}_other`,password,client_type:'WEB'}});
await request(`/tasks/${task.id}`,{token:other.access_token,status:404});pass('cross-user task access denied');
await request('/auth/logout',{token,method:'POST'});
await request('/workspaces',{token,status:401});pass('logout revokes session');
const browser=await chromium.launch({channel:'msedge',headless:true});
try{
  const page=await browser.newPage();
  page.on('response',response=>{if(response.status()>=400)console.log('BROWSER HTTP',response.status(),new URL(response.url()).pathname);});
  await page.goto(`${base}/login`);
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[type="password"]').fill(password);
  await page.locator('button[type="submit"]').click();
  try{await page.waitForURL(/\/(dashboard|tasks)$/, {timeout:30000});}catch(error){console.log('BROWSER PAGE',await page.locator('body').innerText());throw error;}pass('browser login and dashboard');
  for(const route of ['/my-tasks','/projects','/notes','/calendar','/reports','/notifications']){
    await page.goto(`${base}${route}`);await page.locator('main').waitFor();assert.ok(!page.url().endsWith('/login'));pass(`browser route ${route}`);
  }
  await page.screenshot({path:'../.run/local-smoke-browser.png',fullPage:true});
}finally{await browser.close();}
console.log(`LOCAL SMOKE PASS: ${passed.length} checks; test users/data retained: ${username}`);
