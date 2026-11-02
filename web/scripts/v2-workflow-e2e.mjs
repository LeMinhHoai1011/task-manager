import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const api='http://127.0.0.1:8181/api/v1',base='http://127.0.0.1:5174',suffix=Date.now().toString(36);
const runIp=`10.231.${Math.floor(Math.random()*240)+1}`;
async function call(path,method='GET',body,token,version){
 const response=await fetch(api+path,{method,headers:{'Content-Type':'application/json','X-Forwarded-For':runIp+'.10','Idempotency-Key':crypto.randomUUID(),...(token?{Authorization:`Bearer ${token}`} :{}),...(version!=null?{'If-Match':String(version)}:{})},...(body?{body:JSON.stringify(body)}:{})});
 assert.equal(response.status,200,`${method} ${path}: ${response.status}`);return (await response.json()).data;
}
async function user(name){const username=`demo_${name}_${suffix}`,password=`Demo-${crypto.randomUUID()}!`,email=username+'@example.test';const registered=await call('/auth/register','POST',{username,password,email,display_name:`DEMO ${name}`});const login=await call('/auth/login','POST',{username,password});return {username,password,email,id:registered.user_id,token:login.access_token};}
const owner=await user('Manager'),member=await user('Member');
const workspace=await call('/workspaces','POST',{name:'DEMO Workflow Workspace',type:'TEAM'},owner.token);
const invitation=await call(`/workspaces/${workspace.id}/invites`,'POST',{email:member.email,role:'MEMBER'},owner.token);
await call(`/me/invitations/${invitation.invitation_id}/accept`,'POST',{},member.token);
const team=await call(`/workspaces/${workspace.id}/teams`,'POST',{name:'DEMO Workflow Team'},owner.token);
await call(`/teams/${team.id}/members`,'POST',{userId:member.id,role:'MEMBER'},owner.token,1);
const project=await call('/projects','POST',{workspace_id:workspace.id,name:'DEMO Workflow Project'},owner.token);
await call(`/projects/${project.id}/members`,'POST',{user_id:member.id,role:'MEMBER'},owner.token);
await call(`/projects/${project.id}/activate`,'POST',{},owner.token,1);
let settings=await call(`/projects/${project.id}/settings`,'GET',undefined,owner.token);
await call(`/projects/${project.id}/approval`,'PATCH',{enabled:true,version:settings.version},owner.token);
const browser=await chromium.launch({channel:'msedge',headless:true});
await mkdir('../docs/screenshots/v2',{recursive:true});
let ownerPage,memberPage;
let phase=20;
async function login(page,account){await page.goto(base+'/login');await page.locator('input[autocomplete="username"]').fill(account.username);await page.locator('input[type="password"]').fill(account.password);await page.locator('button[type="submit"]').click();await page.waitForURL(/\/(dashboard|tasks)$/);account.token=await page.evaluate(()=>localStorage.getItem('tm.access_token'));}
// Functional scenarios use isolated test IP buckets, as ItgBase does. Production rate limits stay enabled.
async function visit(page,path){await page.context().setExtraHTTPHeaders({'X-Forwarded-For':runIp+'.'+(++phase)});await page.locator(`.app-sidebar a[href="${path}"]`).first().click();await page.waitForURL(base+path);}
async function transition(page,id,verb){
 await page.context().setExtraHTTPHeaders({'X-Forwarded-For':runIp+'.'+(++phase)});
 const refresh=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===`/api/v1/tasks/${id}`);
 const [response]=await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname===`/api/v1/tasks/${id}/workflow/${verb}`),page.getByRole('button',{name:verb,exact:true}).click()]);
 assert.equal(response.status(),200,`Workflow ${verb}`);const result=(await response.json()).data;await refresh;await page.locator('h1.page-title').waitFor();return result;
}
try{
 const ownerContext=await browser.newContext({viewport:{width:1440,height:1100},timezoneId:'Asia/Bangkok',extraHTTPHeaders:{'X-Forwarded-For':runIp+'.1'}});
 const memberContext=await browser.newContext({viewport:{width:1440,height:1100},timezoneId:'Asia/Bangkok',extraHTTPHeaders:{'X-Forwarded-For':runIp+'.2'}});
 ownerPage=await ownerContext.newPage();memberPage=await memberContext.newPage();
 await login(ownerPage,owner);
 await visit(ownerPage,'/teams');await ownerPage.locator('select').first().selectOption(workspace.id);await ownerPage.getByRole('button',{name:'Tải nhóm',exact:true}).click();await ownerPage.getByRole('button',{name:'DEMO Workflow Team',exact:true}).click();await ownerPage.getByText('DEMO Member — MEMBER',{exact:false}).waitFor();await ownerPage.screenshot({path:'../docs/screenshots/v2/teams-members.png',fullPage:true});
 await visit(ownerPage,'/roles');await ownerPage.locator('select').first().selectOption(project.id);const [existingRole]=await Promise.all([ownerPage.waitForResponse(r=>r.request().method()==='GET'&&r.url().endsWith(`/members/${member.id}/custom-role`)),ownerPage.locator('select').nth(1).selectOption(member.id)]);assert.equal(existingRole.status(),200);await ownerPage.getByLabel('Tên vai trò',{exact:true}).fill('DEMO Coordinator');await ownerPage.getByLabel('Sửa thông tin công việc',{exact:true}).check();await ownerPage.getByLabel('Giao và chuyển giao công việc',{exact:true}).check();await ownerPage.getByRole('button',{name:'Lưu vai trò',exact:true}).click();await ownerPage.getByText('Đã lưu vai trò tùy chỉnh',{exact:true}).waitFor();await ownerPage.screenshot({path:'../docs/screenshots/v2/roles-assigned.png',fullPage:true});await ownerPage.getByRole('button',{name:'Thu hồi vai trò',exact:true}).click();await ownerPage.getByText('Đã thu hồi vai trò tùy chỉnh',{exact:true}).waitFor();
 await visit(ownerPage,'/tasks');
 await ownerPage.getByRole('button',{name:'Tạo công việc dự án',exact:true}).click();await ownerPage.getByLabel('Dự án giao việc').selectOption(project.id);
 const title='DEMO V2 — member approval workflow';await ownerPage.getByLabel('Tiêu đề công việc dự án',{exact:true}).fill(title);await ownerPage.getByLabel('Mô tả công việc',{exact:true}).fill('Dữ liệu DEMO cho nghiệm thu hai tài khoản.');
 const due=new Date(Date.now()+86400000).toISOString().slice(0,16);await ownerPage.getByLabel('Hạn hoàn thành',{exact:true}).fill(due);
 await ownerPage.screenshot({path:'../docs/screenshots/v2/project-task-create.png',fullPage:true});
 const [creation]=await Promise.all([ownerPage.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/tasks'),ownerPage.getByRole('button',{name:'Tạo công việc',exact:true}).click()]);assert.equal(creation.status(),200);const task=(await creation.json()).data;
 await ownerPage.getByRole('link',{name:'Mở chi tiết',exact:true}).click();await ownerPage.locator('h1.page-title').waitFor();
 await ownerPage.getByRole('button',{name:'Sửa thông tin / deadline',exact:true}).click();await ownerPage.locator('section').filter({has:ownerPage.getByRole('button',{name:'Lưu nội dung',exact:true})}).last().locator('textarea').fill('Updated DEMO description');await ownerPage.screenshot({path:'../docs/screenshots/v2/project-task-edit.png',fullPage:true});const [edit]=await Promise.all([ownerPage.waitForResponse(r=>r.request().method()==='PATCH'&&new URL(r.url()).pathname===`/api/v1/tasks/${task.id}`),ownerPage.getByRole('button',{name:'Lưu nội dung',exact:true}).click()]);assert.equal(edit.status(),200);assert.equal((await edit.json()).data.description,'Updated DEMO description');await ownerPage.getByText('Updated DEMO description',{exact:true}).first().waitFor();
 await ownerPage.getByLabel('Nội dung mục',{exact:true}).fill('DEMO acceptance checklist');await ownerPage.getByRole('button',{name:'Thêm mục checklist',exact:true}).click();await ownerPage.getByLabel('DEMO acceptance checklist',{exact:true}).waitFor();
 await ownerPage.screenshot({path:'../docs/screenshots/v2/task-detail-checklist.png',fullPage:true});
 await ownerPage.getByLabel('Người phụ trách mới').selectOption(member.id);
 const [assignment]=await Promise.all([ownerPage.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/primary-assignee')),ownerPage.getByRole('button',{name:'Chuyển giao',exact:true}).click()]);assert.equal(assignment.status(),200);
 await ownerPage.locator('section.card').filter({has:ownerPage.getByRole('heading',{name:'Người chịu trách nhiệm chính',exact:true})}).locator('p').first().filter({hasText:'DEMO Member'}).waitFor();
 await ownerPage.screenshot({path:'../docs/screenshots/v2/assignment-desktop.png',fullPage:true});
 const assigned=await call(`/tasks/${task.id}`,'GET',undefined,owner.token);await call(`/tasks/${task.id}/team`,'PUT',{teamId:team.id},owner.token,assigned.version);
 let received=false;for(let i=0;i<30;i++){const inbox=await call('/notifications?filter=task','GET',undefined,member.token);if(inbox.items.some(n=>n.kind==='TASK_REASSIGNED')){received=true;break;}await new Promise(resolve=>setTimeout(resolve,500));}assert.equal(received,true,'Assignment notification persisted through worker');
 await login(memberPage,member);await visit(memberPage,'/notifications');await memberPage.getByText('Người phụ trách công việc đã thay đổi',{exact:true}).waitFor();await memberPage.screenshot({path:'../docs/screenshots/v2/member-assignment-notification.png',fullPage:true});
 await visit(memberPage,'/tasks');await memberPage.getByRole('link',{name:title,exact:true}).click();const [tick]=await Promise.all([memberPage.waitForResponse(r=>r.request().method()==='PATCH'&&r.url().includes('/checklist/')),memberPage.getByLabel('DEMO acceptance checklist',{exact:true}).check()]);assert.equal(tick.status(),200);await memberPage.waitForFunction(()=>document.querySelector('input[aria-label="DEMO acceptance checklist"]')?.checked===true);await memberPage.getByRole('button',{name:'start',exact:true}).waitFor();
 assert.equal((await transition(memberPage,task.id,'start')).to_state,'IN_PROGRESS');assert.equal((await transition(memberPage,task.id,'submit')).to_state,'PENDING_APPROVAL');
 await memberPage.screenshot({path:'../docs/screenshots/v2/workflow-pending-approval.png',fullPage:true});
 await ownerPage.getByText('PENDING_APPROVAL',{exact:true}).first().waitFor();assert.equal((await transition(ownerPage,task.id,'approve')).to_state,'COMPLETED');await ownerPage.screenshot({path:'../docs/screenshots/v2/workflow-approved.png',fullPage:true});
 const dashboard=await call(`/dashboard?projectId=${project.id}`,'GET',undefined,owner.token);assert.equal(dashboard.completed,1);
 await visit(ownerPage,'/dashboard');await ownerPage.getByText('Tổng công việc',{exact:true}).waitFor();await ownerPage.screenshot({path:'../docs/screenshots/v2/dashboard-after-approval.png',fullPage:true});
 await visit(ownerPage,'/tasks');await ownerPage.getByRole('link',{name:title,exact:true}).waitFor();await ownerPage.getByRole('button',{name:'Kanban',exact:true}).click();await ownerPage.screenshot({path:'../docs/screenshots/v2/kanban-after-approval.png',fullPage:true});
 await visit(ownerPage,'/calendar');await ownerPage.getByRole('button',{name:'month',exact:true}).click();await ownerPage.getByLabel('Ngày xem',{exact:true}).fill(due.slice(0,10));await ownerPage.getByRole('link',{name:title,exact:true}).waitFor();await ownerPage.screenshot({path:'../docs/screenshots/v2/calendar-after-approval.png',fullPage:true});
 await memberPage.getByRole('button',{name:'Đăng xuất',exact:true}).click();await memberPage.waitForURL(/\/login$/);await login(memberPage,member);await visit(memberPage,'/tasks');await memberPage.getByRole('link',{name:title,exact:true}).click();await memberPage.getByText('COMPLETED',{exact:true}).first().waitFor();assert.equal((await call(`/tasks/${task.id}`,'GET',undefined,member.token)).state,'COMPLETED');
 console.log('PASS: registered accounts; Workspace invitation/acceptance, Team membership, Project activate/approval; task created in UI with deadline; assignment in UI; worker persisted member notification; member checklist/start/submit; manager approval; Dashboard/Kanban/Calendar update; logout/relogin persistence. DEMO fixtures retained.');
}catch(error){for(const [name,page]of [['owner',ownerPage],['member',memberPage]])if(page){console.error(name,'alerts:',await page.getByRole('alert').allTextContents());await page.screenshot({path:`../docs/screenshots/v2/workflow-failure-${name}.png`,fullPage:true});}throw error;}finally{await browser.close();}
