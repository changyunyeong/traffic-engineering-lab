package com.ticketing.domain.recommendation.controller;

import com.ticketing.domain.recommendation.dto.RecommendationResponse;
import com.ticketing.domain.recommendation.service.RecommendationService;
import com.ticketing.domain.reservation.entity.Reservation;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{userId}")
    @Operation(summary = "FastAPI Anomaly Detection용 예약 데이터 조회")
    public ApiResponse<RecommendationResponse> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        RecommendationResponse response = recommendationService.getRecommendations(userId, limit);
        return ApiResponse.success(response);
    }

    @PostMapping("/train")
    @Operation(summary = "FastAPI 추천 모델 학습 트리거")
    public ApiResponse<Map<String, Object>> trainModel(
            @RequestParam(defaultValue = "false") boolean forceRetrain
    ) {
        Map<String, Object> result = recommendationService.trainModel(forceRetrain);
        return ApiResponse.success(result);
    }

    @GetMapping("/health")
    @Operation(summary = "FastAPI 헬스 체크")
    public ApiResponse<Void> healthCheck() {
        boolean isHealthy = recommendationService.checkHealth();
        return ApiResponse.success(null);
    }

    @GetMapping("/for-anomaly-detection")
    @Operation(summary = "FastAPI Anomaly Detection용 예약 데이터 조회")
    public ApiResponse<List<Map<String, Object>>> getReservationsForAnomalyDetection(
            @RequestParam(defaultValue = "1000") int limit
    ) {
        List<Map<String, Object>> result = recommendationService.getReservationsForAnomalyDetection(limit);
        return ApiResponse.success(result);
    }
}