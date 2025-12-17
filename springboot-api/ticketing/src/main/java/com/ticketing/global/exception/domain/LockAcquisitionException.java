package com.ticketing.global.exception.domain;

import com.ticketing.global.exception.BusinessException;

public class LockAcquisitionException extends BusinessException {

    public LockAcquisitionException(String message) {
        super("LOCK_ACQUISITION_FAILED", message);
    }

    public LockAcquisitionException() {
        super("LOCK_ACQUISITION_FAILED", "잠시 후 다시 시도해주세요");
    }
}
