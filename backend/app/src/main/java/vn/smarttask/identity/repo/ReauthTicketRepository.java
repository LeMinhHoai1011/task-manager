package vn.smarttask.identity.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.smarttask.identity.entity.ReauthTicket;

public interface ReauthTicketRepository extends JpaRepository<ReauthTicket, UUID> {
}
