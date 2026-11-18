import {act,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {beforeEach,expect,test,vi} from "vitest";
import MemberPicker from "../components/MemberPicker";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
beforeEach(()=>{request.mockReset();});

test("giao hàng loạt chỉ chọn người hợp lệ cho tất cả công việc",async()=>{
  request.mockImplementation((path:string)=>Promise.resolve({data:path.includes("/one/")?[{userId:"a",displayName:"An"},{userId:"b",displayName:"Bình"}]:[{userId:"b",displayName:"Bình"}]}));
  const changed=vi.fn();
  render(<MemberPicker taskIds={["one","two"]} value="" onChange={changed}/>);
  fireEvent.click(screen.getByRole("button",{name:"Người phụ trách"}));
  await waitFor(()=>expect(request).toHaveBeenCalledTimes(2));
  await screen.findByRole("option",{name:/Bình/});
  expect(screen.queryByRole("option",{name:/^An/})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("option",{name:/Bình/}));expect(changed).toHaveBeenCalledWith("b");
});

test("phản hồi dự án cũ không ghi đè thành viên dự án mới",async()=>{
  let finish!:(value:unknown)=>void;
  request.mockImplementation((path:string)=>path.includes("/old/")?new Promise(resolve=>{finish=resolve;}):Promise.resolve({data:[{user_id:"new-member",display_name:"Người mới",username:"moi",role:"MEMBER"}]}));
  const view=render(<MemberPicker projectIds={["old"]} value="" onChange={()=>{}}/>);
  fireEvent.click(screen.getByRole("button",{name:"Người phụ trách"}));
  view.rerender(<MemberPicker projectIds={["new"]} value="" onChange={()=>{}}/>);
  await waitFor(()=>expect(request).toHaveBeenCalledTimes(2));
  await act(async()=>finish({data:[{user_id:"old-member",display_name:"Người cũ",role:"MEMBER"}]}));
  expect(await screen.findByRole("option",{name:/Người mới/})).toBeInTheDocument();
  expect(screen.queryByRole("option",{name:/Người cũ/})).not.toBeInTheDocument();
});
