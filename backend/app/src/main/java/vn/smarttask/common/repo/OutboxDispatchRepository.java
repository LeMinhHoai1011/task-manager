package vn.smarttask.common.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.common.entity.OutboxDispatch;

public interface OutboxDispatchRepository extends JpaRepository<OutboxDispatch, OutboxDispatch.Key> {
}
