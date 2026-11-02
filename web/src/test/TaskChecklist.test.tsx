import {act,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {MemoryRouter,Route,Routes} from "react-router-dom";
import {expect,test,vi} from "vitest";
import TaskDetail from "../pages/TaskDetail";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
vi.mock("../realtime/RealtimeContext",()=>({useRealtimeEvent:()=>{}}));
vi.mock("../pages/TaskResponsibility",()=>({default:()=>null}));
vi.mock("../pages/WorkflowPanel",()=>({default:({disabled}:{disabled:boolean})=><button disabled={disabled}>Workflow test action</button>}));
test("checklist restores the original value on denial and blocks workflow while saving",async()=>{
  let rejectTick!:(error:unknown)=>void;
  request.mockImplementation((path:string,options:any)=>{
    if(options?.method==="PATCH")return new Promise((_resolve,reject)=>{rejectTick=reject;});
    if(path==="/tasks/task1")return Promise.resolve({data:{id:"task1",title:"Checklist task",state:"TODO",version:1}});
    if(path.endsWith("/checklist"))return Promise.resolve({data:[{id:"item1",title:"Acceptance item",done:false}]});
    return Promise.resolve({data:[]});
  });
  render(<MemoryRouter initialEntries={["/tasks/task1"]}><Routes><Route path="/tasks/:id" element={<TaskDetail/>}/></Routes></MemoryRouter>);
  const checkbox=await screen.findByLabelText("Acceptance item");fireEvent.click(checkbox);
  expect(checkbox).toBeChecked();expect(screen.getByRole("button",{name:"Workflow test action"})).toBeDisabled();
  await act(async()=>rejectTick({uiMessage:"Quyền đã thu hồi"}));
  await waitFor(()=>expect(checkbox).not.toBeChecked());expect(screen.getByRole("button",{name:"Workflow test action"})).toBeEnabled();
  await screen.findByText("Quyền đã thu hồi");
});
