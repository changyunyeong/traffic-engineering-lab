package com.ticketing.domain.ticket.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
public class TicketCreateRequest {

    @NotNull(message = "이벤트 ID는 필수입니다")
    private Long eventId;

    @NotBlank(message = "티켓 이름은 필수입니다")
    private String name;

    @NotNull(message = "재고는 필수입니다")
    @Min(value = 0, message = "재고는 0 이상이어야 합니다")
    private Long stock;

    @NotNull(message = "가격은 필수입니다")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    private Long price;
}
