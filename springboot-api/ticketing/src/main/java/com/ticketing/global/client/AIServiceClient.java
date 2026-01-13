package com.ticketing.global.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AIServiceClient {
    
    private final WebClient webClient;

    /**
     * RAG 시맨틱 검색
     *
     * @param query    검색 질의 ("신나는 콘서트", "아이와 볼 뮤지컬" 등)
     * @param topK     반환할 결과 수
     * @param category 카테고리 필터 (선택)
     * @return 검색 결과
     */
    public Map<String, Object> searchByRAG(String query, int topK, String category) {
        log.info("RAG 검색 요청: query='{}', topK={}, category={}", query, topK, category);

        try {
            Map<String, Object> request = Map.of(
                "query", query,
                "top_k", topK,
                "category", category != null ? category : ""
            );

            Map<String, Object> response = webClient.post()
            .uri("/api/v1/rag/search")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(30))
            .block();

            log.info("RAG 검색 완료: {} 결과", 
                    response != null ? response.get("total_results") : 0);
            return response;

        } catch (Exception e) {
            log.error("RAG 검색 실패: {}", e.getMessage());
            return Map.of("error", e.getMessage(), "results", List.of());
        }
    }

    /**
     * RAG 인덱싱 트리거
     */
    public Map<String, Object> indexEventsForRAG(boolean forceReindex) {
        log.info("RAG 인덱싱 요청: forceReindex={}", forceReindex);

        try {
            return webClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/rag/index")
                    .queryParam("force_reindex", forceReindex)
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMinutes(5))
                .block();

        } catch (Exception e) {
            log.error("RAG 인덱싱 실패: {}", e.getMessage());
            throw new RuntimeException("RAG 인덱싱 실패: " + e.getMessage());
        }
    }

    /**
     * RAG로 유사 이벤트 검색
     */
    public Map<String, Object> getSimilarEventsByRAG(Long eventId, int topK) {
        try {
            Map<String, Object> request = Map.of(
                    "event_id", eventId,
                    "top_k", topK
            );

            return webClient.post()
                    .uri("/api/v1/rag/similar")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

        } catch (Exception e) {
            log.error("RAG 유사 이벤트 검색 실패: {}", e.getMessage());
            return Map.of("error", e.getMessage(), "results", List.of());
        }
    }

    public Map<String, Object> checkRAGHealth() {
        try {
            return webClient.get()
                    .uri("/api/v1/rag/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception e) {
            return Map.of("status", "unhealthy", "error", e.getMessage());
        }
    }
}