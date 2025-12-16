package com.ticketing.domain.ticket.repository;

import com.ticketing.domain.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
