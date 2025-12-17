package com.ticketing.domain.reservation.repository;

import com.ticketing.domain.reservation.domain.Reservation;
import com.ticketing.global.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // 사용자별 예약 조회
    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    // 사용자 + 상태별 예약 조회
    List<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status);

    // 티켓별 예약 수 조회
    Long countByTicketIdAndStatus(Long ticketId, ReservationStatus status);

    // 만료된 예약 조회 (5분 경과)
    @Query("SELECT r FROM Reservation r " +
            "WHERE r.status = 'PENDING' " +
            "AND r.createdAt < :expiryTime")
    List<Reservation> findExpiredReservations(@Param("expiryTime") LocalDateTime expiryTime);

    // 사용자의 특정 티켓 중복 예약 확인
    @Query("SELECT r FROM Reservation r " +
            "WHERE r.user.id = :userId " +
            "AND r.ticket.id = :ticketId " +
            "AND r.status IN ('PENDING', 'CONFIRMED')")
    Optional<Reservation> findActiveReservation(
            @Param("userId") Long userId,
            @Param("ticketId") Long ticketId
    );

    // 이벤트별 예약 통계
    @Query("SELECT COUNT(r) FROM Reservation r " +
            "WHERE r.ticket.event.id = :eventId " +
            "AND r.status = :status")
    Long countByEventIdAndStatus(
            @Param("eventId") Long eventId,
            @Param("status") ReservationStatus status
    );
}
