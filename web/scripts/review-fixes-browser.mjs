import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const base='http://127.0.0.1:5174',password=process.env.SMARTTASK_LOCAL_DEMO_PASSWORD;
assert.ok(password);const browser=await chromium.launch({channel:'msedge',headless:true});
const dir=new URL('../../.run/review-fixes/',import.meta.url);await mkdir(dir,{recursive:true});
const output=name=>new URL(name,dir).pathname.replace(/^\/([A-Za-z]:)/,'$1');
const context=await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'Asia/Bangkok',serviceWorkers:'block'});context.setDefaultTimeout(30000);
const errors=[],results=[];let refreshes=0,failSave=false;
context.on('page',page=>page.on('pageerror',error=>errors.push(error.message)));
context.on('request',request=>{if(new URL(request.url()).pathname.endsWith('/auth/web/refresh'))refreshes++;});
await context.route('**/api/v1/notes/*',async route=>{if(failSave&&route.request().method()==='PUT')await route.abort('failed');else await route.continue();});
try{
 const a=await context.newPage();await a.goto(base+'/login');await a.locator('input[autocomplete="username"]').fill('user');await a.locator('input[type="password"]').fill(password);await a.locator('button[type="submit"]').click();await a.waitForURL(url=>url.pathname==='/my-tasks');
 refreshes=0;const b=await context.newPage();await Promise.all([a.reload(),b.goto(base+'/my-tasks')]);
 for(const page of [a,b])await page.locator('nav.app-sidebar').getByLabel('Vai trò hiện tại').getByText('Người dùng',{exact:true}).waitFor();
 const tokens=await Promise.all([a,b].map(page=>page.evaluate(async()=>{const client=await import('/src/api/client.ts');return client.getToken();})));
 assert.ok(tokens[0]&&tokens[0]===tokens[1]);assert.equal(refreshes,1,'concurrent tabs must reuse one rotation');
 for(let i=0;i<2;i++){const response=await context.request.get(base+'/api/v1/me/access',{headers:{Authorization:'Bearer '+tokens[i]}});assert.equal(response.status(),200);}
 assert.equal(await a.evaluate(()=>localStorage.getItem('tm.access_token')),null);assert.equal(await a.evaluate(()=>localStorage.getItem('tm.refresh_token')),null);
 results.push('Two real tabs: one cookie rotation, both access tokens valid, no persisted tokens');console.log(results[0]);await b.close();
 await a.locator('nav.app-sidebar a[href="/notes"]').click();const created=a.waitForResponse(response=>new URL(response.url()).pathname==='/api/v1/notes'&&response.request().method()==='POST'&&response.ok());await a.getByRole('button',{name:'Tạo ghi chú mới'}).click();await created;const editor=a.locator('#note-editor-textarea');await editor.waitFor();await a.waitForFunction(()=>document.querySelector('input[placeholder="Tiêu đề ghi chú..."]')?.value==='Ghi chú mới'&&document.getElementById('note-editor-textarea')?.value==='');
 const title='Review regression '+Date.now();await a.getByPlaceholder('Tiêu đề ghi chú...').fill(title);
 const saved=a.waitForResponse(response=>/\/notes\/[^/]+$/.test(new URL(response.url()).pathname)&&response.request().method()==='PUT'&&response.ok());await editor.fill('Saved content before navigation test');await saved;
 failSave=true;await editor.fill('Unsaved draft must stay');await a.locator('nav.app-sidebar a[href="/calendar"]').click();
 await a.getByRole('alertdialog',{name:'Ghi chú chưa lưu'}).waitFor();await a.getByRole('button',{name:'Lưu và rời trang'}).click();
 await a.getByRole('alert').filter({hasText:'Chưa lưu được thay đổi'}).waitFor();assert.equal(new URL(a.url()).pathname,'/notes');assert.equal(await editor.inputValue(),'Unsaved draft must stay');
 await a.getByRole('button',{name:'Ở lại',exact:true}).click();await a.evaluate(()=>history.back());await a.getByRole('alertdialog',{name:'Ghi chú chưa lưu'}).waitFor();assert.equal(await editor.inputValue(),'Unsaved draft must stay');
 await a.screenshot({path:output('notes-navigation-blocked.png'),fullPage:true});
 await a.getByRole('button',{name:'Ở lại',exact:true}).click();failSave=false;await a.locator('nav.app-sidebar a[href="/calendar"]').click();await a.getByRole('button',{name:'Lưu và rời trang'}).click();await a.waitForURL(url=>url.pathname==='/calendar');
 await a.locator('nav.app-sidebar a[href="/notes"]').click();await a.getByText(title,{exact:true}).first().click();await editor.waitFor();await a.waitForFunction(()=>document.getElementById('note-editor-textarea')?.value==='Unsaved draft must stay');assert.equal(await editor.inputValue(),'Unsaved draft must stay');results.push('Notes: link and native Back blocked; failed save retains draft; successful save persists before leaving');
 assert.deepEqual(errors,[]);await writeFile(output('results.json'),JSON.stringify({results,errors},null,2));console.log(JSON.stringify({results,errors}));
}finally{await context.close();await browser.close();}
