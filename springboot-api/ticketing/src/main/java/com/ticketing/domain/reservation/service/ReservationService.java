package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.domain.Reservation;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.domain.ticket.domain.Ticket;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.global.enums.ReservationStatus;
import com.ticketing.global.util.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockExecutor lockExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String STOCK_KEY = "ticket:stock:";
    private static final String LOCK_KEY = "ticket:lock:";

    /**
     * 티켓 예약 - 분산 락을 이용한 동시성 제어
     */
    @Transactional
    public Reservation reserveTicket(Long ticketId, Long userId) {
        String lockKey = LOCK_KEY + ticketId;

        return lockExecutor.executeWithLock(lockKey, 3, 5, () -> {
            // 1. Redis에서 재고 확인 (캐시 우선)
            String stockKey = STOCK_KEY + ticketId;
            Long stock = (Long) redisTemplate.opsForValue().get(stockKey);

            if (stock == null) {
                // 캐시 미스 시 DB 조회 후 캐싱
                Ticket ticket = ticketRepository.findById(ticketId)
                        .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다"));
                stock = ticket.getStock();
                redisTemplate.opsForValue().set(stockKey, stock, Duration.ofMinutes(30));
            }

            if (stock <= 0) {
                throw new IllegalStateException("티켓이 매진되었습니다");
            }

            // 2. 재고 차감 (Redis)
            redisTemplate.opsForValue().decrement(stockKey);

            // 3. 예약 생성 (DB)
            Reservation reservation = Reservation.builder()
                    .ticketId(ticketId)
                    .userId(userId)
                    .status(ReservationStatus.valueOf("PENDING"))
                    .build();
            reservation = reservationRepository.save(reservation);

            // 4. Kafka로 예약 이벤트 발행 (비동기 처리)
            kafkaTemplate.send("reservation-created", reservation);

            log.info("예약 완료: userId={}, ticketId={}, reservationId={}",
                    userId, ticketId, reservation.getId());

            return reservation;
        });
    }

    /**
     * 대기열 기반 예약 (공정성 확보)
     */
    public String addToQueue(Long ticketId, Long userId) {
        String queueKey = "queue:" + ticketId;
        String token = userId + ":" + System.currentTimeMillis();

        // Sorted Set에 타임스탬프로 추가 (선입선출 보장)
        redisTemplate.opsForZSet().add(queueKey, token, System.currentTimeMillis());

        // 대기 순번 조회
        Long rank = redisTemplate.opsForZSet().rank(queueKey, token);

        log.info("대기열 추가: userId={}, rank={}", userId, rank);
        return token;
    }
}
