package vn.smarttask.realtime;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RealtimeTicketRepository extends JpaRepository<RealtimeTicket, UUID> {
  Optional<RealtimeTicket> findByTicketHash(String ticketHash);

  /** Pessimistic validate-and-consume: concurrent handshakes serialize. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RealtimeTicket t where t.ticketHash = :hash")
  Optional<RealtimeTicket> findByTicketHashForUpdate(@Param("hash") String hash);
}
