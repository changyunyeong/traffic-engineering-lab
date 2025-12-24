package com.ticketing.domain.recommendation.controller;

import com.ticketing.domain.recommendation.dto.RecommendationResponse;
import com.ticketing.domain.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/{userId}")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        RecommendationResponse response = recommendationService.getRecommendations(userId, limit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> trainModel(
            @RequestParam(defaultValue = "false") boolean forceRetrain
    ) {
        Map<String, Object> result = recommendationService.trainModel(forceRetrain);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean isHealthy = recommendationService.checkHealth();
        return ResponseEntity.ok(Map.of(
                "fastapi_status", isHealthy ? "healthy" : "unhealthy",
                "connected", isHealthy
        ));
    }
}