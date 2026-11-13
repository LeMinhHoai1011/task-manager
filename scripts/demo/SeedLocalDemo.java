import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.smarttask.SmartTaskApplication;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.task.entity.Task;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.task.service.PersonalTaskService;
import vn.smarttask.task.service.TaskService;
import vn.smarttask.workspace.entity.Workspace;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.service.WorkspaceService;

/** Explicit local-only fixture command. Never loaded by the application at startup. */
public class SeedLocalDemo {
  static final String PASSWORD = "DemoLocal@2026!";
  static final String WORKSPACE = "[DEMO LOCAL] Kiểm thử 4 vai trò";
  static final String PROJECT = "[DEMO LOCAL] Xây dựng website nhóm";
  static final String MARKER = "DEMO LOCAL — ";

  public static void main(String[] args) throws Exception {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (!"1".equals(System.getenv("SMARTTASK_LOCAL_DEMO_CONFIRM")) || url == null) {
      throw new IllegalStateException("Run scripts/seed-local-demo.ps1 explicitly against the local dev container");
    }
    URI db = URI.create(url.substring("jdbc:".length()));
    if (!"postgresql".equals(db.getScheme()) || !Set.of("127.0.0.1", "localhost").contains(db.getHost())
        || db.getPort() != Integer.parseInt(System.getenv("SMARTTASK_LOCAL_DEMO_PORT"))) {
      throw new IllegalStateException("Only the verified loopback dev database is allowed");
    }
    try (ConfigurableApplicationContext context = new SpringApplication(SmartTaskApplication.class).run(
        "--server.address=127.0.0.1", "--server.port=0", "--smarttask.workers.enabled=false",
        "--smarttask.bootstrap.admin-username=", "--spring.datasource.hikari.maximum-pool-size=2",
        "--spring.main.banner-mode=off")) {
      var fixture = new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
          .execute(status -> seed(context));
      Path output = Path.of(System.getenv("SMARTTASK_LOCAL_DEMO_OUTPUT"));
      Files.createDirectories(output.getParent());
      context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValue(output.toFile(), fixture);
      System.out.println("LOCAL_DEMO_READY: 4 accounts; isolated demo workspace/project; fixtures: " + output);
    }
  }

  static Map<String,Object> seed(ConfigurableApplicationContext context) {
    var jdbc = context.getBean(JdbcTemplate.class);
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended('smarttask.local.demo.seed.v1', 0))", Object.class);
    var identity = context.getBean(IdentityService.class);
    var users = context.getBean(AppUserRepository.class);
    PasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    var workspaces = context.getBean(WorkspaceService.class);
    var wsMembers = context.getBean(WorkspaceMembershipRepository.class);
    var projects = context.getBean(ProjectService.class);
    var projectRepo = context.getBean(ProjectRepository.class);
    var projectMembers = context.getBean(ProjectMembershipRepository.class);
    var tasks = context.getBean(TaskService.class);
    var taskRepo = context.getBean(TaskRepository.class);
    var assignments = context.getBean(TaskAssignmentRepository.class);
    var personal = context.getBean(PersonalTaskService.class);

