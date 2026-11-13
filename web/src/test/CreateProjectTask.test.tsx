import {fireEvent,render,screen,waitFor} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {beforeEach,expect,test,vi} from "vitest";
import CreateProjectTask from "../components/CreateProjectTask";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../auth/AccessContext",()=>({useAccess:()=>({projects:[{id:"project-a",canCreateTask:true},{id:"project-b",canCreateTask:true}]})}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
beforeEach(()=>request.mockReset());

test("project context creates directly without fetching or selecting another project",async()=>{
  request.mockResolvedValue({data:{id:"created"}});
  const done=vi.fn();
  render(<MemoryRouter><CreateProjectTask projectId="project-a" projectName="Dự án A" onCreated={done}/></MemoryRouter>);
  fireEvent.click(screen.getByRole("button",{name:"Tạo công việc dự án"}));
  expect(screen.getByRole("note")).toHaveTextContent("Dự án A");
  expect(screen.queryByLabelText("Dự án giao việc")).not.toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Tiêu đề công việc dự án"),{target:{value:"Việc nhỏ"}});
  fireEvent.click(screen.getByRole("button",{name:"Tạo công việc"}));
  await waitFor(()=>expect(done).toHaveBeenCalledOnce());
  expect(request).toHaveBeenCalledWith("/tasks",expect.objectContaining({method:"POST",body:expect.objectContaining({project_id:"project-a",title:"Việc nhỏ"})}));
});

test("changing the selected project never submits the previous project",async()=>{
  request.mockResolvedValue({data:{id:"created"}});
  const view=render(<MemoryRouter><CreateProjectTask projectId="project-a" initialOpen onCreated={()=>{}}/></MemoryRouter>);
  fireEvent.change(screen.getByLabelText("Tiêu đề công việc dự án"),{target:{value:"Việc mới"}});
  view.rerender(<MemoryRouter><CreateProjectTask projectId="project-b" initialOpen onCreated={()=>{}}/></MemoryRouter>);
  fireEvent.click(screen.getByRole("button",{name:"Tạo công việc"}));
  await waitFor(()=>expect(request).toHaveBeenCalledWith("/tasks",expect.objectContaining({body:expect.objectContaining({project_id:"project-b"})})));
});

test("unscoped task creation still requires choosing a project",async()=>{
  request.mockResolvedValue({data:{items:[{id:"project-a",name:"Dự án A"}],has_more:false}});
  render(<MemoryRouter><CreateProjectTask onCreated={()=>{}} initialOpen/></MemoryRouter>);
  await screen.findByRole("option",{name:"Dự án A"});
  fireEvent.change(screen.getByLabelText("Tiêu đề công việc dự án"),{target:{value:"Chưa chọn dự án"}});
  expect(screen.getByRole("button",{name:"Tạo công việc"})).toBeDisabled();
  fireEvent.change(screen.getByLabelText("Dự án giao việc"),{target:{value:"project-a"}});
  expect(screen.getByRole("button",{name:"Tạo công việc"})).toBeEnabled();
});
