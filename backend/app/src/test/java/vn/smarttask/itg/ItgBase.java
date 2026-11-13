package vn.smarttask.itg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.repo.UserSessionRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.project.entity.Project;
import vn.smarttask.project.repo.ProjectMembershipRepository;
import vn.smarttask.project.repo.ProjectRepository;
import vn.smarttask.project.service.ProjectService;
import vn.smarttask.security.GrantService;
import vn.smarttask.task.repo.TaskAssignmentRepository;
import vn.smarttask.task.repo.TaskRepository;
import vn.smarttask.task.service.TaskService;
import vn.smarttask.workflow.repo.ApprovalDecisionRepository;
import vn.smarttask.workflow.repo.ApprovalRoundRepository;
import vn.smarttask.workflow.service.WorkflowService;
import vn.smarttask.workspace.entity.WorkspaceMembership;
import vn.smarttask.workspace.repo.WorkspaceMembershipRepository;
import vn.smarttask.workspace.repo.WorkspaceRepository;
import vn.smarttask.workspace.service.WorkspaceService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * F2.2 integration base: runs straight against the real PostgreSQL 16 at
 * localhost:5433 (scratch database {@code smarttask_itg}).
 *
 * <p>No Testcontainers (broken here: docker-java 3.3.x gets HTTP 400 from the
 * Docker Desktop proxy). Flyway migrates V1..V10 on context boot and
 * Hibernate runs with {@code ddl-auto=validate}, so booting this context
 * already proves migration + mapping alignment.
 *
 * <p>Hermetic fixtures: every test mints unique usernames/emails
 * ({@code itg_<n>_<rand>}) and its own workspace/project/task rows. Nothing
 * is rolled back; nothing is shared across test methods.
 *
 * <p>Rate-limit safety: every HTTP request carries a unique
 * {@code X-Forwarded-For} IP so the in-memory {@code RateLimiter} buckets
 * (AUTH 10/min, DEFAULT 100/min per key) never trigger across tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=${SMARTTASK_IT_DATABASE_URL:jdbc:postgresql://localhost:5433/smarttask_itg}",
    "spring.datasource.username=stm_test",
    "spring.datasource.password=stm_test_pw",
    "spring.datasource.hikari.maximum-pool-size=4",
    "spring.jpa.hibernate.ddl-auto=validate",
    "smarttask.bootstrap.admin-username=",
    "smarttask.self-registration=true",
    "smarttask.workers.enabled=false"})
public abstract class ItgBase {

  private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper om;
  @Autowired protected JdbcTemplate jdbc;

  @Autowired protected IdentityService identity;
  @Autowired protected WorkspaceService workspaces;
  @Autowired protected ProjectService projects;
  @Autowired protected TaskService tasks;
  @Autowired protected WorkflowService workflow;
  @Autowired protected GrantService grants;

  @Autowired protected AppUserRepository users;
  @Autowired protected UserSessionRepository sessions;
  @Autowired protected WorkspaceRepository wsRepo;
  @Autowired protected WorkspaceMembershipRepository wsMembers;
  @Autowired protected ProjectRepository projectRepo;
  @Autowired protected ProjectMembershipRepository projectMembers;
  @Autowired protected TaskRepository taskRepo;
  @Autowired protected TaskAssignmentRepository assignments;
  @Autowired protected vn.smarttask.files.repo.AttachmentRepository attachments;
  @Autowired protected ApprovalRoundRepository rounds;
  @Autowired protected ApprovalDecisionRepository decisions;

  protected static String tag() {
    return Long.toString(Math.abs(SEQ.incrementAndGet()), 36)
        + Long.toString(Math.abs(UUID.randomUUID().getLeastSignificantBits()), 36);
  }

  /** Unique client IP per request: keeps the in-memory rate limiter out of the way. */
  protected static String fwd() {
    long a = Math.abs(UUID.randomUUID().getLeastSignificantBits());
    return "10." + (a % 200 + 10) + "." + ((a / 251) % 250 + 1) + "." + ((a / 63001) % 250 + 1);
  }

  protected static String idem() {
    return UUID.randomUUID().toString();
  }

  // ---------------------------------------------------------------- fixtures