    String[] roles = {"OWNER", "ADMIN", "MEMBER", "GUEST"};
    var accounts = new LinkedHashMap<String,AppUser>();
    for (String role : roles) {
      String suffix = role.toLowerCase(Locale.ROOT);
      String username = "demo_" + suffix, email = "demo." + suffix + "@smarttask.local.test";
      AppUser user = users.findByUsernameIgnoreCase(username).orElse(null);
      if (user != null) {
        if (!email.equals(user.getEmail()) || !(MARKER + role).equals(user.getDisplayName())
            || !encoder.matches(PASSWORD, user.getPasswordHash()) || !user.isActive() || !"ACTIVE".equals(user.getStatus()) || user.isLocked()) {
          throw new IllegalStateException("Existing account " + username + " differs from demo fixture; nothing will be reset or overwritten");
        }
      } else {
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new IllegalStateException("Demo email already belongs to another account");
        user = identity.createByAdmin(null, username, email, PASSWORD, MARKER + role);
      }
      accounts.put(role, user);
    }
    UUID owner = accounts.get("OWNER").getId();
    Workspace workspace = workspaces.listForUser(owner).stream().filter(w -> WORKSPACE.equals(w.getName()))
        .findFirst().orElseGet(() -> workspaces.createTeam(owner, WORKSPACE));
    if (!owner.equals(workspace.getOwnerUserId()) || !"ACTIVE".equals(workspace.getStatus())) {
      throw new IllegalStateException("Existing demo workspace changed; refusing to overwrite it");
    }
    for (String role : List.of("ADMIN", "MEMBER", "GUEST")) {
      AppUser user = accounts.get(role);
      var membership = wsMembers.findByWorkspaceIdAndUserId(workspace.getId(), user.getId());
      if (membership.isEmpty()) {
        var invitation = workspaces.invite(owner, workspace.getId(), user.getEmail(), role, 7);
        workspaces.acceptInvitation(user.getId(), invitation.getId());
      } else if (!"ACTIVE".equals(membership.get().getStatus()) || !role.equals(membership.get().getRole())) {
        throw new IllegalStateException("Demo workspace role changed for " + user.getUsername());
      }
    }
    Project project = projects.listForUser(owner, workspace.getId()).stream().filter(p -> PROJECT.equals(p.getName()))
        .findFirst().orElseGet(() -> projects.create(owner, workspace.getId(), PROJECT,
            "Dữ liệu kiểm thử local: việc nhỏ/lớn, phân công, subtask và quyền Owner/Admin/Member/Guest."));
    if ("PLANNING".equals(project.getStatus())) {
      var current = projectRepo.findById(project.getId()).orElseThrow();
      project = projects.activate(owner, project.getId(), current.getVersion());
    }
    if (!"ACTIVE".equals(project.getStatus())) throw new IllegalStateException("Demo project is not active");
    for (String role : List.of("ADMIN", "MEMBER", "GUEST")) {
      String projectRole = "ADMIN".equals(role) ? "MANAGER" : role;
      var membership = projectMembers.findByProjectIdAndUserId(project.getId(), accounts.get(role).getId());
      if (membership.isEmpty()) projects.addMember(owner, project.getId(), accounts.get(role).getId(), projectRole);
      else if (!"ACTIVE".equals(membership.get().getStatus()) || !projectRole.equals(membership.get().getRole()))
        throw new IllegalStateException("Demo project membership changed; refusing to overwrite it");
    }

