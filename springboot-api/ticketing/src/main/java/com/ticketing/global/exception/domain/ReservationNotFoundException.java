package com.ticketing.global.exception.domain;

import com.ticketing.global.exception.BusinessException;

public class ReservationNotFoundException extends BusinessException {

    public ReservationNotFoundException(String message) {
        super("RESERVATION_NOT_FOUND", message);
    }

    public ReservationNotFoundException(Long reservationId) {
        super("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: " + reservationId);
    }
}
