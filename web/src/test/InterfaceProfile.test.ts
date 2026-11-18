import {expect,test} from "vitest";
import {interfaceProfile,type WorkspaceAccess,type ProjectAccess} from "../auth/AccessContext";

const personal:WorkspaceAccess={id:"personal",name:"Cá nhân",type:"PERSONAL",role:"OWNER",status:"ACTIVE",canManage:true,canCreateProject:true};
const project=(role:string):ProjectAccess=>({id:"project",name:"Team project",role,status:"ACTIVE",canManage:role==="MANAGER",canCreateTask:role==="MANAGER",canLogProgress:role!=="GUEST",metadata:role==="MANAGER",assignment:role==="MANAGER"});
test("personal workspace ownership does not turn a regular user into a manager",()=>{
  expect(interfaceProfile({systemAdmin:false,workspaces:[personal],projects:[]}).home).toBe("/my-tasks");
});
test("live scopes select the admin, manager, employee and guest interfaces",()=>{
  expect(interfaceProfile({systemAdmin:true,workspaces:[personal],projects:[]}).home).toBe("/admin");
  expect(interfaceProfile({systemAdmin:false,workspaces:[personal],projects:[project("MANAGER")]}).home).toBe("/dashboard");
  expect(interfaceProfile({systemAdmin:false,workspaces:[personal],projects:[project("MEMBER")]}).home).toBe("/tasks");
  expect(interfaceProfile({systemAdmin:false,workspaces:[personal],projects:[project("GUEST")]}).home).toBe("/projects");
});
