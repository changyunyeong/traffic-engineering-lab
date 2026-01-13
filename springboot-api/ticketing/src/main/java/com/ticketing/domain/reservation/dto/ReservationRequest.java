package com.ticketing.domain.reservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReservationRequest {

    @NotNull(message = "티켓 ID는 필수입니다")
    private Long ticketId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
}
