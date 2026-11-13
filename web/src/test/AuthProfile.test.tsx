import {act,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {beforeEach,expect,test,vi} from "vitest";
import {AuthProvider,useAuth} from "../auth/AuthContext";
const {request}=vi.hoisted(()=>({request:vi.fn()}));
vi.mock("../api/client",async original=>({...await original<typeof import("../api/client")>(),apiFetch:request}));
beforeEach(()=>{localStorage.clear();request.mockReset();});
function Probe(){const auth=useAuth();return <><p>{auth.user?.id??"no-user"}</p><button onClick={()=>void auth.login("demo_owner","test-password")}>Login probe</button><button onClick={()=>void auth.logout()}>Logout probe</button></>;}
test("successful login restores the same display profile after reload and clears it on logout",async()=>{
  request.mockResolvedValue({data:{access_token:"test-session",refresh_token:"test-refresh",user_id:"demo-owner-id",role:"USER",must_change_password:false}});
  const first=render(<AuthProvider><Probe/></AuthProvider>);
  fireEvent.click(screen.getByRole("button",{name:"Login probe"}));
  await screen.findByText("demo-owner-id");
  first.unmount();
  render(<AuthProvider><Probe/></AuthProvider>);
  expect(screen.getByText("demo-owner-id")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"Logout probe"}));
  await screen.findByText("no-user");
  await waitFor(()=>expect(localStorage.getItem("tm.session_user")).toBeNull());
  expect(localStorage.getItem("tm.access_token")).toBeNull();
});
test("expired sessions clear the cached profile and malformed cache cannot crash the app",async()=>{
  localStorage.setItem("tm.access_token","test-session");
  localStorage.setItem("tm.session_user",JSON.stringify({id:"old-user",email:"demo",role:"USER",mustChangePassword:false}));
  const view=render(<AuthProvider><Probe/></AuthProvider>);
  expect(screen.getByText("old-user")).toBeInTheDocument();
  await act(async()=>window.dispatchEvent(new Event("tm:session-expired")));
  expect(screen.getByText("no-user")).toBeInTheDocument();
  expect(localStorage.getItem("tm.session_user")).toBeNull();
  view.unmount();
  localStorage.setItem("tm.session_user","not-json");
  render(<AuthProvider><Probe/></AuthProvider>);
  expect(screen.getByText("no-user")).toBeInTheDocument();
});
