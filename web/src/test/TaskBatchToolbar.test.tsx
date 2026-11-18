import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, expect, test, vi} from "vitest";
import TaskBatchToolbar from "../components/TaskBatchToolbar";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
beforeEach(()=>{request.mockReset();});
const selected=[{id:"one",title:"Việc một",version:2},{id:"two",title:"Việc hai",version:5}];

test("sends captured versions, removes only successes and explains stale tasks",async()=>{
  request.mockResolvedValue({data:[{id:"one",success:true,version:3},{id:"two",success:false,code:"VERSION_CONFLICT"}]});
  const complete=vi.fn();
  render(<TaskBatchToolbar selected={selected} onComplete={complete} onClear={()=>{}} onBusyChange={()=>{}}/>);
  fireEvent.click(screen.getByRole("button",{name:"Áp dụng"}));
  await waitFor(()=>expect(complete).toHaveBeenCalledWith(["one"]));
  expect(request).toHaveBeenCalledWith("/tasks/batch",expect.objectContaining({method:"PATCH",body:{items:[{id:"one",version:2},{id:"two",version:5}],action:"START",value:null}}));
  expect(screen.getByText("Đã xử lý 1/2 công việc.")).toBeInTheDocument();
  expect(screen.getByText(/Dữ liệu đã thay đổi/)).toBeInTheDocument();
});

test("requires delete confirmation and prevents duplicate submissions",async()=>{
  let finish!:(value:unknown)=>void;
  request.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  render(<TaskBatchToolbar selected={selected} onComplete={()=>{}} onClear={()=>{}} onBusyChange={()=>{}}/>);
  fireEvent.change(screen.getByLabelText("Thao tác hàng loạt"),{target:{value:"DELETE"}});
  expect(screen.getByRole("button",{name:"Áp dụng"})).toBeDisabled();
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.click(screen.getByRole("button",{name:"Áp dụng"}));
  fireEvent.click(screen.getByRole("button",{name:"Đang xử lý…"}));
  expect(request).toHaveBeenCalledTimes(1);
  await act(async()=>finish({data:selected.map(row=>({id:row.id,success:true}))}));
});

test("reuses idempotency key on a network retry",async()=>{
  request.mockRejectedValueOnce(new Error("Network")).mockResolvedValueOnce({data:selected.map(row=>({id:row.id,success:true}))});
  render(<TaskBatchToolbar selected={selected} onComplete={()=>{}} onClear={()=>{}} onBusyChange={()=>{}}/>);
  fireEvent.click(screen.getByRole("button",{name:"Áp dụng"}));
  await screen.findByText(/Chưa nhận đủ kết quả/);
  fireEvent.click(screen.getByRole("button",{name:"Áp dụng"}));
  await waitFor(()=>expect(request).toHaveBeenCalledTimes(2));
  expect(request.mock.calls[0][1].idempotencyKey).toBe(request.mock.calls[1][1].idempotencyKey);
});

test("employee toolbar offers execution actions without management actions",()=>{
  render(<TaskBatchToolbar selected={selected} allowedActions={["START","SUBMIT"]} onComplete={()=>{}} onClear={()=>{}} onBusyChange={()=>{}}/>);
  expect(screen.getByRole("option",{name:"Bắt đầu"})).toBeInTheDocument();
  expect(screen.queryByRole("option",{name:"Thêm người thực hiện"})).not.toBeInTheDocument();
  expect(screen.queryByRole("option",{name:"Xóa mềm"})).not.toBeInTheDocument();
});

test("đổi danh sách đã chọn vẫn có mức ưu tiên mặc định rõ ràng",()=>{
  const props={onComplete:()=>{},onClear:()=>{},onBusyChange:()=>{}};
  const view=render(<TaskBatchToolbar selected={selected} {...props}/>);
  fireEvent.change(screen.getByLabelText("Thao tác hàng loạt"),{target:{value:"PRIORITY"}});
  expect(screen.getByLabelText("Ưu tiên hàng loạt")).toHaveValue("MEDIUM");
  view.rerender(<TaskBatchToolbar selected={selected.slice(0,1)} {...props}/>);
  expect(screen.getByLabelText("Ưu tiên hàng loạt")).toHaveValue("MEDIUM");
  expect(screen.getByRole("button",{name:"Áp dụng"})).toBeEnabled();
});
