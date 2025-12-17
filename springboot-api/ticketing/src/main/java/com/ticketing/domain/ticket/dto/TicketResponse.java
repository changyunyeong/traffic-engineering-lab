package com.ticketing.domain.ticket.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
