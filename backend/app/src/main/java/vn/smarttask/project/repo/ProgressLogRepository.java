package vn.smarttask.project.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.smarttask.project.entity.ProgressLog;

public interface ProgressLogRepository extends JpaRepository<ProgressLog, UUID> {

  List<ProgressLog> findByProjectIdOrderByUpdateDateDescCreatedAtDesc(UUID projectId);

  @Query("SELECT p FROM ProgressLog p WHERE p.projectId = :projectId AND p.updateDate BETWEEN :from AND :to ORDER BY p.updateDate DESC, p.createdAt DESC")
  List<ProgressLog> findByProjectIdAndDateRange(@Param("projectId") UUID projectId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
