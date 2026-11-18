import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const base=process.env.SMARTTASK_ROLES_WEB||'http://127.0.0.1:5173',password=process.env.SMARTTASK_LOCAL_DEMO_PASSWORD;
assert.ok(password&&['localhost','127.0.0.1'].includes(new URL(base).hostname));
const output=new URL('../../.run/workspace-ux/',import.meta.url);await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true}),errors=[],results=[];
let queue=Promise.resolve(),nextRequest=0;
const path=name=>new URL(name,output).pathname.replace(/^\/([A-Za-z]:)/,'$1');
async function shot(page,name){await ready(page);await page.evaluate(()=>{document.activeElement?.blur();window.scrollTo(0,0);});await page.screenshot({path:path(name+'.png'),fullPage:true});}
async function ready(page){await page.waitForFunction(()=>!document.querySelector('.app-main .skeleton,.app-main .animate-spin'));}
async function login(username){const ctx=await browser.newContext({viewport:{width:1440,height:1000},serviceWorkers:'block',timezoneId:'Asia/Bangkok'});ctx.setDefaultTimeout(30000);await ctx.route('**/api/v1/**',async route=>{const turn=queue.then(async()=>{await new Promise(r=>setTimeout(r,Math.max(0,nextRequest-Date.now())));nextRequest=Date.now()+800;});queue=turn.catch(()=>{});await turn;await route.continue();});const page=await ctx.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(base+'/login');await page.locator('input[autocomplete="username"]').fill(username);await page.locator('input[type="password"]').fill(password);await page.locator('button[type="submit"]').click();await page.waitForURL(u=>u.pathname!='/login');await ready(page);return {ctx,page};}
try{
  const admin=await login('admin');let page=admin.page;
  await page.getByRole('heading',{name:'Quản lý tài khoản',exact:true}).waitFor();await page.getByText('@employee', {exact:false}).waitFor();await shot(page,'admin-desktop');
  await page.getByLabel('Tìm tài khoản').fill('employee');await page.getByRole('button',{name:'Tìm',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.directory-table tbody tr').length===1);await shot(page,'admin-search');
  await page.setViewportSize({width:390,height:844});await shot(page,'admin-mobile');assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'admin overflows mobile');results.push('Admin: full directory, active account, search, responsive');await admin.ctx.close();
  const user=await login('user');page=user.page;await page.goto(base+'/calendar');await page.getByRole('heading',{name:'Lịch & nhắc hẹn',exact:true}).waitFor();await ready(page);
  const name='Kiểm thử lịch hẹn '+Date.now();const date=new Date(Date.now()+2*86400000),pad=n=>String(n).padStart(2,'0');const local=`${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}`;
  await page.getByLabel('Tiêu đề',{exact:true}).fill(name);await page.getByLabel('Nội dung cần nhớ').fill('Hồ sơ và thông tin cuộc hẹn đã lưu.');await page.getByLabel('Thời điểm hẹn',{exact:true}).fill(local+'T15:00');await page.getByLabel('Thời điểm nhắc',{exact:true}).fill(local+'T14:30');
  await page.getByRole('button',{name:'Lưu lịch hẹn & nhắc',exact:true}).click();await page.getByRole('status').filter({hasText:'Đã lưu lịch nhắc'}).waitFor();await page.locator('.reminder-row').filter({hasText:name}).waitFor();
  await page.reload();await page.locator('.reminder-row').filter({hasText:name}).waitFor();await page.getByLabel('Ngày xem').fill(local);await page.getByRole('button',{name:'Xem ngày '+local,exact:true}).click();await page.locator('.agenda-row').filter({hasText:name}).waitFor();await shot(page,'calendar-desktop');
  let row=page.locator('.reminder-row').filter({hasText:name});await row.getByRole('button',{name:'Đổi giờ',exact:true}).click();await row.getByLabel('Thời điểm nhắc mới').fill(local+'T14:00');await row.getByRole('button',{name:'Lưu giờ nhắc'}).click();await page.getByRole('status').filter({hasText:'Đã lưu thay đổi'}).waitFor();await row.getByRole('button',{name:'Tạm dừng'}).click();await row.getByRole('button',{name:'Tiếp tục',exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});await shot(page,'calendar-mobile');assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'calendar overflows mobile');
  await page.goto(base+'/notes');await page.getByRole('heading',{name:'Thông tin đã lưu',exact:true}).waitFor();await shot(page,'notes-mobile');await page.setViewportSize({width:1440,height:1000});await shot(page,'notes-desktop');
  await page.goto(base+'/my-tasks');await page.getByText(name,{exact:true}).first().waitFor();await shot(page,'personal-tasks');results.push('User: appointment saved, survives reload, calendar shows it, reminder edited/paused, task retained, mobile fits');
  await page.goto(base+'/settings');await page.getByRole('button',{name:'Thông báo & Giờ nghỉ',exact:true}).click();await page.getByRole('button',{name:'Lưu giờ yên lặng'}).waitFor({state:'visible'});await expectEnabled(page.getByRole('button',{name:'Lưu giờ yên lặng'}));await shot(page,'notification-settings');results.push('Settings: quiet-hour preferences loaded');await user.ctx.close();assert.deepEqual(errors,[]);
  await writeFile(path('results.json'),JSON.stringify({results,errors},null,2));console.log(JSON.stringify({results,errors}));
}catch(e){console.error(e);throw e;}finally{await browser.close();}
async function expectEnabled(locator){const element=await locator.elementHandle();await locator.page().waitForFunction(el=>!el.disabled,element);}
