package vn.smarttask.note.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.smarttask.note.entity.Note;

public interface NoteRepository extends JpaRepository<Note, UUID> {

  @Query("SELECT n FROM Note n WHERE n.deletedAt IS NULL AND " +
         "(n.ownerUserId = :userId OR (n.scope = 'PROJECT' AND n.projectId IN :projectIds) " +
         "OR (n.scope = 'SHARED' AND n.workspaceId IN :workspaceIds) " +
         "OR (n.scope = 'TASK' AND n.projectId IN :projectIds)) " +
         "ORDER BY n.isPinned DESC, n.updatedAt DESC")
  List<Note> findAccessibleNotes(@Param("userId") UUID userId,
                                 @Param("projectIds") List<UUID> projectIds,
                                 @Param("workspaceIds") List<UUID> workspaceIds);

  @Query("SELECT n FROM Note n WHERE n.deletedAt IS NULL AND n.ownerUserId = :userId ORDER BY n.isPinned DESC, n.updatedAt DESC")
  List<Note> findPersonalNotes(@Param("userId") UUID userId);

  @Query("SELECT n FROM Note n WHERE n.deletedAt IS NULL AND n.projectId = :projectId ORDER BY n.isPinned DESC, n.updatedAt DESC")
  List<Note> findByProjectId(@Param("projectId") UUID projectId);

  @Query("SELECT n FROM Note n WHERE n.deletedAt IS NULL AND n.taskId = :taskId ORDER BY n.isPinned DESC, n.updatedAt DESC")
  List<Note> findByTaskId(@Param("taskId") UUID taskId);

  Optional<Note> findByIdAndDeletedAtIsNull(UUID id);
}
