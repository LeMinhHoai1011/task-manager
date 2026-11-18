import {render,screen} from "@testing-library/react";
import {MemoryRouter,Route,Routes} from "react-router-dom";
import {beforeEach,expect,test,vi} from "vitest";
import {RoleHome,RequireProjectManager} from "../App";
const {access}=vi.hoisted(()=>({access:{systemAdmin:false,loading:false,error:"",workspaces:[],projects:[] as import("../auth/AccessContext").ProjectAccess[]}}));
vi.mock("../auth/AccessContext",async original=>({...await original<typeof import("../auth/AccessContext")>(),useAccess:()=>access}));
beforeEach(()=>{access.systemAdmin=false;access.loading=false;access.error="";access.projects=[];});
function home(){render(<MemoryRouter initialEntries={["/"]}><Routes><Route path="/" element={<RoleHome/>}/><Route path="/admin" element={<p>Admin home</p>}/><Route path="/my-tasks" element={<p>Personal home</p>}/><Route path="/tasks" element={<p>Employee home</p>}/></Routes></MemoryRouter>);}
test("ordinary account opens its personal interface",async()=>{home();expect(await screen.findByText("Personal home")).toBeInTheDocument();});
test("system admin opens system administration",async()=>{access.systemAdmin=true;home();expect(await screen.findByText("Admin home")).toBeInTheDocument();});
test("employee opens assigned work",async()=>{access.projects=[{id:"p",role:"MEMBER"} as import("../auth/AccessContext").ProjectAccess];home();expect(await screen.findByText("Employee home")).toBeInTheDocument();});
test("direct project-role URL is blocked for non-managers",()=>{render(<RequireProjectManager><p>Role editor</p></RequireProjectManager>);expect(screen.getByRole("alert")).toBeInTheDocument();expect(screen.queryByText("Role editor")).not.toBeInTheDocument();});
test("project-role guard fails closed while access is loading",()=>{access.loading=true;render(<RequireProjectManager><p>Role editor</p></RequireProjectManager>);expect(screen.getByRole("status")).toBeInTheDocument();expect(screen.queryByText("Role editor")).not.toBeInTheDocument();});
