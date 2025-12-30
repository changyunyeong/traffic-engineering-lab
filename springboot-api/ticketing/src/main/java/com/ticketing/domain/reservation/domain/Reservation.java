package com.ticketing.domain.reservation.domain;

import com.ticketing.domain.ticket.domain.Ticket;
import com.ticketing.domain.user.domain.User;
import com.ticketing.global.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservation_user", columnList = "user_id"),
        @Index(name = "idx_reservation_ticket", columnList = "ticket_id"),
        @Index(name = "idx_reservation_status", columnList = "status")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime cancelledAt;

    // 비즈니스 메서드
    public void confirm() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 예약만 확정할 수 있습니다");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == ReservationStatus.PENDING;
    }

    public boolean isExpired() {
        if (status != ReservationStatus.PENDING) {
            return false;
        }
        // 5분 경과 시 만료
        return createdAt.plusMinutes(5).isBefore(LocalDateTime.now());
    }
}