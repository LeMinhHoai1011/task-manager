package vn.smarttask.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.smarttask.admin.entity.SystemSetting;
import vn.smarttask.admin.repo.SystemSettingRepository;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.identity.service.IdentityService;
import vn.smarttask.security.repo.GlobalPermissionGrantRepository;

/**
 * F1 SA bootstrap. Runs only when no SA exists yet: it reads the
 * {@code bootstrap_completed} system flag and the live-grant count (metadata
 * only — it never scans user content or private data). When bootstrapping,
 * it resolves exactly one configured username
 * ({@code smarttask.bootstrap.admin-username}) and issues the first
 * {@code SYSTEM_ADMIN} grant (granted_by = null, reason BOOTSTRAP).
 *
 * <p>If no username is configured or the user does not exist, nothing is
 * created and the flag is left unset so a later start can retry.
 *
 * <p>The configured account is also activated (status ACTIVE + personal
 * workspace) when it is still PENDING: registration cannot self-approve and no
 * approver grant exists before the first SA, so this local one-time bootstrap
 * is the documented activation path. It never runs through the HTTP API.
 */
@Component
public class BootstrapRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
  static final String FLAG = "bootstrap_completed";

  private final SystemSettingRepository settings;
  private final GlobalPermissionGrantRepository grants;
  private final AppUserRepository users;
  private final GrantService grantService;
  private final String adminUsername;
  private final IdentityService identity;

  public BootstrapRunner(SystemSettingRepository settings,
      GlobalPermissionGrantRepository grants,
      AppUserRepository users,
      GrantService grantService,
      @Value("${smarttask.bootstrap.admin-username:}") String adminUsername) {
    this(settings, grants, users, grantService, adminUsername, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public BootstrapRunner(SystemSettingRepository settings,
      GlobalPermissionGrantRepository grants,
      AppUserRepository users,
      GrantService grantService,
      @Value("${smarttask.bootstrap.admin-username:}") String adminUsername,
      IdentityService identity) {
    this.settings = settings;
    this.grants = grants;
    this.users = users;
    this.grantService = grantService;
    this.adminUsername = adminUsername == null ? "" : adminUsername.trim();
    this.identity = identity;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (settings.findById(FLAG).map(s -> "true".equalsIgnoreCase(s.getValue())).orElse(false)) {
      return;
    }
    if (grants.existsAnyLive()) {
      settings.save(new SystemSetting(FLAG, "true"));
      return;
    }
    if (adminUsername.isEmpty()) {
      log.info("bootstrap: no smarttask.bootstrap.admin-username configured; skipping SA bootstrap");
      return;
    }
    var user = users.findByUsernameIgnoreCase(adminUsername).orElse(null);
    if (user == null) {
      log.warn("bootstrap: configured admin user '{}' not found; skipping SA bootstrap", adminUsername);
      return;
    }
    grantService.grantBootstrap(user.getId(), GrantService.SYSTEM_ADMIN);
    if (!user.isActive() || !"ACTIVE".equals(user.getStatus())) {
      if (identity != null) {
        identity.activateBootstrapAdmin(user.getId());
      } else {
        user.setStatus("ACTIVE");
        user.setActive(true);
        users.save(user);
      }
      log.info("bootstrap: activated configured admin account '{}'", adminUsername);
    }
    settings.save(new SystemSetting(FLAG, "true"));
    log.info("bootstrap: SYSTEM_ADMIN granted to '{}'", adminUsername);
  }
}
