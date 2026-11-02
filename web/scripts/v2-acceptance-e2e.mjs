import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const api='http://127.0.0.1:8181/api/v1',base='http://127.0.0.1:5174';
const username=`demo_v2_${Date.now().toString(36)}`,password=`Demo-${crypto.randomUUID()}!`;
let token;
async function request(path,method='GET',body,version){
  const r=await fetch(api+path,{method,headers:{'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID(),...(token?{Authorization:`Bearer ${token}`} :{}),...(version?{'If-Match':String(version)}:{})},...(body?{body:JSON.stringify(body)}:{})});
  assert.equal(r.status,200,`${method} ${path}: ${r.status}`);return (await r.json()).data;
}
await request('/auth/register','POST',{username,email:`${username}@example.test`,password,display_name:'DEMO V2 Acceptance'});
token=(await request('/auth/login','POST',{username,password})).access_token;
const workspace=await request('/workspaces','POST',{name:'DEMO V2 Workspace',type:'TEAM'});
const project=await request('/projects','POST',{workspace_id:workspace.id,name:'DEMO V2 Project'});
await request('/tasks','POST',{project_id:project.id,title:'DEMO Kanban — quy trình được kiểm tra'});
const personal=await request('/me/tasks','POST',{title:'DEMO V2 Calendar'});
await request(`/me/tasks/${personal.id}`,'PATCH',{title:'DEMO V2 Calendar',priority:'MEDIUM',dueAt:new Date().toISOString(),dueTimezone:'Asia/Bangkok',dueIsDateOnly:false},personal.version);
const browser=await chromium.launch({channel:'msedge',headless:true});
let page;
const unexpected=[];
await mkdir('../docs/screenshots/v2',{recursive:true});
try{
 const context=await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'Asia/Bangkok',extraHTTPHeaders:{'X-Forwarded-For':`10.233.${Date.now()%240}.1`}});page=await context.newPage();
 async function visit(path){await page.locator(`.app-sidebar a[href="${path}"]`).first().click();await page.waitForURL(base+path);}
 page.on('response',r=>{if(r.status()>=400){const path=new URL(r.url()).pathname;console.log('HTTP',r.status(),path);if(!(r.status()===403&&path.endsWith('/workflow/start')))unexpected.push(`${r.status()} ${path}`);}});
 page.on('request',r=>{const url=new URL(r.url());if(url.pathname.endsWith('/realtime/stream')){assert.deepEqual([...url.searchParams.keys()],['ticket']);assert.equal(r.headers().authorization,undefined);}});
 await page.goto(base+'/login');await page.locator('input[autocomplete="username"]').fill(username);await page.locator('input[type="password"]').fill(password);await page.locator('button[type="submit"]').click();await page.waitForURL(/\/(dashboard|tasks)$/);
 await visit('/dashboard');await page.getByText('Tổng công việc',{exact:true}).waitFor();assert.equal(await page.getByRole('alert').count(),0);await page.screenshot({path:'../docs/screenshots/v2/dashboard-desktop.png',fullPage:true});
 await visit('/teams');await page.locator('select').first().selectOption(workspace.id);await page.getByRole('button',{name:'Tải nhóm',exact:true}).click();await page.getByLabel('Tên nhóm',{exact:true}).fill('DEMO V2 Team');await page.getByRole('button',{name:'Tạo nhóm',exact:true}).click();await page.getByRole('button',{name:'DEMO V2 Team',exact:true}).click();await page.getByRole('heading',{name:'DEMO V2 Team',exact:true}).waitFor();await page.screenshot({path:'../docs/screenshots/v2/teams-desktop.png',fullPage:true});
 await visit('/tasks');await page.getByText('DEMO Kanban — quy trình được kiểm tra',{exact:true}).waitFor();await page.getByRole('button',{name:'Kanban',exact:true}).click();await page.getByLabel('Chuyển trạng thái DEMO Kanban — quy trình được kiểm tra').selectOption('IN_PROGRESS');await page.getByRole('alert').waitFor();assert.equal(await page.getByLabel('Chuyển trạng thái DEMO Kanban — quy trình được kiểm tra').inputValue(),'TODO');await page.screenshot({path:'../docs/screenshots/v2/kanban-desktop.png',fullPage:true});
 await visit('/calendar');await page.getByRole('link',{name:'DEMO V2 Calendar',exact:true}).waitFor();await page.screenshot({path:'../docs/screenshots/v2/calendar-desktop.png',fullPage:true});
 await visit('/notifications');await page.getByLabel(/HH:MM/).first().waitFor();const times=page.getByRole('textbox');await times.nth(0).fill('23:00');await times.nth(1).fill('06:30');await Promise.all([page.waitForResponse(r=>r.url().includes('/reminders/preferences')&&r.request().method()==='PUT'&&r.status()===200),page.getByRole('button',{name:'Lưu',exact:true}).click()]);await page.reload();await page.getByRole('textbox').nth(0).waitFor();await page.waitForFunction(()=>[...document.querySelectorAll('input')].some(i=>i.value==='23:00'));await page.screenshot({path:'../docs/screenshots/v2/notifications-desktop.png',fullPage:true});
 await visit('/roles');await page.locator('select').first().selectOption(project.id);await page.screenshot({path:'../docs/screenshots/v2/roles-desktop.png',fullPage:true});
 await page.getByText('Trực tuyến',{exact:true}).waitFor();await context.setOffline(true);await page.getByText('Mất kết nối realtime',{exact:true}).waitFor();await context.setOffline(false);await page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/realtime/ticket')&&r.status()===200);await page.getByText('Trực tuyến',{exact:true}).waitFor();
 await page.setViewportSize({width:390,height:844});for(const route of ['dashboard','teams','calendar','notifications']){await visit('/'+route);await page.locator('h1.page-title').waitFor();if(route==='dashboard')await page.getByText('Tổng công việc',{exact:true}).waitFor();if(route==='teams'){await page.locator('select').first().selectOption(workspace.id);await page.getByRole('button',{name:'Tải nhóm',exact:true}).click();await page.getByRole('button',{name:'DEMO V2 Team',exact:true}).waitFor();}if(route==='calendar')await page.getByRole('link',{name:'DEMO V2 Calendar',exact:true}).waitFor();if(route==='notifications')await page.waitForFunction(()=>[...document.querySelectorAll('input')].some(i=>i.value==='23:00'));assert.equal(await page.getByRole('alert').count(),0);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,`Horizontal overflow: ${route}`);await page.screenshot({path:`../docs/screenshots/v2/${route}-mobile.png`,fullPage:true});}
 assert.deepEqual(unexpected,[],'Unexpected HTTP failures in browser acceptance');
 console.log('PASS: dashboard, Team creation/picker, Kanban denied transition rollback, scoped calendar, persisted quiet-hours, roles screen, offline/reconnect with fresh ticket and no bearer in URL; desktop/mobile without horizontal overflow or unexpected HTTP errors. Demo fixtures retained.');
}catch(error){if(page){console.error('Page alerts:',await page.getByRole('alert').allTextContents());await page.screenshot({path:'../docs/screenshots/v2/acceptance-failure.png',fullPage:true});}throw error;}finally{await browser.close();}
