import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import assert from 'node:assert/strict';
const fixture=JSON.parse(await readFile(new URL('../../.run/local-demo/fixtures.json',import.meta.url),'utf8'));
const base=process.env.SMARTTASK_READINESS_WEB||'http://127.0.0.1:5175';
assert.ok(['localhost','127.0.0.1'].includes(new URL(base).hostname)&&fixture.localOnly);
const dir=new URL('../../.run/readiness-head-20261113/browser/',import.meta.url);await mkdir(dir,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const results=[],errors=[],http=[];let page;
const pass=test=>{results.push({test,status:'PASS'});console.log('PASS '+test);};
try{
 for(const account of fixture.accounts){
  const context=await browser.newContext({viewport:{width:1440,height:1000}});context.setDefaultTimeout(60000);
  let queue=Promise.resolve(),dispatch=0,access='';
  await context.route('**/api/v1/**',async route=>{const turn=queue.then(async()=>{await new Promise(r=>setTimeout(r,Math.max(0,dispatch+800-Date.now())));dispatch=Date.now();});queue=turn.catch(()=>{});await turn;await route.continue();});
  page=await context.newPage();page.on('pageerror',e=>errors.push({account:account.username,message:e.message}));
  page.on('response',async res=>{const path=new URL(res.url()).pathname;
    if(path.startsWith('/api/v1/')&&res.status()>=400)http.push({account:account.username,path,status:res.status()});
    if(['/api/v1/auth/web/login','/api/v1/auth/web/refresh'].includes(path)&&res.ok()){
      const body=await res.json();assert.equal(body.data.refresh_token,undefined);access=body.data.access_token;
    }
  });
  await page.goto(base+'/login');await page.locator('input[autocomplete="username"]').fill(account.username);await page.locator('input[type="password"]').fill(account.password);
  const logged=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/auth/web/login'&&r.ok());await page.locator('button[type="submit"]').click();await logged;
  await page.waitForURL(/dashboard|tasks/);await page.locator('nav.app-sidebar').waitFor();await page.waitForTimeout(2000);
  assert.ok(access);const storage=await page.evaluate(()=>({...localStorage}));assert.equal(storage['tm.access_token'],undefined);assert.equal(storage['tm.refresh_token'],undefined);
  const cookie=(await context.cookies()).find(c=>c.name==='stm_refresh');assert.ok(cookie?.httpOnly);assert.equal(cookie.sameSite,'Strict');
  pass(account.username+': HttpOnly refresh, access only in memory');
  const oldAccess=access;const rotated=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/auth/web/refresh'&&r.ok());await page.reload();await rotated;await page.locator('nav.app-sidebar').waitFor();await page.waitForTimeout(1000);assert.notEqual(access,oldAccess);
  pass(account.username+': reload restores and rotates session');
  async function visit(path){await page.locator(`nav.app-sidebar a.nav-link[href="/${path}"]`).click();await page.waitForURL(u=>u.pathname==='/'+path);await page.locator('h1').first().waitFor();await page.waitForTimeout(3500);await page.screenshot({path:new URL(account.username+'-'+path+'.png',dir).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});}
  await visit('dashboard');await page.getByRole('button',{name:'Tạo việc nhanh',exact:true}).click();
  const manager=['OWNER','ADMIN'].includes(account.workspaceRole);
  assert.equal(await page.getByRole('dialog').getByRole('button',{name:'Việc dự án',exact:true}).count(),manager?1:0);
  if(account.workspaceRole==='OWNER'){
    await page.getByRole('dialog').getByRole('button',{name:'Việc dự án',exact:true}).click();await page.getByRole('dialog').locator('select').selectOption(fixture.project.id);
    await page.getByPlaceholder('Bạn cần làm gì?').fill('[READINESS HEAD] Dashboard quick create');
    const saved=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/tasks'&&r.request().method()==='POST');await page.getByRole('button',{name:'Tạo công việc',exact:true}).click();const res=await saved;assert.equal(res.status(),200);assert.ok(res.request().headers()['idempotency-key']);
  }else await page.getByRole('dialog').getByRole('button',{name:'Hủy',exact:true}).click();
  pass(account.username+': Dashboard quick-create respects role and contract');
  for(const screen of ['my-tasks','projects','notes','calendar','notifications','workspaces'])await visit(screen);
  pass(account.username+': My Tasks, Projects, Notes, Calendar, Notifications and Workspace render');
  await visit('projects');await page.locator(`a[href="/projects/${fixture.project.id}"]`).first().click();await page.locator('h1').filter({hasText:fixture.project.name}).waitFor();
  await page.getByRole('button',{name:/2\. Công việc/}).click();await page.getByText(fixture.tasks[0].title,{exact:true}).click();await page.waitForURL(u=>u.pathname==='/tasks/'+fixture.tasks[0].id);
  await page.getByRole('note').filter({hasText:'Vai trò trong dự án:'}).waitFor();await page.waitForTimeout(3000);
  assert.equal(await page.getByRole('button',{name:'Sửa thông tin / deadline',exact:true}).count(),manager?1:0);
  assert.equal(await page.getByRole('button',{name:'Chuyển giao người phụ trách',exact:true}).count(),manager?1:0);
  await page.screenshot({path:new URL(account.username+'-task-detail.png',dir).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
  pass(account.username+': Task Detail hides management from executors/readers');
  await visit('my-role');assert.equal(await page.locator('nav.app-sidebar a[href="/admin"]').count(),0);
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  await page.screenshot({path:new URL(account.username+'-mobile.png',dir).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});pass(account.username+': role guide and mobile sidebar fit');
  await page.setViewportSize({width:1440,height:1000});const ended=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/auth/web/logout');await page.locator('nav.app-sidebar').getByRole('button',{name:'Đăng xuất',exact:true}).click();assert.equal((await ended).status(),200);await page.waitForURL(/login/);
  assert.equal((await context.cookies()).some(c=>c.name==='stm_refresh'),false);pass(account.username+': logout revokes and clears browser cookie');
  await page.waitForTimeout(15000);await context.close();
 }
 assert.deepEqual(errors,[]);assert.deepEqual(http,[]);
}catch(error){results.push({test:'Readiness browser acceptance',status:'FAIL',error:error.stack});process.exitCode=1;if(page&&!page.isClosed())await page.screenshot({path:new URL('failure.png',dir).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});}
finally{await writeFile(new URL('results.json',dir),JSON.stringify({baseline:'b97022a2682e16d3f5e5b4189364e11b50a04c2f',base,results,errors,http},null,2));await browser.close();}
