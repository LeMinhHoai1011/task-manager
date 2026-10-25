package vn.smarttask.files.repo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.smarttask.files.entity.StreamTicket;

public interface StreamTicketRepository extends JpaRepository<StreamTicket, UUID> {
  Optional<StreamTicket> findByTicketHash(String ticketHash);

  /** Pessimistic consume: concurrent requests serialize on the ticket row. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from StreamTicket t where t.ticketHash = :hash")
  Optional<StreamTicket> findByTicketHashForUpdate(@Param("hash") String hash);
}
