import {act,render,screen,waitFor} from "@testing-library/react";
import {beforeEach,expect,test,vi} from "vitest";
import {AccessProvider,useAccess} from "../auth/AccessContext";
const {request,auth}=vi.hoisted(()=>({request:vi.fn(),auth:{user:{id:"u1"},token:"token1" as string|null}}));
vi.mock("../auth/AuthContext",()=>({useAuth:()=>auth}));
vi.mock("../realtime/RealtimeContext",()=>({useRealtimeEvent:()=>{}}));
vi.mock("../api/client",()=>({apiFetch:request,unwrapData:(r:any)=>r.data}));
function Probe(){const a=useAccess();return <p>{a.loading?"loading":a.error?"denied":a.systemAdmin?"system-admin":a.projects.map(p=>p.name).join(",")||"no-access"}</p>;}
beforeEach(()=>{request.mockReset();auth.token="token1";auth.user={id:"u1"};});
test("access defaults to denied while loading and on request failure",async()=>{
  request.mockRejectedValue(new Error("offline"));render(<AccessProvider><Probe/></AccessProvider>);
  expect(screen.getByText("loading")).toBeInTheDocument();await screen.findByText("denied");
});
test("ignores responses from a previous account after switching sessions",async()=>{
  let finish!:(r:any)=>void;request.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
  const view=render(<AccessProvider><Probe/></AccessProvider>);
  auth.user={id:"u2"};auth.token="token2";
  request.mockResolvedValue({data:{systemAdmin:false,workspaces:[],projects:[{id:"p2",name:"User 2 project"}]}});
  view.rerender(<AccessProvider><Probe/></AccessProvider>);await screen.findByText("User 2 project");
  await act(async()=>finish({data:{systemAdmin:true,workspaces:[],projects:[]}}));
  expect(screen.queryByText("system-admin")).not.toBeInTheDocument();
  auth.token=null;view.rerender(<AccessProvider><Probe/></AccessProvider>);
  await waitFor(()=>expect(screen.getByText("no-access")).toBeInTheDocument());
});
