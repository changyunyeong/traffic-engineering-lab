package com.ticketing.domain.reservation.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationRequest {

    @NotNull(message = "티켓 ID는 필수입니다")
    private Long ticketId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
}
