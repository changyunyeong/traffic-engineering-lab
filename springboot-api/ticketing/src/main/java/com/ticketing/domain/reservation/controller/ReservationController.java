package com.ticketing.domain.reservation.controller;

import com.ticketing.domain.reservation.dto.ReservationRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.service.ReservationService;
import com.ticketing.global.dto.ApiResponse;
import com.ticketing.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reservation", description = "예약 API")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "티켓 예약", description = "티켓을 예약합니다 (동시성 제어)")
    @PostMapping
    public ApiResponse<ReservationResponse> reserveTicket(
            @Valid @RequestBody ReservationRequest request) {

        ReservationResponse response = reservationService.reserveTicket(request);
        return ApiResponse.success("예약이 완료되었습니다", response);
    }

    @Operation(summary = "예약 조회", description = "ID로 예약을 조회합니다")
    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> getReservation(@PathVariable Long id) {
        ReservationResponse response = reservationService.getReservation(id);
        return ApiResponse.success(response);
    }

    @Operation(summary = "사용자 예약 조회", description = "특정 사용자의 모든 예약을 조회합니다")
    @GetMapping("/user/{userId}")
    public ApiResponse<PageResponse<ReservationResponse>> getUserReservations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ReservationResponse> reservations = reservationService.getUserReservations(userId, pageable);

        PageResponse<ReservationResponse> pageResponse = PageResponse.<ReservationResponse>builder()
                .content(reservations.getContent())
                .page(reservations.getNumber())
                .size(reservations.getSize())
                .totalElements(reservations.getTotalElements())
                .totalPages(reservations.getTotalPages())
                .last(reservations.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Operation(summary = "예약 확정", description = "대기 중인 예약을 확정합니다")
    @PostMapping("/{id}/confirm")
    public ApiResponse<ReservationResponse> confirmReservation(@PathVariable Long id) {
        ReservationResponse response = reservationService.confirmReservation(id);
        return ApiResponse.success("예약이 확정되었습니다", response);
    }

    @Operation(summary = "예약 취소", description = "예약을 취소하고 재고를 복구합니다")
    @PostMapping("/{id}/cancel")
    public ApiResponse<ReservationResponse> cancelReservation(@PathVariable Long id) {
        ReservationResponse response = reservationService.cancelReservation(id);
        return ApiResponse.success("예약이 취소되었습니다", response);
    }
}
