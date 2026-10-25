package vn.smarttask.security.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.smarttask.security.entity.GlobalPermissionGrant;

public interface GlobalPermissionGrantRepository extends JpaRepository<GlobalPermissionGrant, UUID> {
  List<GlobalPermissionGrant> findByUserIdAndCode(UUID userId, String code);

  @Query("select g from GlobalPermissionGrant g where g.userId = :user and g.code = :code"
      + " and g.revokedAt is null and (g.expiresAt is null or g.expiresAt > current_timestamp)")
  List<GlobalPermissionGrant> findLive(@Param("user") UUID user, @Param("code") String code);

  @Query("select g from GlobalPermissionGrant g where g.userId = :user and g.code = :code"
      + " and g.revokedAt is null")
  Optional<GlobalPermissionGrant> findLiveRow(@Param("user") UUID user, @Param("code") String code);

  @Query("select count(g) > 0 from GlobalPermissionGrant g where g.revokedAt is null"
      + " and (g.expiresAt is null or g.expiresAt > current_timestamp)")
  boolean existsAnyLive();
}
