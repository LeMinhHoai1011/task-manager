import {afterEach,expect,test,vi} from "vitest";
import {waitFor} from "@testing-library/react";
import {connectRealtime} from "../realtime/socket";
afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();});
test("offline aborts an idle stream and online obtains a fresh ticket",async()=>{
  const network=vi.spyOn(navigator,"onLine","get").mockReturnValue(true);
  const fetcher=vi.fn((_url:string,options:any)=>Promise.resolve({ok:true,body:{getReader:()=>({read:()=>new Promise(resolve=>options.signal.addEventListener("abort",()=>resolve({done:true})))})}}));
  vi.stubGlobal("fetch",fetcher);
  const ticket=vi.fn().mockResolvedValueOnce("first").mockResolvedValueOnce("fresh");
  const status=vi.fn();const connection=connectRealtime({getTicket:ticket,onStatus:status});
  try {
    await waitFor(()=>expect(fetcher).toHaveBeenCalledTimes(1));
    network.mockReturnValue(false);window.dispatchEvent(new Event("offline"));
    expect(status).toHaveBeenLastCalledWith(false);
    expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true);
    network.mockReturnValue(true);window.dispatchEvent(new Event("online"));
    await waitFor(()=>expect(fetcher).toHaveBeenCalledTimes(2));
    expect(ticket).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[1][0]).toContain("ticket=fresh");
    expect(fetcher.mock.calls[1][1].headers).toEqual({Accept:"text/event-stream"});
  }finally{connection.close();}
  window.dispatchEvent(new Event("online"));expect(ticket).toHaveBeenCalledTimes(2);
});