  protected AppUser activeUser(String prefix, String password) {
    String t = tag();
    return identity.createByAdmin(null, prefix + "_" + t, prefix + "_" + t + "@itg.io",
        password, prefix + " " + t);
  }

  protected String loginToken(String username, String password) {
    return identity.login(username, password, "itg", "test").accessToken();
  }

  protected UUID mkWorkspace(UUID ownerId, String prefix) {
    return workspaces.createTeam(ownerId, prefix + "-ws-" + tag()).getId();
  }

  protected void addWsMember(UUID wsId, UUID userId, String role) {
    wsMembers.save(new WorkspaceMembership(UUID.randomUUID(), wsId, userId, role));
  }

  /** Creates a project (creator becomes MANAGER) and flips it ACTIVE for workflow use. */
  protected Project mkActiveProject(UUID managerId, UUID wsId, String prefix) {
    Project p = projects.create(managerId, wsId, prefix + "-p-" + tag(), null);
    // create() returns a detached instance (assigned-UUID save() takes the merge
    // path, @PrePersist runs only on Hibernate's managed copy): re-fetch before
    // mutating + saving, otherwise created_at would be written back as NULL.
    Project fresh = projectRepo.findById(p.getId()).orElseThrow();
    fresh.setStatus("ACTIVE");
    return projectRepo.save(fresh);
  }

  protected void addProjectMember(UUID managerId, UUID projectId, UUID userId, String role) {
    projects.addMember(managerId, projectId, userId, role);
  }

  protected vn.smarttask.task.entity.Task mkTask(UUID actorId, UUID projectId, String title) {
    return tasks.create(actorId, projectId, title, null, "MEDIUM", null);
  }

  protected void enableApproval(UUID managerId, UUID projectId) {
    Project p = projectRepo.findById(projectId).orElseThrow();
    projects.approvalToggle(managerId, projectId, true, p.getVersion());
  }

  // ---------------------------------------------------------------- http

  private MockHttpServletRequestBuilder withCommon(MockHttpServletRequestBuilder b, String token) {
    b.header("X-Forwarded-For", fwd()).accept(MediaType.APPLICATION_JSON);
    if (token != null) {
      b.header("Authorization", "Bearer " + token);
    }
    return b;
  }

  protected MvcResult doGet(String path, String token) throws Exception {
    return mvc.perform(withCommon(get(path), token)).andReturn();
  }

  protected MvcResult doPost(String path, String token, Object body) throws Exception {
    MockHttpServletRequestBuilder b = withCommon(post(path), token)
        .contentType(MediaType.APPLICATION_JSON);
    if (body != null) {
      b.content(body instanceof String s ? s : om.writeValueAsString(body));
    }
    return mvc.perform(b).andReturn();
  }

  protected MvcResult doPost(String path, String token, Object body,
      Map<String, String> headers) throws Exception {
    MockHttpServletRequestBuilder b = withCommon(post(path), token)
        .contentType(MediaType.APPLICATION_JSON);
    if (body != null) {
      b.content(body instanceof String s ? s : om.writeValueAsString(body));
    }
    headers.forEach(b::header);
    return mvc.perform(b).andReturn();
  }

  protected MvcResult doPatch(String path, String token, Object body,
      Map<String, String> headers) throws Exception {
    MockHttpServletRequestBuilder b = withCommon(patch(path), token)
        .contentType(MediaType.APPLICATION_JSON);
    if (body != null) {
      b.content(body instanceof String s ? s : om.writeValueAsString(body));
    }
    headers.forEach(b::header);
    return mvc.perform(b).andReturn();
  }

  protected JsonNode json(MvcResult r) throws Exception {
    return om.readTree(r.getResponse().getContentAsString());
  }

  protected String errorCode(MvcResult r) throws Exception {
    return json(r).path("error").path("code").asText();
  }

  protected String reasonCode(MvcResult r) throws Exception {
    return json(r).path("error").path("reason_code").asText();
  }

  protected String accessTokenOf(MvcResult loginResult) throws Exception {
    return json(loginResult).path("data").path("access_token").asText();
  }

  protected String refreshTokenOf(MvcResult loginResult) throws Exception {
    return json(loginResult).path("data").path("refresh_token").asText();
  }
}
