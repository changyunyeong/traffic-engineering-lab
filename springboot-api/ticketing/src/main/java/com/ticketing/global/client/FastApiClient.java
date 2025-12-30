package com.ticketing.global.client;

import com.ticketing.domain.recommendation.dto.RecommendationRequest;
import com.ticketing.domain.recommendation.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FastApiClient {

    private final WebClient fastApiWebClient;

    public RecommendationResponse getRecommendations(Long userId, Integer limit) {

        RecommendationRequest request = RecommendationRequest.builder()
                .userId(userId)
                .limit(limit)
                .build();

        try {
            RecommendationResponse response = fastApiWebClient.post()
                    .uri("/api/v1/recommendations")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RecommendationResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return response != null ? response : createEmptyResponse(userId);

        } catch (Exception e) {
            log.error("FastAPI call failed: {}", e.getMessage());
            return createEmptyResponse(userId);
        }
    }

    public Map<String, Object> trainModel(boolean forceRetrain) {

        try {
            return fastApiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/recommendations/train")
                            .queryParam("force_retrain", forceRetrain)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(5))
                    .block();
        } catch (Exception e) {
            log.error("Model training failed: {}", e.getMessage());
            throw new RuntimeException("모델 학습 실패: " + e.getMessage());
        }
    }

    public boolean checkHealth() {
        try {
            Map<String, Object> response = fastApiWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            boolean isHealthy = response != null && "healthy".equals(response.get("status"));
            log.info("FastAPI health: {}", isHealthy ? "healthy" : "unhealthy");
            return isHealthy;

        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    private RecommendationResponse createEmptyResponse(Long userId) {
        return RecommendationResponse.builder()
                .userId(userId)
                .recommendations(new ArrayList<>())
                .build();
    }
}
