package com.ticketing.global.exception.domain.user;

import com.ticketing.global.exception.BusinessException;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }

    public UserNotFoundException(Long userId) {
        super("USER_NOT_FOUND", "해당 유저가 존재하지 않습니다: " + userId);
    }
}
