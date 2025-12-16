package com.ticketing.domain.ticket.controller;

import com.ticketing.domain.reservation.domain.Reservation;
import com.ticketing.domain.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final ReservationService reservationService;

    @PostMapping("/{ticketId}/reserve")
    public ResponseEntity<Reservation> reserve(
            @PathVariable Long ticketId,
            @RequestHeader("X-User-Id") Long userId) {

        Reservation reservation = reservationService.reserveTicket(ticketId, userId);
        return ResponseEntity.ok(reservation);
    }

    @PostMapping("/{ticketId}/queue")
    public ResponseEntity<String> joinQueue(
            @PathVariable Long ticketId,
            @RequestHeader("X-User-Id") Long userId) {

        String token = reservationService.addToQueue(ticketId, userId);
        return ResponseEntity.ok(token);
    }
}
