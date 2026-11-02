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
  const changed=vi.fn();render(<TaskResponsibility id="task1" version="7" onChanged={changed}/>);
  await screen.findByRole("option",{name:"Bob"});
  fireEvent.change(screen.getByLabelText("Người phụ trách mới"),{target:{value:"u2"}});
  fireEvent.click(screen.getByRole("button",{name:"Chuyển giao"}));
  await waitFor(()=>expect(request).toHaveBeenCalledWith("/tasks/task1/primary-assignee",expect.objectContaining({ifMatch:"7",body:{userId:"u2"}})));
  await waitFor(()=>expect(changed).toHaveBeenCalled());
});
