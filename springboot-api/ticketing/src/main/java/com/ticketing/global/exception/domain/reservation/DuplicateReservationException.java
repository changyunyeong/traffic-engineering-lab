package com.ticketing.global.exception.domain.reservation;

import com.ticketing.global.exception.BusinessException;

public class DuplicateReservationException extends BusinessException {

    public DuplicateReservationException(String message) {
        super("DUPLICATE_RESERVATION", message);
    }

    public DuplicateReservationException() {
        super("DUPLICATE_RESERVATION", "이미 해당 티켓을 예약하셨습니다");
    }
}
