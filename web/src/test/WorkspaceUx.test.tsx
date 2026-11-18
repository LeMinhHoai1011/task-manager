import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, Link } from "react-router-dom";
import { beforeEach, expect, test, vi } from "vitest";
import Admin from "../pages/Admin";
import Calendar from "../pages/Calendar";
import Notes from "../pages/Notes";
import Settings from "../pages/Settings";
const { api, toast } = vi.hoisted(() => ({ api: vi.fn(), toast: vi.fn() }));
vi.mock("../api/client", async (original) => ({ ...await original<typeof import("../api/client")>(), apiFetch: api }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ user: { id: "me", email: "me@test.invalid" } }) }));
vi.mock("../auth/AccessContext", async original => ({...await original<typeof import("../auth/AccessContext")>(), useAccess: () => ({ projects: [], workspaces: [] }) }));
vi.mock("../components/ToastContext", () => ({ useToast: () => ({ showToast: toast }) }));
vi.mock("../components/ThemeContext", () => ({ useTheme: () => ({ theme: "light", setTheme: vi.fn() }) }));
vi.mock("../realtime/RealtimeContext", () => ({ useRealtimeEvent: () => { } }));
vi.mock("../components/ScopeFilters", () => ({ default: () => null }));
const account = { user_id: "other", username: "employee", email: "employee@test.invalid", display_name: "Nhân viên", status: "ACTIVE", locked: false, created_at: "2026-11-01T10:00:00Z" };
beforeEach(() => { api.mockReset(); toast.mockReset(); api.mockImplementation(async (path: string) => path === "/accounts/directory" ? { data: { content: [account], totalElements: 1, totalPages: 1 } } : path === "/me/tasks" ? { data: { content: [], last: true } } : path === "/tasks" ? { data: { items: [] } } : path === "/notifications/vapid/subscription-status" ? { data: { delivery_status: "DISABLED" } } : { data: [] }); });
function mount(component: React.ReactNode) { return render(<RouterProvider router={createMemoryRouter([{path:"*",element:component}])}/>); }
test("admin lists active accounts and only offers applicable actions", async () => { mount(<Admin />); expect(await screen.findByText("Nhân viên")).toBeInTheDocument(); expect(api).toHaveBeenCalledWith("/accounts/directory", expect.anything()); expect(screen.getByRole("button", { name: "Khóa" })).toBeEnabled(); expect(screen.queryByRole("button", { name: "Duyệt" })).not.toBeInTheDocument(); fireEvent.change(screen.getByLabelText("Tìm tài khoản"), { target: { value: "employee" } }); fireEvent.click(screen.getByRole("button", { name: "Tìm" })); await waitFor(() => expect(api).toHaveBeenCalledWith("/accounts/directory", expect.objectContaining({ query: expect.objectContaining({ q: "employee" }) }))); });
test("personal appointment submits title, details, UTC times and stable retry key", async () => { mount(<Calendar />); await waitFor(() => expect(api).toHaveBeenCalledWith("/reminders", expect.anything())); fireEvent.change(screen.getByLabelText("Tiêu đề"), { target: { value: "Hẹn khách" } }); fireEvent.change(screen.getByLabelText("Nội dung cần nhớ"), { target: { value: "Mang hồ sơ" } }); fireEvent.change(screen.getByLabelText("Thời điểm hẹn"), { target: { value: "2099-12-01T10:00" } }); fireEvent.change(screen.getByLabelText("Thời điểm nhắc"), { target: { value: "2099-12-01T09:00" } }); api.mockImplementation(async (path: string) => { if (path === "/reminders/personal")
    throw new Error("offline"); return { data: [] }; }); fireEvent.click(screen.getByRole("button", { name: "Lưu lịch hẹn & nhắc" })); await screen.findByText(/Nội dung nhập vẫn được giữ lại/); expect(screen.getByLabelText("Tiêu đề")).toHaveValue("Hẹn khách"); const first = api.mock.calls.find(([path]) => path === "/reminders/personal")![1]; expect(first.body).toMatchObject({ title: "Hẹn khách", description: "Mang hồ sơ", remind_at: new Date("2099-12-01T09:00").toISOString() }); fireEvent.click(screen.getByRole("button", { name: "Lưu lịch hẹn & nhắc" })); await waitFor(() => expect(api.mock.calls.filter(([path]) => path === "/reminders/personal")).toHaveLength(2)); expect(api.mock.calls.filter(([path]) => path === "/reminders/personal")[1][1].idempotencyKey).toBe(first.idempotencyKey); });
test("an older note save response cannot erase newer typing", async () => { const note = { id: "note", ownerUserId: "me", scope: "PERSONAL", title: "My note", content: "Original", version: 1, isPinned: false }; let resolveSave: (value: unknown) => void = () => { }; api.mockImplementation(async (path: string, opts: any) => { if (path === "/notes")
    return { data: [note] }; if (path === "/projects?limit=50")
    return { items: [] }; if (path === "/notes/note" && opts?.method === "PUT")
    return new Promise(resolve => { resolveSave = resolve; }); return { data: [] }; }); const view = mount(<Notes />); const editor = await screen.findByPlaceholderText(/Bắt đầu viết ghi chú/); fireEvent.change(editor, { target: { value: "First draft" } }); fireEvent.click(screen.getByRole("button", { name: "Lưu ngay" })); await waitFor(() => expect(api).toHaveBeenCalledWith("/notes/note", expect.objectContaining({ ifMatch: "1" }))); fireEvent.change(editor, { target: { value: "Newer draft" } }); await act(async () => resolveSave({ data: { ...note, content: "First draft", version: 2 } })); expect(editor).toHaveValue("Newer draft"); fireEvent.click(screen.getByRole("button", { name: "Lưu ngay" })); await waitFor(() => expect(api).toHaveBeenCalledWith("/notes/note", expect.objectContaining({ ifMatch: "2", body: expect.objectContaining({ content: "Newer draft" }) }))); await act(async () => resolveSave({ data: { ...note, content: "Newer draft", version: 3 } })); view.unmount(); });
