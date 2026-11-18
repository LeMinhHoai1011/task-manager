import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const base='http://127.0.0.1:5173',password=process.env.SMARTTASK_LOCAL_DEMO_PASSWORD;assert.ok(password);
const dir=new URL('../../.run/theme-contrast/',import.meta.url);await mkdir(dir,{recursive:true});const output=name=>new URL(name,dir).pathname.replace(/^\/([A-Za-z]:)/,'$1');
const browser=await chromium.launch({channel:'msedge',headless:true}),results=[],errors=[];let next=0,queue=Promise.resolve();
try{
 for(const account of ['employee','admin']){
  const ctx=await browser.newContext({viewport:{width:1440,height:1000},serviceWorkers:'block',reducedMotion:'reduce'});ctx.setDefaultTimeout(30000);
  await ctx.route('**/api/v1/**',async route=>{const q=queue.then(async()=>{await new Promise(r=>setTimeout(r,Math.max(0,next-Date.now())));next=Date.now()+700;});queue=q.catch(()=>{});await q;await route.continue();});
  const page=await ctx.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(base+'/login');await page.locator('input[autocomplete="username"]').fill(account);await page.locator('input[type="password"]').fill(password);await page.locator('button[type="submit"]').click();await page.waitForURL(url=>url.pathname!='/login');
  for(const route of account==='employee'?['/my-role','/dashboard','/notes','/my-tasks','/tasks','/calendar','/files','/projects','/workspaces','/settings']:['/admin']){
   await page.goto(base+route);await page.getByLabel('Vai trò hiện tại').getByText(account==='admin'?'Quản trị hệ thống':'Nhân viên',{exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('.app-main .animate-spin,.app-main .skeleton'));
   for(const theme of ['light','dark']){
    if(await page.locator('html').evaluate(el=>el.classList.contains('dark'))!==(theme==='dark'))await page.getByLabel('Đổi giao diện',{exact:true}).click();await page.waitForTimeout(200);
    const contrast=await page.evaluate(()=>{
     const canvas=document.createElement('canvas');canvas.width=canvas.height=1;const ctx=canvas.getContext('2d',{willReadFrequently:true}),cache=new Map();
     const rgba=color=>{if(cache.has(color))return cache.get(color);ctx.clearRect(0,0,1,1);ctx.fillStyle=color;ctx.fillRect(0,0,1,1);const c=Array.from(ctx.getImageData(0,0,1,1).data);c[3]/=255;cache.set(color,c);return c;};
     const blend=(fg,bg)=>fg.slice(0,3).map((c,i)=>c*fg[3]+bg[i]*(1-fg[3])).concat(1);
     const background=el=>{const chain=[];for(let p=el;p;p=p.parentElement)chain.push(p);return chain.reverse().reduce((bg,p)=>blend(rgba(getComputedStyle(p).backgroundColor),bg),[255,255,255,1]);};
     const luminance=c=>c.slice(0,3).map(x=>x/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4).reduce((s,x,i)=>s+x*[.2126,.7152,.0722][i],0);
     const failures=[];let checked=0;const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let node;
     while(node=walker.nextNode()){
      const text=node.textContent.trim(),el=node.parentElement;if(!text||!el?.closest('.app-main,.app-sidebar')||el.closest('script,style,svg,button:disabled,[aria-disabled="true"]'))continue;
      const style=getComputedStyle(el),range=document.createRange();range.selectNode(node);const rect=range.getBoundingClientRect();if(!rect.width||!rect.height||style.visibility==='hidden'||style.opacity==='0')continue;
      const bg=background(el),fg=blend(rgba(style.color),bg),a=luminance(fg),b=luminance(bg),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05),large=parseFloat(style.fontSize)>=24||(parseFloat(style.fontSize)>=18.66&&parseInt(style.fontWeight)>=700);
      checked++;if(ratio+.02<(large?3:4.5))failures.push({text:text.slice(0,55),class:el.className,ratio:Number(ratio.toFixed(2)),color:style.color,background:bg.slice(0,3).map(Math.round)});
     }
     return {checked,failures};
    });
    const row={account,route,theme,...contrast};results.push(row);console.log(JSON.stringify(row));
    if(['/my-role','/admin','/files'].includes(route))await page.screenshot({path:output(account+route.replaceAll('/','-')+'-'+theme+'.png'),fullPage:true});
   }
  }
  await page.reload();await page.waitForFunction(()=>document.documentElement.classList.contains('dark'));await ctx.close();
 }
 await writeFile(output('results.json'),JSON.stringify({results,errors},null,2));assert.deepEqual(errors,[]);if(!process.env.SMARTTASK_THEME_AUDIT_ONLY)assert.equal(results.flatMap(r=>r.failures).length,0,'Text contrast must meet 4.5:1 (3:1 for large text)');
}finally{await browser.close();}
