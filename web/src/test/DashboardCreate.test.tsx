import {render,screen,fireEvent,waitFor,within} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {beforeEach,expect,test,vi} from "vitest";
import Dashboard from "../pages/Dashboard";
const {request,access}=vi.hoisted(()=>({request:vi.fn(),access:{systemAdmin:false,workspaces:[],projects:[] as {id:string;name:string;canCreateTask:boolean}[]}}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
vi.mock("../auth/AuthContext",()=>({useAuth:()=>({user:{id:"u1",email:"Test"}})}));
vi.mock("../auth/AccessContext",async original=>({...await original<typeof import("../auth/AccessContext")>(),useAccess:()=>access}));
vi.mock("../realtime/RealtimeContext",()=>({useRealtimeEvent:()=>{}}));
vi.mock("../components/ScopeFilters",()=>({default:()=>null}));
beforeEach(()=>{access.projects=[];request.mockReset();request.mockImplementation((path:string)=>Promise.resolve(path==="/dashboard"?{data:{total:0,focus:[],completion_rate:0}}:{data:{items:[]}}));});
test("Hôm nay links each task to its own scope and switches deadline buckets", async () => {
  request.mockImplementation((path: string) => Promise.resolve(path === "/dashboard"
    ? {data: {total: 0, focus: [], completion_rate: 0}}
    : {data: {items: path === "/dashboard/today" ? [
      {id: "own", title: "Private agenda", scope: "PERSONAL", state: "TODO", due_date_only: "2026-11-27"},
      {id: "assigned", title: "Team agenda", scope: "PROJECT", project_name: "Operations",
        responsibility: "PRIMARY", state: "TODO", due_date_only: "2026-11-27"},
    ] : []}}));
  render(<MemoryRouter><Dashboard /></MemoryRouter>);
  expect(await screen.findByRole("link", {name: "Private agenda"})).toHaveAttribute("href", "/my-tasks/own");
  expect(screen.getByRole("link", {name: "Team agenda"})).toHaveAttribute("href", "/tasks/assigned");
  expect(screen.getByText(/Operations · Phụ trách chính/)).toHaveTextContent("2026-11-27");
  fireEvent.click(screen.getByRole("button", {name: "7 ngày tới"}));
  await waitFor(() => expect(request).toHaveBeenCalledWith("/dashboard/today",
    expect.objectContaining({query: expect.objectContaining({bucket: "upcoming", page: 0})})));
});
test("member quick-create only offers personal work",async()=>{
  render(<MemoryRouter><Dashboard/></MemoryRouter>);fireEvent.click(screen.getByRole("button",{name:"Tạo việc nhanh"}));
  expect(within(screen.getByRole("dialog")).queryByRole("button",{name:"Việc dự án"})).not.toBeInTheDocument();
});
test("manager quick-create sends the project and an idempotency key",async()=>{
  access.projects=[{id:"p1",name:"Managed project",canCreateTask:true}];
  render(<MemoryRouter><Dashboard/></MemoryRouter>);fireEvent.click(screen.getByRole("button",{name:"Tạo việc nhanh"}));
  const dialog=within(screen.getByRole("dialog"));fireEvent.click(dialog.getByRole("button",{name:"Việc dự án"}));
  fireEvent.change(dialog.getByPlaceholderText("Bạn cần làm gì?"),{target:{value:"Real work"}});
  fireEvent.click(dialog.getByRole("button",{name:/^Tạo công việc$/}));
  await waitFor(()=>expect(request).toHaveBeenCalledWith("/tasks",expect.objectContaining({method:"POST",body:{title:"Real work",project_id:"p1"},idempotencyKey:expect.any(String)})));
});
