package com.ticketing.domain.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.domain.event.entity.Event;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.domain.recommendation.dto.RecommendationResponse;
import com.ticketing.domain.recommendation.entity.EventRecommendation;
import com.ticketing.domain.reservation.entity.Reservation;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.global.client.FastApiClient;
import com.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final FastApiClient fastApiClient;
    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "recommendation:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 사용자에게 이벤트 추천
     * Redis 캐싱 적용
     */
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(Long userId, Integer limit) {

        // 1. 캐시 확인
        String cacheKey = CACHE_KEY_PREFIX + userId;
        String cachedData = redisTemplate.opsForValue().get(cacheKey);

        if (cachedData != null) {
            try {
                return objectMapper.readValue(cachedData, RecommendationResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cached data: {}", e.getMessage());
            }
        }

        // 2. FastAPI에서 추천 받기
        RecommendationResponse response = fastApiClient.getRecommendations(userId, limit);

        // 3. 추천된 이벤트 정보 보강 (DB에서 최신 정보)
        enrichRecommendations(response);

        // 4. 캐시 저장
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonData, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache recommendations: {}", e.getMessage());
        }

        return response;
    }

    /**
     * 추천 이벤트 정보 보강
     */
    private void enrichRecommendations(RecommendationResponse response) {
        if (response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
            return;
        }

        List<Long> eventIds = response.getRecommendations().stream()
                .map(EventRecommendation::getEventId)
                .collect(Collectors.toList());

        List<Event> events = eventRepository.findAllById(eventIds);
        Map<Long, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        // 이벤트 정보 업데이트
        response.getRecommendations().forEach(rec -> {
            Event event = eventMap.get(rec.getEventId());
            if (event != null) {
                rec.setTitle(event.getTitle());
            }
        });
    }

    /**
     * 인기 이벤트 추천 (Fallback)
     */
    @Transactional(readOnly = true)
    public List<EventRecommendation> getPopularEvents(int limit) {

        // 최근 예약이 많은 이벤트 조회
        List<Event> popularEvents = eventRepository.findAll().stream()
                .limit(limit)
                .collect(Collectors.toList());

        return popularEvents.stream()
                .map(event -> EventRecommendation.builder()
                        .eventId(event.getId())
                        .title(event.getTitle())
                        .score(0.5)  // 기본 점수
                        .reason("인기 이벤트")
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * FastAPI Anomaly Detection용 예약 데이터 조회
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReservationsForAnomalyDetection(int limit) {

        List<Reservation> reservations = reservationRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        return reservations.stream()
                .map(r -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("user_id", r.getUser().getId());
                    data.put("event_id", r.getTicket().getEvent().getId());
                    data.put("ticket_id", r.getTicket().getId());
                    data.put("price", r.getTicket().getPrice());
                    data.put("purchase_time", r.getCreatedAt());
                    data.put("ip_address", "192.168.1." + new Random().nextInt(255));
                    data.put("user_agent", "Mozilla/5.0");
                    return data;
                })
                .collect(Collectors.toList());

    }

    /**
     * 추천 캐시 삭제 (사용자가 새로운 예약을 했을 때)
     */
    public void invalidateCache(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(cacheKey);
        log.info("Invalidated recommendation cache for user: {}", userId);
    }

    /**
     * 추천 모델 재학습 트리거
     */
    public Map<String, Object> trainModel(boolean forceRetrain) {
        log.info("Triggering model training: forceRetrain={}", forceRetrain);
        return fastApiClient.trainModel(forceRetrain);
    }

    /**
     * FastAPI 상태 확인
     */
    public boolean checkFastApiHealth() {
        return fastApiClient.checkHealth();
    }

    public boolean checkHealth() {
        return fastApiClient.checkHealth();
    }
}
