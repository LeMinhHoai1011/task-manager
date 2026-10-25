package vn.smarttask.identity.repo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.smarttask.identity.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from UserSession s where s.userId = :userId and s.active = true and s.status = 'ACTIVE'")
  Optional<UserSession> findActiveSessionByUserForUpdate(@Param("userId") UUID userId);

  List<UserSession> findByUserIdAndActiveTrue(UUID userId);
  Optional<UserSession> findBySessionTokenHash(String hash);
}
