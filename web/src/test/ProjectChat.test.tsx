import {act,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {beforeEach,expect,test,vi} from "vitest";
import ProjectChat from "../chat/ProjectChat";
import {ChatDraftProvider,useChatDraft} from "../chat/ChatDraftContext";
import {mergeMessages,type ChatMessage} from "../chat/useProjectChat";
import {TestRealtimeProvider} from "../realtime/RealtimeContext";

const {request,identity}=vi.hoisted(()=>({request:vi.fn(),identity:{id:"me"}}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
vi.mock("../auth/AuthContext",()=>({useAuth:()=>({user:{id:identity.id}})}));
vi.mock("../auth/AccessContext",async original=>({...await original<typeof import("../auth/AccessContext")>(),useAccess:()=>({loading:false,projects:[{id:"project-a",name:"Dự án A",role:"MANAGER"},{id:"project-b",name:"Dự án B",role:"MEMBER"}]})}));
const permission={read:true,write:true,moderate:true};
const noop=()=>{};
let rows:ChatMessage[],revision:number,failSend:boolean,unreadCount:number;
function message(id:string,seq:number,body:string,owner="me"):ChatMessage{return {id,seq,changed_seq:seq,sender_id:owner,sender_name:owner==="me"?"Nguyễn An":"Trần Bình",sender_username:owner,client_message_id:`client-${id}`,body,deleted:false,created_at:"2026-11-18T09:00:00Z",linked_task:null,task_unavailable:false};}
function page(items=rows){return {data:{items,revision,next_before:null,next_after:revision,has_more:false,access:permission,unread:{count:unreadCount,last_read_seq:0}}};}
function wrapper(project="project-a",event:any=null){return <MemoryRouter><TestRealtimeProvider lastEvent={event}><ChatDraftProvider><ProjectChat key={project} projectId={project} onUnread={noop}/></ChatDraftProvider></TestRealtimeProvider></MemoryRouter>;}
beforeEach(()=>{
  identity.id="me";rows=[];revision=0;failSend=false;unreadCount=0;request.mockReset();
  request.mockImplementation((path:string,opts:any)=>{
    if(path==="/tasks")return Promise.resolve({data:{items:[{id:"task-a",title:"Công việc liên quan",project_id:"project-a"}]}});
    if(opts?.method==="POST"){
      if(failSend){failSend=false;return Promise.reject({uiMessage:"Mất mạng. Thử lại tin nhắn."});}
      const saved={...message("saved",++revision,opts.body.body),client_message_id:opts.body.client_message_id,linked_task:opts.body.linked_task_id?{id:opts.body.linked_task_id,title:"Công việc liên quan",state:"TODO"}:null};rows.push(saved);return Promise.resolve({data:saved});
    }
    if(opts?.method==="DELETE"){rows=rows.map(m=>({...m,deleted:true,body:null,changed_seq:++revision}));return Promise.resolve({data:rows[0]});}
    if(opts?.method==="PUT")return Promise.resolve({data:{count:0,last_read_seq:opts.body.last_read_seq}});
    return Promise.resolve(page(opts?.query?.after!=null?rows.filter(m=>m.changed_seq>opts.query.after):rows));
  });
});

test("đánh dấu tin chưa đọc và gom các tin liên tiếp cùng người gửi", async()=>{
  rows=[message("one",1,"Đã xem bản kế hoạch","peer"),message("two",2,"Mình sẽ cập nhật tiến độ","peer")];
  revision=2;unreadCount=2;
  render(wrapper());
  expect(await screen.findByText("Tin chưa đọc")).toBeInTheDocument();
  const messages=screen.getAllByRole("article",{name:"Tin của Trần Bình"});
  expect(messages[0]).toHaveClass("has-continuation");
  expect(messages[1]).toHaveClass("is-continued");
  expect(screen.getAllByText("Trần Bình")).toHaveLength(1);
});

test("gửi thất bại giữ nội dung và thử lại cùng định danh",async()=>{
  failSend=true;render(wrapper());
  fireEvent.change(await screen.findByRole("textbox",{name:"Tin nhắn cho dự án"}),{target:{value:"Tin nhắn tiếng Việt 😀"}});
  fireEvent.click(screen.getByRole("button",{name:"Gửi tin nhắn"}));
  fireEvent.click(await screen.findByRole("button",{name:"Thử lại tin nhắn"}));
  await screen.findByText("Đã gửi");
  const sends=request.mock.calls.filter(([,opts])=>opts?.method==="POST");
  expect(sends).toHaveLength(2);expect(sends[0][1].body).toEqual(sends[1][1].body);
  expect(screen.getAllByText("Tin nhắn tiếng Việt 😀")).toHaveLength(1);
});

test("chọn công việc theo tên và gửi liên kết trong đúng dự án",async()=>{
  render(wrapper());await screen.findByRole("textbox",{name:"Tin nhắn cho dự án"});
  fireEvent.click(screen.getByRole("button",{name:"Liên kết công việc"}));
  fireEvent.click(screen.getByRole("button",{name:"Công việc liên quan"}));
  fireEvent.click(await screen.findByRole("option",{name:/Công việc liên quan/}));
  fireEvent.change(screen.getByRole("textbox",{name:"Tin nhắn cho dự án"}),{target:{value:"Mời kiểm tra đầu việc này"}});
  fireEvent.click(screen.getByRole("button",{name:"Gửi tin nhắn"}));
  expect(await screen.findByRole("link",{name:/Công việc liên quan/})).toHaveAttribute("href","/tasks/task-a");
  expect(request.mock.calls.find(([,opts])=>opts?.method==="POST")?.[1].body.linked_task_id).toBe("task-a");
});

test("xóa tin chính mình cần xác nhận và gửi đúng phiên bản",async()=>{
  rows=[message("one",1,"Nội dung gửi nhầm")];revision=1;
  render(wrapper());fireEvent.click(await screen.findByRole("button",{name:/Xóa tin của Nguyễn An/}));
  expect(request.mock.calls.some(([,opts])=>opts?.method==="DELETE")).toBe(false);
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận xóa tin"}));
  await screen.findByText("Tin nhắn đã được xóa");expect(screen.queryByText("Nội dung gửi nhầm")).not.toBeInTheDocument();
  expect(request).toHaveBeenCalledWith("/projects/project-a/messages/one",expect.objectContaining({method:"DELETE",ifMatch:"1"}));
});

test("sự kiện phòng khác không tải lại phòng đang xem và mở trang không tự đánh dấu đọc",async()=>{
  rows=[message("one",1,"Tin của người khác","other")];revision=1;
  const view=render(wrapper());await screen.findByText("Tin của người khác");
  await waitFor(()=>expect(screen.queryByText("Đang tải tin nhắn…")).not.toBeInTheDocument());
  const calls=request.mock.calls.length;
  view.rerender(wrapper("project-a",{kind:"CHAT_MESSAGE_CREATED",scopeType:"CHAT",scopeId:"project-b",seq:1}));
  expect(request.mock.calls).toHaveLength(calls);
  expect(request.mock.calls.some(([path])=>path.endsWith("/chat/read"))).toBe(false);
});

test("phản hồi cũ không hiện trong dự án mới",async()=>{
  let finish!:(value:unknown)=>void;
  request.mockImplementation((path:string)=>path.includes("project-a")?new Promise(resolve=>{finish=resolve;}):Promise.resolve(page([message("new",1,"Nội dung dự án mới")])));
  const view=render(wrapper());view.rerender(wrapper("project-b"));
  await screen.findByText("Nội dung dự án mới");
  await act(async()=>finish(page([message("old",1,"Nội dung dự án cũ")])));
  expect(screen.queryByText("Nội dung dự án cũ")).not.toBeInTheDocument();
});

test("tin đã xóa không bị bản lịch sử cũ ghi đè",()=>{
  const live=message("one",1,"Nội dung cũ"),deleted={...live,changed_seq:2,deleted:true,body:null};
  expect(mergeMessages([deleted],[live])[0].body).toBeNull();
  expect(mergeMessages([live],[deleted,deleted])).toHaveLength(1);
});

test("bản nháp tách dự án và không đi sang tài khoản khác",()=>{
  function Draft({project}:{project:string}){const {draft,update}=useChatDraft(project);return <input aria-label="Bản nháp" value={draft.body} onChange={e=>update(old=>({...old,body:e.target.value}))}/>;}
  const draftView=(project:string)=><ChatDraftProvider><Draft project={project}/></ChatDraftProvider>;
  const view=render(draftView("project-a"));
  fireEvent.change(screen.getByLabelText("Bản nháp"),{target:{value:"Nội dung của tài khoản A"}});
  view.rerender(draftView("project-b"));expect(screen.getByLabelText("Bản nháp")).toHaveValue("");
  view.rerender(draftView("project-a"));expect(screen.getByLabelText("Bản nháp")).toHaveValue("Nội dung của tài khoản A");
  identity.id="different-account";view.rerender(draftView("project-a"));expect(screen.getByLabelText("Bản nháp")).toHaveValue("");
});

test("nội dung HTML là văn bản và Enter trong bộ gõ không gửi tin",async()=>{
  const unsafe='<img src=x onerror="alert(1)">';rows=[message("one",1,unsafe)];revision=1;
  const view=render(wrapper());await screen.findByText(unsafe);
  expect(view.container.querySelector("img")).toBeNull();
  const input=screen.getByRole("textbox",{name:"Tin nhắn cho dự án"});
  fireEvent.change(input,{target:{value:"Đang gõ tiếng Việt"}});
  fireEvent.keyDown(input,{key:"Enter",isComposing:true});fireEvent.keyDown(input,{key:"Enter",keyCode:229});
  expect(request.mock.calls.some(([,opts])=>opts?.method==="POST")).toBe(false);
  expect(input).toHaveValue("Đang gõ tiếng Việt");
});

test("mất quyền xóa nội dung và bỏ phản hồi lịch sử đang chạy",async()=>{
  rows=[message("one",10,"Nội dung đang xem")];revision=10;
  request.mockImplementation((_path:string,opts:any)=>Promise.resolve({...page(opts?.query?.after!=null?[]:rows),data:{...page(opts?.query?.after!=null?[]:rows).data,next_before:1}}));
  const view=render(wrapper());await screen.findByText("Nội dung đang xem");
  await waitFor(()=>expect(request.mock.calls.length).toBeGreaterThanOrEqual(2));
  let finish!:(value:unknown)=>void;
  request.mockImplementation((_path:string,opts:any)=>opts?.query?.before?new Promise(resolve=>{finish=resolve;}):Promise.reject({status:404}));
  fireEvent.click(screen.getByRole("button",{name:"Xem tin trước"}));
  view.rerender(wrapper("project-a",{kind:"CHAT_MESSAGE_CREATED",scopeType:"CHAT",scopeId:"project-a",seq:1}));
  await screen.findByText("Bạn không còn quyền xem trao đổi này. Nội dung đã được đóng.");
  await act(async()=>finish(page([message("old",1,"Phản hồi cũ phải bị bỏ")])));
  expect(screen.queryByText("Nội dung đang xem")).not.toBeInTheDocument();
  expect(screen.queryByText("Phản hồi cũ phải bị bỏ")).not.toBeInTheDocument();
});

test("lỗi liên kết không đóng phòng, sửa bản gửi giữ định danh nếu nội dung không đổi",async()=>{
  const implementation=request.getMockImplementation()!;
  let rejected=false;
  request.mockImplementation((path:string,opts:any)=>{
    if(opts?.method==="POST"&&!rejected){rejected=true;return Promise.reject({status:404,uiMessage:"Công việc không còn khả dụng"});}
    return implementation(path,opts);
  });
  render(wrapper());const input=await screen.findByRole("textbox",{name:"Tin nhắn cho dự án"});
  fireEvent.change(input,{target:{value:"Nội dung vẫn được giữ"}});fireEvent.click(screen.getByRole("button",{name:"Gửi tin nhắn"}));
  fireEvent.click(await screen.findByRole("button",{name:"Sửa bản gửi"}));
  expect(input).toHaveValue("Nội dung vẫn được giữ");
  expect(screen.queryByText("Bạn không còn quyền xem trao đổi này. Nội dung đã được đóng.")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Gửi tin nhắn"}));await screen.findByText("Đã gửi");
  const sends=request.mock.calls.filter(([,opts])=>opts?.method==="POST");expect(sends[0][1].body.client_message_id).toBe(sends[1][1].body.client_message_id);
});
