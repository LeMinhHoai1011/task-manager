import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from '../../.tools/browser/node_modules/playwright/index.mjs';

const base=process.env.SMOKE_BASE || 'http://127.0.0.1:5173';
const mobileOnly=process.env.UI_MOBILE_ONLY==='1';
const out=new URL('../../.run/ui-daylight/',import.meta.url);
await mkdir(out,{recursive:true});
const username=`ui_daylight_${Date.now().toString(36)}`;
const password=`Test-${crypto.randomUUID()}!`;
const checks=[];
function pass(name){checks.push(name);console.log(`PASS ${name}`);}
const registered=await fetch(`${base}/api/v1/auth/register`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,email:`${username}@example.test`,password,display_name:'UI daylight review'})});
assert.equal(registered.status,200);
const loginResponse=await fetch(`${base}/api/v1/auth/login`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password,client_type:'WEB'})});
assert.equal(loginResponse.status,200);
const seedToken=(await loginResponse.json()).data.access_token;
async function seed(path,body){
  const response=await fetch(`${base}/api/v1${path}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${seedToken}`,'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify(body)});
  assert.equal(response.status,200,`seed ${path}`);return (await response.json()).data;
}
const workspace=await seed('/workspaces',{name:'UI test workspace',type:'TEAM'});
const project=await seed('/projects',{workspace_id:workspace.id,name:'Kiểm thử thiết kế SmartTask'});
await seed('/tasks',{project_id:project.id,title:'Kiểm tra giao diện sáng',priority:'MEDIUM'});
await seed('/tasks',{project_id:project.id,title:'Rà soát lịch và deadline',priority:'HIGH',due_date:'2026-11-10',due_timezone:'Asia/Bangkok'});
await seed('/me/tasks',{title:'Kiểm tra danh sách công việc cá nhân'});
await seed('/notes',{scope:'PERSONAL',title:'Ghi chú kiểm thử bố cục',content:'Kiểm tra độ dễ đọc và phối màu sáng.',isPinned:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
try{
  const context=await browser.newContext({viewport:{width:1440,height:1000},colorScheme:'dark',serviceWorkers:'block'});
  await context.addInitScript(()=>{if(!localStorage.getItem('tm.palette-version'))localStorage.setItem('tm.theme','dark');});
  const page=await context.newPage();
  await page.goto(`${base}/login`);
  await page.waitForFunction(()=>document.documentElement.classList.contains('dark')===false && localStorage.getItem('tm.theme')==='light');
  const intro=await page.locator('.auth-intro').evaluate(element=>getComputedStyle(element).backgroundColor);
  assert.equal(intro,'rgb(237, 244, 240)');
  await page.screenshot({path:new URL('login-desktop.png',out).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
  pass('light login under dark OS and old dark preference');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[type="password"]').fill(password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/(dashboard|tasks)$/);
  pass('browser login after theme changes');
  await page.goto(`${base}/dashboard`);
  await page.locator('.dashboard-page').waitFor();
  await page.waitForTimeout(1000);
  assert.equal(await page.locator('.app-sidebar').evaluate(element=>getComputedStyle(element).backgroundColor),'rgb(250, 252, 251)');
  assert.equal(await page.locator('.app-shell').evaluate(element=>getComputedStyle(element).backgroundColor),'rgb(244, 247, 246)');
  pass('sidebar and workspace use the light palette');
  await page.screenshot({path:new URL('dashboard-desktop.png',out).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
  for(const route of (mobileOnly?[]:['/my-tasks','/projects','/calendar','/notes','/settings'])){
    await page.goto(`${base}${route}`);
    await page.locator('main').waitFor();await page.waitForTimeout(350);
    assert.equal(await page.locator('html').evaluate(element=>element.classList.contains('dark')),false);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`overflow on ${route}`);
    await page.screenshot({path:new URL(`${route.slice(1)}-desktop.png`,out).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
    pass(`light route and desktop layout ${route}`);
  }
  const toggle=page.getByRole('button',{name:'Đổi giao diện',exact:true});
  await toggle.click();await page.waitForFunction(()=>document.documentElement.classList.contains('dark'));
  await page.reload();await page.waitForFunction(()=>document.documentElement.classList.contains('dark'));
  await page.getByRole('button',{name:'Đổi giao diện',exact:true}).click();
  await page.waitForFunction(()=>!document.documentElement.classList.contains('dark'));
  pass('theme toggle works and explicit preference survives reload');
  // Respect the real API's per-minute limits when sweeping many routes.
  // Do not disable protection or spoof a client address for visual checks.
  if(!mobileOnly){
    console.log('Waiting for the API rate-limit window before the mobile sweep...');
    await page.waitForTimeout(60000);
  }
  await page.setViewportSize({width:390,height:844});
  for(const route of ['/dashboard','/my-tasks','/projects','/calendar']){
    await page.goto(`${base}${route}`);await page.locator('main').waitFor();await page.waitForTimeout(350);
    if(route==='/my-tasks')await page.getByRole('listitem').getByText('Kiểm tra danh sách công việc cá nhân',{exact:true}).waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`mobile overflow ${route}`);
    await page.screenshot({path:new URL(`${route.slice(1)}-mobile.png`,out).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
    pass(`mobile layout ${route}`);
  }
  await page.getByRole('button',{name:'Mở menu',exact:true}).click();
  await page.locator('.app-sidebar.is-open').waitFor();
  await page.screenshot({path:new URL('navigation-mobile.png',out).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true});
  pass('mobile navigation opens');
  await writeFile(new URL('checks.json',out),JSON.stringify({base,username,checks,passed:checks.length},null,2));
  console.log(`DAYLIGHT UI PASS: ${checks.length} checks`);
}finally{await browser.close();}
