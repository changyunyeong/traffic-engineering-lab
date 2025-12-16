package com.ticketing.domain.reservation.domain;

import com.ticketing.global.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ticketId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}