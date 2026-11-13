import {act,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {MemoryRouter,Route,Routes} from "react-router-dom";
import {expect,test,vi} from "vitest";
import TaskDetail from "../pages/TaskDetail";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
vi.mock("../realtime/RealtimeContext",()=>({useRealtimeEvent:()=>{}}));
vi.mock("../pages/TaskResponsibility",()=>({default:()=>null}));
vi.mock("../pages/WorkflowPanel",()=>({default:()=>null}));
vi.mock("../components/ProjectTaskEditor",()=>({default:()=>null}));

test("subtask uses the parent endpoint, needs no project picker and blocks repeated clicks",async()=>{
  let finish!:(value:unknown)=>void;
  let children:any[]=[];
  request.mockImplementation((path:string,options:any)=>{
    if(path.endsWith("/access"))return Promise.resolve({data:{role:"MANAGER",metadata:true,assignment:true,structure:true,subtask:true,execute:true,comment:true,review:true}});
    if(path==="/tasks/parent")return Promise.resolve({data:{id:"parent",project_id:"project-a",title:"Việc lớn",state:"TODO",version:1}});
    if(path==="/tasks/parent/subtasks"&&options?.method==="POST")return new Promise(resolve=>{finish=value=>{children=[{id:"child",title:"Việc con",state:"TODO"}];resolve(value);};});
    if(path==="/tasks/parent/subtasks")return Promise.resolve({data:children});
    return Promise.resolve({data:[]});
  });
  render(<MemoryRouter initialEntries={["/tasks/parent"]}><Routes><Route path="/tasks/:id" element={<TaskDetail/>}/></Routes></MemoryRouter>);
  const title=await screen.findByRole("textbox",{name:"Tên việc con"});
  expect(screen.queryByLabelText("Dự án giao việc")).not.toBeInTheDocument();
  fireEvent.change(title,{target:{value:"Việc con"}});
  fireEvent.click(screen.getByRole("button",{name:"Thêm việc con"}));
  expect(screen.getByRole("button",{name:"Đang tạo..."})).toBeDisabled();
  fireEvent.click(screen.getByRole("button",{name:"Đang tạo..."}));
  expect(request.mock.calls.filter(([,opts])=>opts?.method==="POST")).toHaveLength(1);
  expect(request).toHaveBeenCalledWith("/tasks/parent/subtasks",expect.objectContaining({body:{title:"Việc con"}}));
  await act(async()=>finish({data:{id:"child",title:"Việc con",state:"TODO"}}));
  await waitFor(()=>expect(screen.getByRole("link",{name:"Việc con"})).toHaveAttribute("href","/tasks/child"));
});
