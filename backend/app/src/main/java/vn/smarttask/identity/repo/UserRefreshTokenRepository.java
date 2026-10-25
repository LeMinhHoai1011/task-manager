package vn.smarttask.identity.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.identity.entity.UserRefreshToken;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, UUID> {
  Optional<UserRefreshToken> findByTokenHash(String hash);
  List<UserRefreshToken> findBySessionId(UUID sessionId);
}
