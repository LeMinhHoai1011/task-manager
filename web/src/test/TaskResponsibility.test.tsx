import {render,screen,fireEvent,waitFor} from "@testing-library/react";
import {test,expect,vi} from "vitest";
import TaskResponsibility from "../pages/TaskResponsibility";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",()=>({apiFetch:request}));
test("uses server-authorized candidates and sends the task version",async()=>{
  request.mockImplementation((path:string,opts:any)=>{
    if(opts?.method==="POST")return Promise.resolve({data:{version:8}});
    if(path.endsWith("assignment-candidates"))return Promise.resolve({data:[{userId:"u1",displayName:"Alice"},{userId:"u2",displayName:"Bob"}]});
    return Promise.resolve({data:{primaryUserId:"u1"}});
  });
  const changed=vi.fn();render(<TaskResponsibility canAssign id="task1" version="7" onChanged={changed}/>);
  await screen.findByText("u1");
  expect(request.mock.calls.some(([path])=>path.endsWith("assignment-candidates"))).toBe(false);
  fireEvent.click(screen.getByRole("button",{name:"Chuyển giao người phụ trách"}));
  await screen.findByRole("option",{name:"Bob"});
  fireEvent.change(screen.getByLabelText("Người phụ trách mới"),{target:{value:"u2"}});
  fireEvent.click(screen.getByRole("button",{name:"Chuyển giao"}));
  await waitFor(()=>expect(request).toHaveBeenCalledWith("/tasks/task1/primary-assignee",expect.objectContaining({ifMatch:"7",body:{userId:"u2"}})));
  await waitFor(()=>expect(changed).toHaveBeenCalled());
});

test("opening a readable task does not request assignment-management data",async()=>{
  request.mockReset();
  request.mockResolvedValue({data:{primaryUserId:null}});
  render(<TaskResponsibility id="readonly-task" version="1" onChanged={()=>{}}/>);
  await screen.findByText("Chưa chỉ định");
  await waitFor(()=>expect(request).toHaveBeenCalledWith("/tasks/readonly-task/primary-assignee"));
  expect(request.mock.calls.some(([path])=>path.endsWith("assignment-candidates"))).toBe(false);
  expect(screen.queryByRole("button",{name:"Chuyển giao người phụ trách"})).not.toBeInTheDocument();
});
