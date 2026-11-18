import {fireEvent,render,screen,waitFor} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {expect,test,vi} from "vitest";
import Files from "../pages/Files";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));

test("khôi phục theo tệp đã chọn và phiên bản có thật, không nhập mã",async()=>{
  request.mockImplementation((path:string)=>Promise.resolve({data:path.endsWith('/access')?{files:true}:path.endsWith('/attachments')?{items:[{id:'file-a',file_name:'Tài liệu.pdf',status:'AVAILABLE',version:2}]}:path.endsWith('/versions')?[{version:1,note:'Bản đầu'},{version:2,note:'Cập nhật'}]:{items:[]}}));
  render(<MemoryRouter initialEntries={['/files?taskId=task-a']}><Files/></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button',{name:'Lịch sử phiên bản'}));
  const versions=await screen.findByRole('combobox',{name:'Phiên bản cần khôi phục'});
  expect(screen.getByRole('button',{name:'Xác nhận khôi phục thành phiên bản mới'})).toBeDisabled();
  fireEvent.change(versions,{target:{value:'1'}});
  fireEvent.click(screen.getByRole('button',{name:'Xác nhận khôi phục thành phiên bản mới'}));
  await waitFor(()=>expect(request).toHaveBeenCalledWith('/files/file-a/restore',expect.objectContaining({method:'POST',body:{source_version:1,change_note:'Khôi phục từ ứng dụng'}})));
  expect(screen.queryByPlaceholderText('attachment id')).not.toBeInTheDocument();
});
