import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';

const fixtures=JSON.parse(await readFile(new URL('../../.run/local-demo/fixtures.json',import.meta.url),'utf8'));
const base=(process.env.SMARTTASK_DEMO_WEB_URL||'http://127.0.0.1:5173').replace(/\/$/,'');
assert.ok(fixtures.localOnly&&['127.0.0.1','localhost'].includes(new URL(base).hostname),'Local demo only');
const evidence=fileURLToPath(new URL('../../.run/local-demo/',import.meta.url));
const screenshots=fileURLToPath(new URL('../../docs/screenshots/local-demo/',import.meta.url));
await mkdir(screenshots,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const results=[],errors=[],failures=[],expected=new Set();
const stamp=Date.now().toString(36);
const parent=fixtures.tasks.find(t=>t.size==='LARGE');
const hidden=fixtures.tasks[3];
const readable=fixtures.tasks[0];
const created={};
let page;

async function api(path,method='GET',body,expectedStatus=200,version){
  if(expectedStatus>=400)expected.add(`${method} ${expectedStatus} ${path}`);
  const result=await page.evaluate(async({path,method,body,version})=>{
    const headers={Authorization:'Bearer '+localStorage.getItem('tm.access_token'),'Idempotency-Key':crypto.randomUUID(),'Content-Type':'application/json'};
    if(version!==undefined)headers['If-Match']=String(version);
    const res=await fetch('/api/v1'+path,{method,headers,...(body!==undefined?{body:JSON.stringify(body)}:{})});
    return {status:res.status,body:await res.json()};
  },{path,method,body,version});
  assert.equal(result.status,expectedStatus,`${method} ${path}: ${result.status}`);
  return result.body.data;
}
function pass(test,extra={}){results.push({test,status:'PASS',...extra});console.log('PASS '+test);}

try {
 for(const account of fixtures.accounts){
  const context=await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'Asia/Bangkok'});
  // Pace real requests through the unchanged shared-IP limiter. No response mocking.
  let dispatch=Promise.resolve(),lastDispatch=0;
  await context.route('**/api/v1/**',async route=>{
    const turn=dispatch.then(async()=>{
      await new Promise(resolve=>setTimeout(resolve,Math.max(0,lastDispatch+800-Date.now())));
      lastDispatch=Date.now();
    });
    dispatch=turn.catch(()=>{});await turn;await route.continue();
  });
  context.setDefaultTimeout(60000);
  page=await context.newPage();
  page.on('pageerror',error=>errors.push({account:account.username,message:error.message}));
  page.on('response',res=>{const url=new URL(res.url());if(url.pathname.startsWith('/api/v1/')&&res.status()>=400)failures.push(`${res.request().method()} ${res.status()} ${url.pathname.slice('/api/v1'.length)}`);});
  await page.goto(base+'/login');
  await page.locator('input[autocomplete="username"]').fill(account.username);
  await page.locator('input[type="password"]').fill(account.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/(dashboard|tasks)$/);
  await page.waitForTimeout(6500);
  pass(account.username+': UI login');

  await page.goto(base+`/projects/${fixtures.project.id}?tab=tasks`);
  await page.getByRole('heading',{level:1}).filter({hasText:fixtures.project.name}).waitFor();
  await page.getByText(readable.title,{exact:true}).waitFor();
  await page.waitForTimeout(6500);
  const membership=await api(`/projects/${fixtures.project.id}/members`);
  assert.equal(membership.find(m=>m.user_id===account.userId)?.role,account.projectRole);
  pass(account.username+': đúng vai trò và dữ liệu Project');
  const access=await api('/me/access');
  assert.equal(access.systemAdmin,false);
  assert.equal(access.workspaces.find(w=>w.id===fixtures.workspace.id)?.role,account.workspaceRole);
  const manager=['OWNER','ADMIN'].includes(account.workspaceRole);
  assert.equal(access.projects.find(p=>p.id===fixtures.project.id)?.canManage,manager);
  assert.equal(access.workspaces.find(w=>w.id===fixtures.workspace.id)?.canCreateProject,manager);
  await page.getByRole('note').filter({hasText:'Vai trò dự án:'}).waitFor();
  assert.equal(await page.getByRole('button',{name:'7. Cài đặt',exact:true}).count(),manager?1:0);
  pass(account.username+': quyền backend khớp tab và vai trò hiển thị');

  if(['OWNER','ADMIN'].includes(account.workspaceRole)){
    await page.getByRole('button',{name:'Tạo công việc dự án',exact:true}).waitFor();
  }else{
    assert.equal(await page.getByRole('button',{name:'Tạo công việc dự án',exact:true}).count(),0);
    assert.equal(await page.getByText(hidden.title,{exact:true}).count(),0);
    await api(`/tasks/${hidden.id}`,'GET',undefined,404);
    pass(account.username+': chỉ thấy Task được giao; Task khác trả 404');
  }
  await page.screenshot({path:screenshots+account.username+'-project.png',fullPage:true});

  if(account.workspaceRole==='OWNER'){
    await page.getByRole('button',{name:'Tạo công việc dự án',exact:true}).click();
    assert.equal(await page.getByLabel('Dự án giao việc',{exact:true}).count(),0);
    await page.getByRole('note').filter({hasText:fixtures.project.name}).waitFor();
    const title='[UI DEMO] Task tạo ngay trong Project '+stamp;
    await page.getByLabel('Tiêu đề công việc dự án',{exact:true}).fill(title);
    const response=page.waitForResponse(res=>new URL(res.url()).pathname==='/api/v1/tasks'&&res.request().method()==='POST');
    await page.getByRole('button',{name:'Tạo công việc',exact:true}).click();
    const saved=await response;
    assert.equal(saved.status(),200);
    assert.equal(saved.request().postDataJSON().project_id,fixtures.project.id);
    created.projectTask=(await saved.json()).data.id;
    await page.getByText(title,{exact:true}).waitFor();
    await page.waitForTimeout(6500);
    pass('Owner: tạo Task tại Project không chọn lại Project');

    await page.getByTitle('Tạo nhanh',{exact:true}).click();
    await page.getByRole('link',{name:'+ Việc dự án mới',exact:true}).click();
    await page.waitForURL(url=>url.pathname==='/tasks'&&url.searchParams.get('project_id')===fixtures.project.id&&url.searchParams.get('create')==='1');
    await page.getByLabel('Tiêu đề công việc dự án',{exact:true}).waitFor();
    assert.equal(await page.getByLabel('Dự án giao việc',{exact:true}).count(),0);
    pass('Owner: Tạo nhanh giữ đúng Project đang mở');
    await page.waitForTimeout(6500);

    await page.goto(base+`/tasks/${parent.id}`);
    await page.getByRole('textbox',{name:'Tên việc con',exact:true}).waitFor();
    await page.waitForTimeout(6500);
    assert.equal(await page.getByLabel('Dự án giao việc',{exact:true}).count(),0);
    const childTitle='[UI DEMO] Subtask tự nhận Project '+stamp;
    await page.getByRole('textbox',{name:'Tên việc con',exact:true}).fill(childTitle);
    const childResponse=page.waitForResponse(res=>new URL(res.url()).pathname===`/api/v1/tasks/${parent.id}/subtasks`&&res.request().method()==='POST');
    await page.getByRole('button',{name:'Thêm việc con',exact:true}).click();
    const childSaved=await childResponse;
    assert.equal(childSaved.status(),200);
    assert.deepEqual(childSaved.request().postDataJSON(),{title:childTitle});
    const child=(await childSaved.json()).data;
    assert.equal(child.project_id,fixtures.project.id);
    created.subtask=child.id;
    await page.getByRole('link',{name:childTitle,exact:true}).waitFor();
    await page.waitForTimeout(6500);
    await page.reload();
    await page.getByRole('link',{name:childTitle,exact:true}).waitFor();
    await page.screenshot({path:screenshots+'subtask-inherited-project.png',fullPage:true});
    pass('Owner: subtask tự kế thừa Project và còn sau refresh');
  }



  if(account.workspaceRole==='OWNER'){
    await page.goto(base+`/notes?project_id=${fixtures.project.id}`);
    await page.getByLabel('Nơi tạo ghi chú',{exact:true}).waitFor();
    await page.waitForTimeout(6500);
    await page.getByLabel('Nơi tạo ghi chú',{exact:true}).locator(`option[value="${fixtures.project.id}"]`).waitFor({state:'attached'});
    await page.waitForFunction(id=>document.querySelector('select[aria-label="Nơi tạo ghi chú"]')?.value===id,fixtures.project.id);
    assert.equal(await page.getByLabel('Nơi tạo ghi chú',{exact:true}).inputValue(),fixtures.project.id);
    const saved=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/notes'&&r.request().method()==='POST');
    await page.getByRole('button',{name:'Tạo ghi chú mới',exact:true}).click();
    const response=await saved;assert.equal(response.status(),200);
    const note=(await response.json()).data;assert.equal(note.scope,'PROJECT');assert.equal(note.projectId,fixtures.project.id);created.projectNote=note.id;
    const team=await api(`/workspaces/${fixtures.workspace.id}/teams`,'POST',{name:'[UI DEMO] Nhóm kiểm thử '+stamp});created.team=team.id;created.teamName=team.name;
  }
  const notesAccess=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/me/access');
  await page.goto(base+`/notes?id=${created.projectNote}`);
  await notesAccess;
  const noteTitle=page.getByPlaceholder('Tiêu đề ghi chú...');
  await noteTitle.waitFor();await page.waitForTimeout(6500);
  await page.waitForFunction(readOnly=>document.querySelector('input[placeholder="Tiêu đề ghi chú..."]')?.readOnly===readOnly,!['OWNER','ADMIN'].includes(account.workspaceRole));
  assert.equal(await noteTitle.evaluate(el=>el.readOnly),!['OWNER','ADMIN'].includes(account.workspaceRole));
  await page.screenshot({path:screenshots+account.username+'-notes-permission.png',fullPage:true});
  pass(account.username+': ghi chú Project chỉ cho quản lý sửa');
  await page.goto(base+'/teams');
  await page.locator('form').first().locator('select').selectOption(fixtures.workspace.id);
  await page.getByRole('button',{name:'Tải nhóm',exact:true}).click();
  await page.getByRole('button',{name:created.teamName,exact:true}).waitFor();
  await page.waitForTimeout(6500);
  assert.equal(await page.getByRole('button',{name:'Tạo nhóm',exact:true}).count(),['OWNER','ADMIN'].includes(account.workspaceRole)?1:0);
  await page.getByRole('button',{name:created.teamName,exact:true}).click();
  await page.getByRole('heading',{name:created.teamName,exact:true}).waitFor();
  const groupAccess=await api(`/teams/${created.team}/access`);
  assert.equal(groupAccess.canManage,['OWNER','ADMIN'].includes(account.workspaceRole));
  await page.waitForTimeout(6500);
  assert.equal(await page.getByRole('button',{name:'Thêm / đổi vai trò',exact:true}).count(),groupAccess.canManage?1:0);
  await page.screenshot({path:screenshots+account.username+'-teams-permission.png',fullPage:true});
  pass(account.username+': quyền tạo nhóm và sửa thành viên khớp backend');
  const taskAccess=await api(`/tasks/${readable.id}/access`);
  assert.equal(taskAccess.metadata,['OWNER','ADMIN'].includes(account.workspaceRole));
  assert.equal(taskAccess.assignment,['OWNER','ADMIN'].includes(account.workspaceRole));
  if(!['OWNER','ADMIN'].includes(account.workspaceRole)){
    await page.goto(base+`/tasks/${readable.id}`);
    await page.getByRole('note').filter({hasText:'Vai trò trong dự án:'}).waitFor();
    await page.waitForTimeout(6500);
    assert.equal(await page.getByRole('textbox',{name:'Tên việc con',exact:true}).count(),0);
    assert.equal(await page.getByRole('button',{name:'Chuyển giao người phụ trách',exact:true}).count(),0);
    assert.equal(await page.getByRole('button',{name:'Hủy công việc',exact:true}).count(),0);
    await api('/projects','POST',{workspace_id:fixtures.workspace.id,name:'Không được tạo'},403);
    if(account.workspaceRole==='GUEST'){
      assert.equal(taskAccess.execute,false);assert.deepEqual(taskAccess.workflowActions,[]);
      assert.equal(await page.getByRole('button',{name:'Nộp hoàn thành',exact:true}).count(),0);
    }
    await page.goto(base+`/projects/${fixtures.project.id}?tab=settings`);
    await page.getByRole('alert').filter({hasText:'Chỉ quản lý dự án'}).waitFor();
    await page.waitForTimeout(6500);
    pass(account.username+': ẩn thao tác quản lý, chặn URL cài đặt và API tạo Project');
  }
  await page.goto(base+'/admin');
  await page.getByRole('alert').filter({hasText:'không có quyền quản trị hệ thống'}).waitFor();
  await page.goto(base+'/my-role');
  await page.getByRole('heading',{name:'Vai trò và việc cần làm',exact:true}).waitFor();
  await page.getByRole('link',{name:fixtures.project.name,exact:true}).waitFor();
  await page.screenshot({path:screenshots+account.username+'-role-guide.png',fullPage:true});
  await page.waitForTimeout(6500);
  pass(account.username+': trang trách nhiệm đúng phạm vi, không có quyền hệ thống');
  if(account.workspaceRole==='MEMBER'){
    const task=await api(`/tasks/${readable.id}`);
    await api(`/tasks/${readable.id}`,'PATCH',{title:readable.title},403,task.version);
    pass('Member: không được sửa metadata Task dù được giao');
    await page.goto(base+`/tasks/${readable.id}`);
    if(task.state==='TODO'){
      const started=page.waitForResponse(res=>res.url().endsWith(`/tasks/${readable.id}/workflow/start`)&&res.request().method()==='POST');
      await page.getByRole('button',{name:'Bắt đầu',exact:true}).click();
      assert.equal((await started).status(),200);
      await page.getByRole('button',{name:'Nộp hoàn thành',exact:true}).waitFor();
      assert.equal((await api(`/tasks/${readable.id}`)).state,'IN_PROGRESS');
      pass('Member: bắt đầu Task được giao trên giao diện');
    }else{pass('Member: Task đã bắt đầu được giữ nguyên',{state:task.state});}
  }
  if(account.workspaceRole==='GUEST'){
    const task=await api(`/tasks/${readable.id}`);
    await api(`/tasks/${readable.id}/subtasks`,'POST',{title:'Không được tạo'},403);
    await api(`/tasks/${readable.id}/workflow/start`,'POST',{},403,task.version);
    pass('Guest: đọc Task được giao, không tạo subtask/đổi trạng thái');
  }

  await page.goto(base+'/my-tasks');
  await page.locator('.personal-task-list').getByText(fixtures.personalTasks[account.username][0].title,{exact:true}).waitFor();
  await page.waitForTimeout(6500);
  const own=await api('/me/tasks?size=100');
  const rows=own.items||own.content||own;
  assert.ok(Array.isArray(rows));
  assert.equal(rows.filter(t=>t.title.startsWith('[DEMO cá nhân]')).length,4);
  const other=fixtures.accounts.find(a=>a.username!==account.username);
  await api(`/me/tasks/${fixtures.personalTasks[other.username][0].id}`,'GET',undefined,404);
  pass(account.username+': 4 việc cá nhân riêng, không đọc được việc người khác');
  await page.screenshot({path:screenshots+account.username+'-personal.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.waitForTimeout(300);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  pass(account.username+': mobile không tràn ngang');
  await page.screenshot({path:screenshots+account.username+'-mobile.png',fullPage:true});
  // Keep the real shared-IP limiter intact; pace successive account sessions.
  await page.waitForTimeout(25000);
  await context.close();
 }
 assert.deepEqual(errors,[],'Runtime JavaScript errors');
 assert.deepEqual(failures.filter(f=>!expected.has(f)),[],'Unexpected HTTP failures');
}catch(error){
 results.push({test:'Local four-role acceptance',status:'FAIL',error:error.stack});
 if(page&&!page.isClosed())await page.screenshot({path:screenshots+'failure.png',fullPage:true});
 process.exitCode=1;
}finally{
 const output={date:new Date().toISOString(),web:base,browser:browser.version(),results,errors,httpFailures:failures,expectedDenials:[...expected],created};
 await writeFile(evidence+'ui-results.json',JSON.stringify(output,null,2));
 console.log(JSON.stringify(output,null,2));
 await browser.close();
}
