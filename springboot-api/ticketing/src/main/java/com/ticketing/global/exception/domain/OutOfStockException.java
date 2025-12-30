package com.ticketing.global.exception.domain;

import com.ticketing.global.exception.BusinessException;

public class OutOfStockException extends BusinessException {

    public OutOfStockException(String message) {
        super("OUT_OF_STOCK", message);
    }

    public OutOfStockException() {
        super("OUT_OF_STOCK", "티켓이 매진되었습니다");
    }
}