    String[] titles = {"[DEMO] Việc nhỏ — kiểm tra nội dung trang chủ", "[DEMO] Việc nhỏ — rà soát giao diện mobile",
        "[DEMO] Việc lớn — xây dựng chức năng quản lý công việc", "[DEMO] Việc lớn — chuẩn bị nghiệm thu website"};
    String[] priorities = {"LOW", "MEDIUM", "HIGH", "URGENT"};
    int[] dueDays = {1, 2, 7, 12};
    var roots = new ArrayList<Task>();
    var rootInfo = new ArrayList<Map<String,Object>>();
    for (int i = 0; i < titles.length; i++) {
      final String title = titles[i];
      Task root = taskRepo.findByProjectId(project.getId()).stream()
          .filter(t -> t.getParentTaskId() == null && title.equals(t.getTitle())).findFirst().orElse(null);
      if (root == null) {
        root = tasks.create(owner, project.getId(), title,
            (i < 2 ? "Quy mô nhỏ: khoảng 30–60 phút. " : "Quy mô lớn: nhiều bước, theo dõi bằng các việc con. ")
                + "Dùng để kiểm tra danh sách, Kanban, Calendar, phân công và quyền truy cập.", priorities[i], null);
        root = tasks.setDeadline(owner, root.getId(), null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(dueDays[i]),
            "Asia/Bangkok", root.getVersion());
      }
      if (root.getDeletedAt() != null) throw new IllegalStateException("Demo task was deleted; it will not be restored automatically");
      roots.add(root);
      rootInfo.add(Map.of("id", root.getId(), "title", root.getTitle(), "size", i < 2 ? "SMALL" : "LARGE"));
    }
    for (int index : List.of(0, 2)) assignIfAbsent(tasks, taskRepo, assignments, owner, roots.get(index).getId(), accounts.get("MEMBER").getId());
    assignIfAbsent(tasks, taskRepo, assignments, owner, roots.get(0).getId(), accounts.get("GUEST").getId());
    var childInfo = new ArrayList<Map<String,Object>>();
    for (int index : List.of(2, 3)) {
      String[] children = index == 2 ? new String[]{"[DEMO] Phân tích luồng tạo và giao việc", "[DEMO] Kiểm thử lưu và tải lại công việc"}
          : new String[]{"[DEMO] Lập checklist nghiệm thu", "[DEMO] Tổng hợp kết quả kiểm thử"};
      for (String title : children) {
        Task child = taskRepo.findByParentTaskId(roots.get(index).getId()).stream().filter(t -> title.equals(t.getTitle())).findFirst().orElse(null);
        if (child == null) child = tasks.createSubtask(owner, roots.get(index).getId(), title, "Tự kế thừa dự án từ việc cha; không chọn lại Project.");
        if (index == 2) assignIfAbsent(tasks, taskRepo, assignments, owner, child.getId(), accounts.get("MEMBER").getId());
        childInfo.add(Map.of("id", child.getId(), "parentId", roots.get(index).getId(), "projectId", child.getProjectId(), "title", title));
      }
    }
    var credentials = new ArrayList<Map<String,Object>>();
    var personalInfo = new LinkedHashMap<String,List<Map<String,Object>>>();
    for (String role : roles) {
      AppUser user = accounts.get(role);
      credentials.add(Map.of("username", user.getUsername(), "password", PASSWORD, "userId", user.getId(),
          "workspaceRole", role, "projectRole", Set.of("OWNER", "ADMIN").contains(role) ? "MANAGER" : role));
      var ownTasks = new ArrayList<Map<String,Object>>();
      String[] personalTitles = {"[DEMO cá nhân] Việc nhỏ — đọc thông báo", "[DEMO cá nhân] Việc nhỏ — cập nhật kế hoạch hôm nay",
          "[DEMO cá nhân] Việc lớn — lập kế hoạch học tập", "[DEMO cá nhân] Việc lớn — hoàn tất báo cáo tuần"};
      for (int i = 0; i < personalTitles.length; i++) {
        final String title = personalTitles[i];
        Task task = taskRepo.findByOwnerUserIdAndScopeAndDeletedAtIsNull(user.getId(), "PERSONAL", PageRequest.of(0, 100)).stream()
            .filter(t -> title.equals(t.getTitle())).findFirst().orElse(null);
        if (task == null) {
          task = personal.create(user.getId(), title);
          task = personal.edit(user.getId(), task.getId(), new PersonalTaskService.Edit(title,
              "Công việc cá nhân ngoài Project, chỉ tài khoản này được xem.", "DEMO LOCAL", priorities[i], null,
              OffsetDateTime.now(ZoneOffset.UTC).plusDays(i == 1 ? -1 : dueDays[i]), false, "Asia/Bangkok"), task.getVersion());
          if (i == 2) task = personal.start(user.getId(), task.getId(), task.getVersion());
          if (i == 3) task = personal.complete(user.getId(), task.getId(), true, task.getVersion());
        }
        ownTasks.add(Map.of("id", task.getId(), "title", title));
      }
      personalInfo.put(user.getUsername(), ownTasks);
    }
    return Map.of("localOnly", true, "database", System.getenv("SPRING_DATASOURCE_URL"), "web", "http://localhost:5173",
        "accounts", credentials, "workspace", Map.of("id", workspace.getId(), "name", WORKSPACE),
        "project", Map.of("id", project.getId(), "name", PROJECT), "tasks", rootInfo, "subtasks", childInfo, "personalTasks", personalInfo);
  }

  static void assignIfAbsent(TaskService tasks, TaskRepository repo, TaskAssignmentRepository assignments, UUID owner, UUID taskId, UUID user) {
    if (assignments.findByTaskIdAndUserId(taskId, user).isEmpty()) tasks.assign(owner, taskId, user, repo.findById(taskId).orElseThrow().getVersion());
  }
}
