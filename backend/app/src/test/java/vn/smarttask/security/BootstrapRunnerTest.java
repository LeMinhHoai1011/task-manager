package vn.smarttask.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import vn.smarttask.admin.entity.SystemSetting;
import vn.smarttask.admin.repo.SystemSettingRepository;
import vn.smarttask.identity.entity.AppUser;
import vn.smarttask.identity.repo.AppUserRepository;
import vn.smarttask.security.repo.GlobalPermissionGrantRepository;

/**
 * F1 bootstrap (offline, Mockito): runs only when the flag is unset AND no
 * live grant exists; resolves exactly one configured username; never scans
 * user content.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapRunnerTest {

  @Mock SystemSettingRepository settings;
  @Mock GlobalPermissionGrantRepository grants;
  @Mock AppUserRepository users;
  @Mock GrantService grantService;

  private AppUser user(UUID id) {
    AppUser u = new AppUser(id, "root", "root@x.io", "h", "Root");
    u.setActive(true);
    u.setStatus("ACTIVE");
    return u;
  }

  @Test
  void skipsWhenFlagSet() {
    when(settings.findById(BootstrapRunner.FLAG))
        .thenReturn(Optional.of(new SystemSetting(BootstrapRunner.FLAG, "true")));
    new BootstrapRunner(settings, grants, users, grantService, "root").run(null);
    verify(grants, never()).existsAnyLive();
    verify(users, never()).findByUsernameIgnoreCase(any());
  }

  @Test
  void sealsFlagWhenGrantsAlreadyExist() {
    when(settings.findById(BootstrapRunner.FLAG)).thenReturn(Optional.empty());
    when(grants.existsAnyLive()).thenReturn(true);
    new BootstrapRunner(settings, grants, users, grantService, "root").run(null);
    verify(settings).save(any(SystemSetting.class));
    verify(users, never()).findByUsernameIgnoreCase(any());
  }

  @Test
  void bootstrapsSingleConfiguredUser() {
    UUID id = UUID.randomUUID();
    when(settings.findById(BootstrapRunner.FLAG)).thenReturn(Optional.empty());
    when(grants.existsAnyLive()).thenReturn(false);
    when(users.findByUsernameIgnoreCase("root")).thenReturn(Optional.of(user(id)));
    new BootstrapRunner(settings, grants, users, grantService, "root").run(null);
    verify(grantService).grantBootstrap(id, GrantService.SYSTEM_ADMIN);
    verify(settings).save(any(SystemSetting.class));
  }

  @Test
  void missingUserCreatesNothingAndLeavesFlagUnset() {
    when(settings.findById(BootstrapRunner.FLAG)).thenReturn(Optional.empty());
    when(grants.existsAnyLive()).thenReturn(false);
    when(users.findByUsernameIgnoreCase("root")).thenReturn(Optional.empty());
    new BootstrapRunner(settings, grants, users, grantService, "root").run(null);
    verify(grantService, never()).grantBootstrap(any(), any());
    verify(settings, never()).save(any(SystemSetting.class));
  }

  @Test
  void noUsernameConfiguredDoesNothing() {
    when(settings.findById(BootstrapRunner.FLAG)).thenReturn(Optional.empty());
    when(grants.existsAnyLive()).thenReturn(false);
    new BootstrapRunner(settings, grants, users, grantService, "").run(null);
    verify(users, never()).findByUsernameIgnoreCase(any());
    verify(settings, never()).save(any(SystemSetting.class));
  }
}