test("quiet hours load from account and save to the real preference API", async () => { api.mockImplementation(async (path: string) => path === "/reminders/preferences" ? { data: { quiet_hours_enabled: true, quiet_hours_start: "21:00", quiet_hours_end: "06:00", timezone: "Asia/Bangkok" } } : path === "/notifications/vapid/subscription-status" ? { data: { delivery_status: "DISABLED" } } : { data: [] }); mount(<Settings />); const tab = screen.getAllByRole("button").find(button => button.textContent?.trim() === "Thông báo & Giờ nghỉ"); expect(tab).toBeTruthy(); fireEvent.click(tab!); const toggle = await screen.findByLabelText("Bật giờ yên lặng"); await waitFor(() => expect(toggle).toBeChecked()); fireEvent.click(screen.getByRole("button", { name: "Lưu giờ yên lặng" })); await waitFor(() => expect(api).toHaveBeenCalledWith("/reminders/preferences", expect.objectContaining({ method: "PUT", body: expect.objectContaining({ quiet_hours_start: "21:00", timezone: "Asia/Bangkok" }) }))); });


test("failed delete keeps an unsaved note draft available for saving",async()=>{
  const note={id:"note",ownerUserId:"me",scope:"PERSONAL",title:"My note",content:"Original",version:1,isPinned:false};
  api.mockImplementation(async(path:string,opts:any)=>{
    if(path==="/notes")return {data:[note]};
    if(path==="/projects?limit=50")return {items:[]};
    if(path==="/notes/note"&&opts?.method==="DELETE")throw new Error("offline");
    if(path==="/notes/note"&&opts?.method==="PUT")return {data:{...note,...opts.body,version:2}};
    return {data:[]};
  });
  mount(<Notes/>);const editor=await screen.findByPlaceholderText(/Bắt đầu viết ghi chú/);
  fireEvent.change(editor,{target:{value:"Draft to keep"}});fireEvent.click(screen.getByRole("button",{name:"Xóa ghi chú"}));
  await waitFor(()=>expect(toast).toHaveBeenCalledWith(expect.objectContaining({type:"error"})));
  fireEvent.click(screen.getByRole("button",{name:"Lưu ngay"}));
  await waitFor(()=>expect(api).toHaveBeenCalledWith("/notes/note",expect.objectContaining({method:"PUT",body:expect.objectContaining({content:"Draft to keep"})})));
  expect(editor).toHaveValue("Draft to keep");
});


test("navigation waits for note persistence and keeps the draft when saving fails",async()=>{
  const note={id:"note",ownerUserId:"me",scope:"PERSONAL",title:"My note",content:"Original",version:1,isPinned:false};let fail=true;
  api.mockImplementation(async(path:string,opts:any)=>{
    if(path==="/notes")return {data:[note]};if(path==="/projects?limit=50")return {items:[]};
    if(path==="/notes/note"&&opts?.method==="PUT"){if(fail)throw new Error("offline");return {data:{...note,...opts.body,version:2}};}
    return {data:[]};
  });
  const router=createMemoryRouter([{path:"/notes",element:<><Link to="/tasks">Go to tasks</Link><Notes/></>},{path:"/tasks",element:<p>Tasks destination</p>}],{initialEntries:["/notes"]});
  render(<RouterProvider router={router}/>);const editor=await screen.findByPlaceholderText(/Bắt đầu viết ghi chú/);
  fireEvent.change(editor,{target:{value:"Draft that must survive"}});fireEvent.click(screen.getByText("Go to tasks"));
  await screen.findByRole("alertdialog",{name:"Ghi chú chưa lưu"});expect(router.state.location.pathname).toBe("/notes");
  fireEvent.click(screen.getByRole("button",{name:"Lưu và rời trang"}));await waitFor(()=>expect(toast).toHaveBeenCalledWith(expect.objectContaining({type:"error"})));
  expect(editor).toHaveValue("Draft that must survive");expect(router.state.location.pathname).toBe("/notes");
  await waitFor(()=>expect(screen.getByRole("button",{name:"Lưu và rời trang"})).toBeEnabled());fail=false;fireEvent.click(screen.getByRole("button",{name:"Lưu và rời trang"}));
  await screen.findByText("Tasks destination");expect(router.state.location.pathname).toBe("/tasks");
});

test("all-day tasks stay on their original date across browser timezones",async()=>{
  api.mockImplementation(async(path:string)=>path==="/calendar"?{data:[{task_id:"all-day",scope:"PERSONAL",title:"All-day original date",due_at:"2026-11-15T23:59:59.999Z",due_is_date_only:true,tz:"UTC",due_date_only:"2026-11-15"}]}:path==="/me/tasks"?{data:{content:[],last:true}}:path==="/tasks"?{data:{items:[]}}:{data:[]});
  mount(<Calendar/>);await waitFor(()=>expect(api).toHaveBeenCalledWith("/calendar",expect.anything()));
  fireEvent.change(screen.getByLabelText("Ngày xem"),{target:{value:"2026-11-15"}});
  expect(await screen.findByRole("link",{name:/All-day original date/})).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Xem ngày 2026-11-16"}));
  expect(screen.queryByRole("link",{name:/All-day original date/})).not.toBeInTheDocument();
});
