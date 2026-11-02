import {fireEvent,render,screen,waitFor,cleanup} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {afterEach,expect,test,vi} from "vitest";
import Tasks from "../pages/Tasks";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
vi.mock("../realtime/RealtimeContext",()=>({useRealtimeEvent:()=>{}}));
afterEach(()=>{cleanup();request.mockReset();});
async function board(){render(<MemoryRouter><Tasks/></MemoryRouter>);await screen.findByText("Board task");fireEvent.click(screen.getByRole("button",{name:"Kanban"}));return screen.getByLabelText("Chuyển trạng thái Board task");}
test("restores the card when workflow denies a transition",async()=>{
  request.mockImplementation((_path:string,options:any)=>options?.method==="POST"?Promise.reject({uiMessage:"Quyền đã bị thu hồi"}):Promise.resolve({data:{items:[{id:"task1",title:"Board task",state:"TODO",version:7}]}}));
  const select=await board();fireEvent.change(select,{target:{value:"IN_PROGRESS"}});
  await screen.findByText("Quyền đã bị thu hồi");
  await waitFor(()=>expect(screen.getByLabelText("Chuyển trạng thái Board task")).toHaveValue("TODO"));
  expect(request).toHaveBeenCalledWith("/tasks/task1/workflow/start",expect.objectContaining({method:"POST",ifMatch:"7"}));
});
test("uses the server submit result and cannot skip approval",async()=>{
  request.mockImplementation((_path:string,options:any)=>options?.method==="POST"?Promise.resolve({data:{to_state:"PENDING_APPROVAL",version:8}}):Promise.resolve({data:{items:[{id:"task1",title:"Board task",state:"IN_PROGRESS",version:7}]}}));
  await board();fireEvent.change(screen.getByLabelText("Chuyển trạng thái Board task"),{target:{value:"COMPLETED"}});
  await waitFor(()=>expect(screen.getByLabelText("Chuyển trạng thái Board task")).toHaveValue("PENDING_APPROVAL"));
  expect(request).toHaveBeenCalledWith("/tasks/task1/workflow/submit",expect.objectContaining({method:"POST",ifMatch:"7"}));
});
