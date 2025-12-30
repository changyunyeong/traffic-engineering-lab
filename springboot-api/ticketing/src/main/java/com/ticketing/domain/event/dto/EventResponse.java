package com.ticketing.domain.event.dto;

import com.ticketing.domain.ticket.dto.TicketResponse;
import com.ticketing.global.enums.Category;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {

    private Long id;
    private String title;
    private String description;
    private Category category;
    private String venue;
    private LocalDateTime eventDate;
    private String imageUrl;
    private Long totalStock;  // 전체 티켓 재고
    private LocalDateTime createdAt;
    private List<TicketResponse> tickets;
}
