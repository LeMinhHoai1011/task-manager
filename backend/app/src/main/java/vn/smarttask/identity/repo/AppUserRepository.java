package vn.smarttask.identity.repo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.identity.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByUsernameIgnoreCase(String username);
  Optional<AppUser> findByEmailIgnoreCase(String email);
  /** F3 §7 accounts alias: pending-approval queue, oldest first. */
  java.util.List<AppUser> findByStatusOrderByCreatedAtAsc(String status);
}
