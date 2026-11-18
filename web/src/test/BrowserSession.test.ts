import {beforeEach,afterEach,expect,test,vi} from "vitest";
const channels=new Set<MockChannel>();
class MockChannel extends EventTarget {
  constructor(public name:string){super();channels.add(this);}
  postMessage(data:unknown){for(const peer of channels)if(peer!==this&&peer.name===this.name)queueMicrotask(()=>peer.dispatchEvent(new MessageEvent("message",{data})));}
  close(){channels.delete(this);}
}
beforeEach(()=>{
  let queue=Promise.resolve();
  vi.stubGlobal("navigator",{locks:{request:(_name:string,action:()=>unknown)=>{const run=queue.then(action);queue=run.then(()=>{},()=>{});return run;}}});
  vi.stubGlobal("BroadcastChannel",MockChannel);
});

afterEach(()=>{channels.clear();vi.unstubAllEnvs();vi.unstubAllGlobals();vi.resetModules();localStorage.clear();});
test("secure browser mode stores access only in memory and refreshes using CSRF plus HttpOnly cookie",async()=>{
  vi.stubEnv("VITE_SECURE_BROWSER_SESSION","true");vi.resetModules();localStorage.clear();
  localStorage.setItem("tm.refresh_token","legacy-secret");localStorage.setItem("tm.access_token","legacy-access");
  const client=await import("../api/client");
  expect(localStorage.getItem("tm.refresh_token")).toBeNull();
  client.setTokens("memory-access","must-never-be-stored");
  expect(client.getToken()).toBe("memory-access");expect(client.getRefreshToken()).toBeNull();
  expect(localStorage.getItem("tm.access_token")).toBeNull();expect(localStorage.getItem("tm.refresh_token")).toBeNull();
  const fetcher=vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({data:{csrf_token:"csrf-proof"}})))
    .mockResolvedValueOnce(new Response(JSON.stringify({data:{access_token:"rotated-access",user_id:"u2",email:"u2@test",role:"USER",must_change_password:false}})));
  vi.stubGlobal("fetch",fetcher);
  const [a,b]=await Promise.all([client.refreshAccessToken(),client.refreshAccessToken()]);expect(a).toBe("rotated-access");expect(b).toBe(a);
  expect(fetcher).toHaveBeenCalledTimes(2);
  const [url,options]=fetcher.mock.calls[1];expect(url).toBe("/api/v1/auth/web/refresh");
  expect(options.credentials).toBe("same-origin");expect(options.headers["X-CSRF-Token"]).toBe("csrf-proof");
  expect(options.body).toBe("{}");expect(localStorage.getItem("tm.refresh_token")).toBeNull();
  client.clearTokens();expect(client.getToken()).toBeNull();expect(localStorage.getItem("tm.browser_session")).toBeNull();
});
test("logout while refresh is in flight cannot restore access in memory",async()=>{
  vi.stubEnv("VITE_SECURE_BROWSER_SESSION","true");vi.resetModules();localStorage.clear();
  const client=await import("../api/client");client.setTokens("access-before");
  let finish!:(r:Response)=>void;
  const fetcher=vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({data:{csrf_token:"csrf-proof"}})))
    .mockImplementationOnce(()=>new Promise<Response>(resolve=>{finish=resolve;}));
  vi.stubGlobal("fetch",fetcher);const pending=client.refreshAccessToken();
  await vi.waitFor(()=>expect(fetcher).toHaveBeenCalledTimes(2));client.clearTokens();
  finish(new Response(JSON.stringify({data:{access_token:"too-late"}})));
  expect(await pending).toBeNull();expect(client.getToken()).toBeNull();expect(localStorage.getItem("tm.browser_session")).toBeNull();
});

test("two isolated tabs share one rotated browser session instead of burning the cookie twice",async()=>{
  vi.stubEnv("VITE_SECURE_BROWSER_SESSION","true");vi.resetModules();localStorage.clear();
  const a=await import("../api/client");a.setTokens("old-access");
  vi.resetModules();const b=await import("../api/client");
  let rotations=0;
  vi.stubGlobal("fetch",vi.fn(async(url:string)=>{
    if(url.endsWith("/csrf"))return new Response(JSON.stringify({data:{csrf_token:"csrf-proof"}}));
    rotations++;
    return rotations===1?new Response(JSON.stringify({data:{access_token:"new-access",expires_at:"2099-01-01T00:00:00Z",user_id:"same-user",email:"same@test"}})):new Response("{}",{status:401});
  }));
  const tokens=await Promise.all([a.refreshAccessToken(),b.refreshAccessToken()]);
  expect(tokens).toEqual(["new-access","new-access"]);expect(rotations).toBe(1);
  expect(a.getToken()).toBe("new-access");expect(b.getToken()).toBe("new-access");expect(localStorage.getItem("tm.browser_session")).toBe("1");
  const stale=new MockChannel("smarttask:browser-session");
  stale.postMessage({type:"rotated",data:{access_token:"stale-access",generation:"obsolete",expires_at:"2099-01-01T00:00:00Z"}});
  await new Promise(resolve=>setTimeout(resolve,0));expect(a.getToken()).toBe("new-access");expect(b.getToken()).toBe("new-access");

});
