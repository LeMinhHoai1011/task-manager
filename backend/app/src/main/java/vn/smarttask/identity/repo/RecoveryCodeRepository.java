package vn.smarttask.identity.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.identity.entity.RecoveryCode;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {
  List<RecoveryCode> findByUserIdAndUsedFalse(UUID userId);
}
