package com.ticketing.global.exception.domain.ticket;

import com.ticketing.global.exception.BusinessException;

public class TicketNotFoundException extends BusinessException {

    public TicketNotFoundException(String message) {
        super("TICKET_NOT_FOUND", message);
    }

    public TicketNotFoundException(Long ticketId) {
        super("TICKET_NOT_FOUND", "해당 티켓 존재하지 않습니다: " + ticketId);
    }
}
