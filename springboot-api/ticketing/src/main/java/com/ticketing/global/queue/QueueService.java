package com.ticketing.global.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "queue:ticket:";
    private static final int PROCESS_RATE_PER_SECOND = 100;  // 초당 처리량

    /**
     * 대기열 진입
     */
    public QueueStatusResponse enterQueue(Long ticketId, Long userId) {
        String queueKey = QUEUE_KEY_PREFIX + ticketId;
        String token = generateToken(userId);
        double timestamp = System.currentTimeMillis();

        // Sorted Set에 추가 (timestamp 기준 정렬)
        redisTemplate.opsForZSet().add(queueKey, token, timestamp);

        // 현재 대기 순번 조회
        Long position = redisTemplate.opsForZSet().rank(queueKey, token);
        Long totalWaiting = redisTemplate.opsForZSet().size(queueKey);

        // 예상 대기 시간 계산 (초 단위)
        Integer estimatedWaitTime = position != null
                ? (int) (position / PROCESS_RATE_PER_SECOND)
                : 0;

        log.info("User entered queue: userId={}, ticketId={}, position={}",
                userId, ticketId, position);

        return QueueStatusResponse.builder()
                .token(token)
                .position(position != null ? position + 1 : 1)  // 1부터 시작
                .totalWaiting(totalWaiting)
                .estimatedWaitTimeSeconds(estimatedWaitTime)
                .enteredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 대기열 상태 조회
     */
    public QueueStatusResponse getQueueStatus(Long ticketId, String token) {
        String queueKey = QUEUE_KEY_PREFIX + ticketId;

        Long position = redisTemplate.opsForZSet().rank(queueKey, token);
        if (position == null) {
            throw new IllegalArgumentException("대기열에서 토큰을 찾을 수 없습니다");
        }

        Long totalWaiting = redisTemplate.opsForZSet().size(queueKey);
        Integer estimatedWaitTime = (int) (position / PROCESS_RATE_PER_SECOND);

        return QueueStatusResponse.builder()
                .token(token)
                .position(position + 1)
                .totalWaiting(totalWaiting)
                .estimatedWaitTimeSeconds(estimatedWaitTime)
                .build();
    }

    /**
     * 대기열에서 제거 (예약 완료 또는 이탈 시)
     */
    public void removeFromQueue(Long ticketId, String token) {
        String queueKey = QUEUE_KEY_PREFIX + ticketId;
        redisTemplate.opsForZSet().remove(queueKey, token);

        log.info("User removed from queue: ticketId={}, token={}", ticketId, token);
    }

    /**
     * 처리 가능한 대기자 N명 가져오기
     */
    public Set<Object> pollFromQueue(Long ticketId, int count) {
        String queueKey = QUEUE_KEY_PREFIX + ticketId;

        // 가장 앞에 있는 N명 조회
        Set<Object> tokens = redisTemplate.opsForZSet().range(queueKey, 0, count - 1);

        // 조회된 토큰 제거
        if (tokens != null && !tokens.isEmpty()) {
            tokens.forEach(token -> redisTemplate.opsForZSet().remove(queueKey, token));
        }

        return tokens;
    }

    /**
     * 토큰 생성
     */
    private String generateToken(Long userId) {
        return userId + ":" + UUID.randomUUID().toString();
    }

    /**
     * 대기열 크기 조회
     */
    public Long getQueueSize(Long ticketId) {
        String queueKey = QUEUE_KEY_PREFIX + ticketId;
        return redisTemplate.opsForZSet().size(queueKey);
    }
}
