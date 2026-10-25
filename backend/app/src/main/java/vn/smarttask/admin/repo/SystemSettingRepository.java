package vn.smarttask.admin.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.admin.entity.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
