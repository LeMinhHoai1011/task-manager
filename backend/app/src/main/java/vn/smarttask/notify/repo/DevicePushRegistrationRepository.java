package vn.smarttask.notify.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.notify.entity.DevicePushRegistration;

public interface DevicePushRegistrationRepository
    extends JpaRepository<DevicePushRegistration, UUID> {
  List<DevicePushRegistration> findByUserIdOrderByRegisteredAtDesc(UUID userId);
  Optional<DevicePushRegistration> findByPushTokenHash(String pushTokenHash);
  List<DevicePushRegistration> findBySessionIdAndStatus(UUID sessionId, String status);
  long countByUserIdAndStatus(UUID userId, String status);
  List<DevicePushRegistration> findByPlatformAndStatus(String platform, String status);
}
