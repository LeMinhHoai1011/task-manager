package vn.smarttask.identity.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.identity.entity.UserRefreshToken;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, UUID> {
  Optional<UserRefreshToken> findByTokenHash(String hash);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select t from UserRefreshToken t where t.tokenHash = :hash")
  Optional<UserRefreshToken> findByTokenHashForUpdate(@org.springframework.data.repository.query.Param("hash") String hash);
  List<UserRefreshToken> findBySessionId(UUID sessionId);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<UserRefreshToken> findByRotatedFromId(UUID rotatedFromId);
}
