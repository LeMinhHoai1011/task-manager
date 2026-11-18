import {runInNewContext} from 'node:vm';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {chromium} from '../../.tools/browser/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
const sw=await readFile(new URL('../public/sw.js',import.meta.url),'utf8');let version=1;
const results=[];const pass=test=>{results.push({test,status:'PASS'});console.log('PASS '+test);};
const server=createServer((req,res)=>{
 res.setHeader('Cache-Control','no-store');
 if(req.url==='/sw.js'){res.setHeader('Content-Type','text/javascript');res.end(sw+'\n// Test rollout '+version);}
 else if(req.url==='/manifest.webmanifest'){res.setHeader('Content-Type','application/manifest+json');res.end('{}');}
 else if(req.url.startsWith('/api/')){res.setHeader('Content-Type','application/json');res.end('{"private":true}');}
 else {res.setHeader('Content-Type','text/html');res.end(`<html><body><h1>Shell ${version}</h1><input id="draft" /></body></html>`);}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({channel:'msedge',headless:true});
try{
 const handlers={};let online;
 runInNewContext(sw,{self:{location:{origin:'http://127.0.0.1'},addEventListener:(name,handler)=>handlers[name]=handler},URL,Response,
   fetch:async()=>new Response('fresh HTML',{headers:{'content-type':'text/html'}}),
   caches:{open:async()=>({put:async()=>{throw new Error('Quota exceeded');}}),match:async()=>new Response('stale HTML')}});
 handlers.fetch({request:{url:'http://127.0.0.1/',method:'GET',mode:'navigate'},respondWith:value=>online=value});
 assert.equal(await(await online).text(),'fresh HTML');pass('Cache quota errors cannot replace fresh online HTML with stale content');
 const context=await browser.newContext();const page=await context.newPage();await page.goto(base);
 await page.evaluate(async()=>{await caches.open('foreign-app-cache');await navigator.serviceWorker.register('/sw.js');await navigator.serviceWorker.ready;});
 await page.waitForFunction(()=>!!navigator.serviceWorker.controller);
 version=2;await page.reload();await page.getByRole('heading',{name:'Shell 2'}).waitFor();pass('Online navigation obtains new HTML rather than a stale cached shell');
 await page.evaluate(()=>fetch('/api/v1/private').then(r=>r.json()));
 const urls=await page.evaluate(async()=>{const out=[];for(const key of await caches.keys())for(const req of await(await caches.open(key)).keys())out.push(req.url);return out;});
 assert.equal(urls.some(u=>u.includes('/api/')),false);assert.ok((await page.evaluate(()=>caches.keys())).includes('foreign-app-cache'));pass('API data is not cached; foreign application caches are preserved');
 await context.setOffline(true);await page.reload();await page.getByRole('heading',{name:'Shell 2'}).waitFor();pass('Offline navigation uses the last public HTML shell');
 await context.setOffline(false);version=3;
 await page.locator('#draft').fill('Unsaved work');await page.evaluate(async()=>{const r=await navigator.serviceWorker.getRegistration();await r.update();});
 await page.waitForFunction(async()=>!!(await navigator.serviceWorker.getRegistration())?.waiting);
 assert.equal(await page.locator('#draft').inputValue(),'Unsaved work');pass('Worker update waits for consent and preserves unsaved content');
 await page.evaluate(async()=>{const changed=new Promise(resolve=>navigator.serviceWorker.addEventListener('controllerchange',resolve,{once:true}));(await navigator.serviceWorker.getRegistration()).waiting.postMessage({type:'SKIP_WAITING'});await changed;});
 await page.reload();await page.getByRole('heading',{name:'Shell 3'}).waitFor();pass('Consented update activates the new worker and loads the new HTML');
}catch(error){results.push({test:'Service Worker acceptance',status:'FAIL',error:error.stack});process.exitCode=1;}
finally{const dir=new URL('../../.run/readiness-head-20261113/',import.meta.url);await mkdir(dir,{recursive:true});await writeFile(new URL('service-worker-results.json',dir),JSON.stringify({results},null,2));await browser.close();server.closeAllConnections();server.close();}
