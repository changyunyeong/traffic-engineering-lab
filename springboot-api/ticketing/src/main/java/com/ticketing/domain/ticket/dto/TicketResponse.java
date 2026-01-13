package com.ticketing.domain.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private Long id;
    private Long eventId;
    private String eventTitle;
    private String name;
    private Long stock;
    private Long price;
    private Boolean available;  // 예약 가능 여부
    private LocalDateTime createdAt;
}
