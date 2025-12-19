package com.ticketing.domain.user.controller;

import com.ticketing.domain.reservation.dto.ReservationRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.user.dto.UserCreateRequest;
import com.ticketing.domain.user.dto.UserResponse;
import com.ticketing.domain.user.service.UserService;
import com.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "유저 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "회원가입")
    @PostMapping
    public ApiResponse<UserResponse> signUp(
            @Valid @RequestBody UserCreateRequest request) {

        UserResponse response = userService.joinUser(request);
        return ApiResponse.success("회원가입이 완료되었습니다", response);
    }
}
