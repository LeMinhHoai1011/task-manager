import {act,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {beforeEach,expect,test,vi} from "vitest";
import ResourcePicker from "../components/ResourcePicker";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
beforeEach(()=>{request.mockReset();});

test("tải thêm vẫn tìm và chọn được dữ liệu ở trang sau",async()=>{
  request.mockImplementation((_path:string,opts:any)=>Promise.resolve({data:opts.query.cursor?{items:[{id:"two",name:"Dự án sau"}],next_cursor:null}:{items:[{id:"one",name:"Dự án đầu"}],next_cursor:"next"}}));
  const change=vi.fn();render(<ResourcePicker kind="projects" label="Dự án" value="" onChange={change}/>);
  fireEvent.click(screen.getByRole("button",{name:"Dự án"}));
  fireEvent.click(await screen.findByRole("button",{name:"Tải thêm lựa chọn"}));
  fireEvent.click(await screen.findByRole("option",{name:"Dự án sau"}));
  expect(change).toHaveBeenCalledWith("two");
});

test("bỏ phản hồi chậm từ dự án cũ khi đổi ngữ cảnh",async()=>{
  let finish!:(value:unknown)=>void;
  request.mockImplementation((_path:string,opts:any)=>opts.query.project_id==="old"?new Promise(resolve=>{finish=resolve;}):Promise.resolve({data:{items:[{id:"new-task",title:"Việc mới"}],next_cursor:null}}));
  const view=render(<ResourcePicker kind="tasks" label="Công việc" projectId="old" value="" onChange={()=>{}}/>);
  view.rerender(<ResourcePicker kind="tasks" label="Công việc" projectId="new" value="" onChange={()=>{}}/>);
  await waitFor(()=>expect(request).toHaveBeenCalledTimes(2));
  await act(async()=>finish({data:{items:[{id:"old-task",title:"Việc cũ"}]}}));
  fireEvent.click(screen.getByRole("button",{name:"Công việc"}));
  expect(await screen.findByRole("option",{name:"Việc mới"})).toBeInTheDocument();
  expect(screen.queryByRole("option",{name:"Việc cũ"})).not.toBeInTheDocument();
});
