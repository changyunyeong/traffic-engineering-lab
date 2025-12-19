package com.ticketing.domain.ticket.repository;

import com.ticketing.domain.ticket.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 이벤트별 티켓 조회
    List<Ticket> findByEventId(Long eventId);

    // 재고 있는 티켓만 조회
    @Query("SELECT t FROM Ticket t WHERE t.event.id = :eventId AND t.stock > 0")
    List<Ticket> findAvailableTicketsByEventId(@Param("eventId") Long eventId);

    // Pessimistic Lock (비관적 락)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdWithLock(@Param("id") Long id);

    // 전체 재고 수 조회
    @Query("SELECT SUM(t.stock) FROM Ticket t WHERE t.event.id = :eventId")
    Long getTotalStockByEventId(@Param("eventId") Long eventId);
}
