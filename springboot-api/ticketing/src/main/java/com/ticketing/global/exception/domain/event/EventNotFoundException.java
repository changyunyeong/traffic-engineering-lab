package com.ticketing.global.exception.domain.event;

import com.ticketing.global.exception.BusinessException;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException(String message) {
        super("EVENT_NOT_FOUND", message);
    }

    public EventNotFoundException(Long eventId) {
        super("EVENT_NOT_FOUND", "해당 이벤트가 존재하지 않습니다: " + eventId);
    }
}
