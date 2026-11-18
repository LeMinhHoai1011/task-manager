import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.smarttask.SmartTaskApplication;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.project.repo.*;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.security.GrantService;
import vn.smarttask.task.repo.*;
import vn.smarttask.task.service.*;
import vn.smarttask.workspace.repo.*;
import vn.smarttask.workspace.service.WorkspaceService;

/** Explicit local-only fixtures. Password comes from the caller, never application startup. */
public class SeedRoleAccounts {
  static final String MARKER="LOCAL ROLE TEST — ";
  public static void main(String[] args) throws Exception {
    String url=System.getenv("SPRING_DATASOURCE_URL"), password=System.getenv("SMARTTASK_LOCAL_DEMO_PASSWORD");
    if(!"1".equals(System.getenv("SMARTTASK_LOCAL_DEMO_CONFIRM"))||url==null||password==null||password.length()<8)
      throw new IllegalStateException("Use seed-local-demo.ps1 -RoleAccounts with an explicit test password");
    URI db=URI.create(url.substring("jdbc:".length()));
    if(!"postgresql".equals(db.getScheme())||!Set.of("127.0.0.1","localhost").contains(db.getHost())
        ||db.getPort()!=Integer.parseInt(System.getenv("SMARTTASK_LOCAL_DEMO_PORT")))
      throw new IllegalStateException("Only the verified local dev database is allowed");
    try(var context=new SpringApplication(SmartTaskApplication.class).run("--server.address=127.0.0.1","--server.port=0",
        "--smarttask.workers.enabled=false","--smarttask.bootstrap.admin-username=","--spring.main.banner-mode=off")) {
      var fixture=new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status->seed(context,password));
      Path output=Path.of(System.getenv("SMARTTASK_LOCAL_DEMO_OUTPUT"));Files.createDirectories(output.getParent());
      context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValue(output.toFile(),fixture);
      System.out.println("LOCAL_ROLE_ACCOUNTS_READY: admin, manager, employee, user, guest, workspace_admin");
    }
  }
  static Map<String,Object> seed(ConfigurableApplicationContext context,String password) {
    var jdbc=context.getBean(JdbcTemplate.class);
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended('smarttask.local.role.accounts.v1',0))",Object.class);
    var users=context.getBean(AppUserRepository.class);var identity=context.getBean(IdentityService.class);
    PasswordEncoder encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    var accounts=new LinkedHashMap<String,AppUser>();
    for(String username:List.of("admin","manager","employee","user","guest","workspace_admin")) {
      String email=username+"@roles.smarttask.local.test";
      var existing=users.findByUsernameIgnoreCase(username);AppUser account;
      if(existing.isPresent()) {
        account=existing.get();
        if(!email.equals(account.getEmail())||!(MARKER+username).equals(account.getDisplayName())
            ||!encoder.matches(password,account.getPasswordHash())||!account.isActive()||!"ACTIVE".equals(account.getStatus())||account.isLocked())
          throw new IllegalStateException("Account collision; no existing account or password was overwritten: "+username);
      } else {
        if(users.findByEmailIgnoreCase(email).isPresent())throw new IllegalStateException("Test email already used: "+username);
        account=identity.createByAdmin(null,username,email,password,MARKER+username);
      }
      accounts.put(username,account);
    }
    context.getBean(GrantService.class).grantBootstrap(accounts.get("admin").getId(),GrantService.SYSTEM_ADMIN);
    context.getBean(GrantService.class).grantBootstrap(accounts.get("admin").getId(),"ACCOUNT_APPROVE_GLOBAL");
    var workspaces=context.getBean(WorkspaceService.class);var wsMembers=context.getBean(WorkspaceMembershipRepository.class);
    var projects=context.getBean(ProjectService.class);var projectRepo=context.getBean(ProjectRepository.class);
    var members=context.getBean(ProjectMembershipRepository.class);
    UUID manager=accounts.get("manager").getId();
    String workspaceName=MARKER+"Phân cấp giao diện";
    var workspace=workspaces.listForUser(manager).stream().filter(w->workspaceName.equals(w.getName())).findFirst()
        .orElseGet(()->workspaces.createTeam(manager,workspaceName));
    if(!manager.equals(workspace.getOwnerUserId())||!"ACTIVE".equals(workspace.getStatus()))throw new IllegalStateException("Role-test workspace changed");
    for(var entry:Map.of("employee","MEMBER","guest","GUEST","workspace_admin","ADMIN").entrySet()) {
      var account=accounts.get(entry.getKey());
      var membership=wsMembers.findByWorkspaceIdAndUserId(workspace.getId(),account.getId());
      if(membership.isEmpty()) {
        var invitation=workspaces.invite(manager,workspace.getId(),account.getEmail(),entry.getValue(),7);
        workspaces.acceptInvitation(account.getId(),invitation.getId());
      } else if(!entry.getValue().equals(membership.get().getRole())||!"ACTIVE".equals(membership.get().getStatus()))
        throw new IllegalStateException("Role-test workspace membership changed");
    }
    String projectName=MARKER+"Quy trình giao việc và duyệt";
    var project=projects.listForUser(manager,workspace.getId()).stream().filter(p->projectName.equals(p.getName())).findFirst()
        .orElseGet(()->projects.create(manager,workspace.getId(),projectName,"Dữ liệu kiểm thử vai trò local"));
    project=projectRepo.findById(project.getId()).orElseThrow();
    if("PLANNING".equals(project.getStatus())){project.setStatus("ACTIVE");project=projectRepo.save(project);}
    if(!"ACTIVE".equals(project.getStatus()))throw new IllegalStateException("Role-test project is not active");
    for(var entry:Map.of("employee","MEMBER","guest","GUEST").entrySet()) {
      UUID target=accounts.get(entry.getKey()).getId();var membership=members.findMembership(project.getId(),target);
      if(membership.isEmpty())projects.addMember(manager,project.getId(),target,entry.getValue());
      else if(!entry.getValue().equals(membership.get().getRole()))throw new IllegalStateException("Role-test project membership changed");
    }
    if(!project.isApprovalEnabled())projects.approvalToggle(manager,project.getId(),true,project.getVersion());
    var tasks=context.getBean(TaskService.class);var taskRepo=context.getBean(TaskRepository.class);
    var assignments=context.getBean(TaskAssignmentRepository.class);var taskInfo=new ArrayList<Map<String,Object>>();
    for(String username:List.of("employee","guest")) {
      String title=MARKER+("employee".equals(username)?"Nhân viên thực hiện và nộp duyệt":"Khách theo dõi, chỉ đọc");
      var task=taskRepo.findByProjectId(project.getId()).stream().filter(t->title.equals(t.getTitle())&&t.getDeletedAt()==null).findFirst().orElse(null);
      if(task==null) {
        task=tasks.create(manager,project.getId(),title,"Kiểm tra đúng quyền trên từng giao diện", "MEDIUM",null);
        tasks.addChecklist(manager,task.getId(),"Đọc yêu cầu và cập nhật kết quả");
      }
      UUID assignee=accounts.get(username).getId();
      if(assignments.findByTaskIdAndUserId(task.getId(),assignee).isEmpty())
        tasks.assign(manager,task.getId(),assignee,taskRepo.findById(task.getId()).orElseThrow().getVersion());
      taskInfo.add(Map.of("id",task.getId(),"assignee",username));
    }
    var personal=context.getBean(PersonalTaskService.class);
    var output=new ArrayList<Map<String,Object>>();
    for(var entry:accounts.entrySet()) {
      if(personal.list(entry.getValue().getId(),0,1).isEmpty())personal.create(entry.getValue().getId(),MARKER+"Công việc cá nhân của "+entry.getKey());
      output.add(Map.of("username",entry.getKey(),"id",entry.getValue().getId(),"email",entry.getValue().getEmail()));
    }
    return Map.of("localOnly",true,"accounts",output,"workspaceId",workspace.getId(),"projectId",project.getId(),"tasks",taskInfo);
  }
}
