import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const fixture=JSON.parse(await readFile(new URL('../../.run/local-demo/role-fixtures.json',import.meta.url),'utf8'));
const base=process.env.SMARTTASK_ROLES_WEB||'http://127.0.0.1:5173';
const password=process.env.SMARTTASK_LOCAL_DEMO_PASSWORD;
assert.ok(password&&fixture.localOnly&&['localhost','127.0.0.1'].includes(new URL(base).hostname));
const dir=new URL('../../.run/role-interfaces/',import.meta.url);await mkdir(dir,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const results=[],errors=[];let nextRequest=0,queue=Promise.resolve(),page;
const expectations={admin:['/admin','Quản trị hệ thống'],manager:['/dashboard','Quản lý'],employee:['/tasks','Nhân viên'],user:['/my-tasks','Người dùng'],guest:['/projects','Khách chỉ đọc'],workspace_admin:['/dashboard','Quản lý']};
async function api(context,token,path){return context.request.get(base+'/api/v1'+path,{headers:{Authorization:'Bearer '+token}});}
try {
  for(const account of fixture.accounts) {
    const context=await browser.newContext({viewport:{width:1440,height:1000},serviceWorkers:'block'});context.setDefaultTimeout(30000);
    await context.route('**/api/v1/**',async route=>{
      const turn=queue.then(async()=>{await new Promise(r=>setTimeout(r,Math.max(0,nextRequest-Date.now())));nextRequest=Date.now()+800;});
      queue=turn.catch(()=>{});await turn;await route.continue();
    });
    page=await context.newPage();page.on('pageerror',e=>errors.push({account:account.username,message:e.message}));
    let token='';page.on('response',async response=>{if(/\/auth\/(web\/)?login$/.test(new URL(response.url()).pathname)&&response.ok())token=(await response.json()).data.access_token;});
    await page.goto(base+'/login');await page.locator('input[autocomplete="username"]').fill(account.username);
    await page.locator('input[type="password"]').fill(password);await page.locator('button[type="submit"]').click();
    const [home,label]=expectations[account.username];await page.waitForURL(u=>u.pathname===home);
    const nav=page.locator('nav.app-sidebar');await nav.getByLabel('Vai trò hiện tại').getByText(label,{exact:true}).waitFor();
    assert.equal(await nav.getByRole('link',{name:'Quản lý tài khoản',exact:true}).count(),account.username==='admin'?1:0);
    assert.equal(await nav.getByRole('link',{name:'Phân quyền dự án',exact:true}).count(),account.username==='manager'?1:0);
    if(account.username==='user')assert.equal(await nav.locator('a[href="/tasks"]').count(),0);
    if(account.username==='employee')assert.equal(await nav.getByRole('link',{name:'Việc được giao',exact:true}).count(),1);
    await page.waitForFunction(()=>!document.querySelector('.app-main .skeleton, .app-main .animate-spin'));
    if(account.username==='employee')await page.locator(`a[href="/tasks/${fixture.tasks.find(t=>t.assignee==='employee').id}"]`).first().waitFor();
    await page.screenshot({path:new URL(account.username+'.png',dir).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
    assert.ok(token);const access=await api(context,token,'/me/access');assert.equal(access.status(),200);
    const capability=(await access.json()).data;
    assert.equal(capability.systemAdmin,account.username==='admin');
    if(['admin','user','workspace_admin'].includes(account.username))assert.equal(capability.projects.length,0);
    if(['manager','employee','guest'].includes(account.username))assert.equal(capability.projects.length,1);
    const accounts=await api(context,token,'/accounts');assert.equal(accounts.status(),account.username==='admin'?200:403);
    const employeeTask=fixture.tasks.find(t=>t.assignee==='employee').id;
    const readEmployee=await api(context,token,'/tasks/'+employeeTask);assert.equal(readEmployee.status(),['manager','employee'].includes(account.username)?200:404);
    if(account.username!=='admin') {
      await page.goto(base+'/admin');await page.getByRole('alert').filter({hasText:'không có quyền quản trị hệ thống'}).waitFor();
    }
    if(account.username!=='manager') {
      await page.goto(base+'/roles');await page.getByRole('alert').filter({hasText:'Chỉ quản lý dự án'}).waitFor();
    }
    results.push({account:account.username,home,interface:label,status:'PASS'});console.log('PASS '+account.username+': home, navigation, API scope and direct URL guards');
    await context.close();
  }
  assert.equal(errors.length,0,JSON.stringify(errors));
} catch(error) {
  results.push({status:'FAIL',error:error.stack});process.exitCode=1;
  if(page&&!page.isClosed())await page.screenshot({path:new URL('failure.png',dir).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
} finally {
  await writeFile(new URL('results.json',dir),JSON.stringify({base,results,errors},null,2));await browser.close();
}
