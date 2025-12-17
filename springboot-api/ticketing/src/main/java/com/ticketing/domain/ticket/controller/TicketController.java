package com.ticketing.domain.ticket.controller;

import com.ticketing.domain.ticket.dto.TicketCreateRequest;
import com.ticketing.domain.ticket.dto.TicketResponse;
import com.ticketing.domain.ticket.service.TicketService;
import com.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Ticket", description = "티켓 API")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "티켓 생성", description = "이벤트에 티켓을 추가합니다")
    @PostMapping
    public ApiResponse<TicketResponse> createTicket(
            @Valid @RequestBody TicketCreateRequest request) {

        TicketResponse response = ticketService.createTicket(request);
        return ApiResponse.success("티켓이 생성되었습니다", response);
    }

    @Operation(summary = "티켓 조회", description = "ID로 티켓을 조회합니다")
    @GetMapping("/{id}")
    public ApiResponse<TicketResponse> getTicket(@PathVariable Long id) {
        TicketResponse response = ticketService.getTicket(id);
        return ApiResponse.success(response);
    }

    @Operation(summary = "이벤트별 티켓 조회", description = "특정 이벤트의 모든 티켓을 조회합니다")
    @GetMapping("/event/{eventId}")
    public ApiResponse<List<TicketResponse>> getTicketsByEvent(@PathVariable Long eventId) {
        List<TicketResponse> tickets = ticketService.getTicketsByEvent(eventId);
        return ApiResponse.success(tickets);
    }

    @Operation(summary = "예약 가능한 티켓 조회", description = "재고가 있는 티켓만 조회합니다")
    @GetMapping("/event/{eventId}/available")
    public ApiResponse<List<TicketResponse>> getAvailableTickets(@PathVariable Long eventId) {
        List<TicketResponse> tickets = ticketService.getAvailableTickets(eventId);
        return ApiResponse.success(tickets);
    }
}