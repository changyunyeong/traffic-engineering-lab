package com.ticketing.domain.reservation.dto;

import com.ticketing.global.enums.ReservationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long id;
    private Long ticketId;
    private String ticketName;
    private Long userId;
    private String userEmail;
    private ReservationStatus status;
    private Long price;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private Boolean expired;  // 만료 여부
}
