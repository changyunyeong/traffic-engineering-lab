package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.entity.Reservation;
import com.ticketing.domain.reservation.dto.ReservationRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.domain.ticket.entity.Ticket;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.domain.user.entity.User;
import com.ticketing.domain.user.repository.UserRepository;
import com.ticketing.global.enums.ReservationStatus;
import com.ticketing.global.exception.domain.reservation.DuplicateReservationException;
import com.ticketing.global.exception.domain.OutOfStockException;
import com.ticketing.global.exception.domain.reservation.ReservationNotFoundException;
import com.ticketing.global.exception.domain.ticket.TicketNotFoundException;
import com.ticketing.global.exception.domain.user.UserNotFoundException;
import com.ticketing.global.util.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockExecutor lockExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String STOCK_KEY_PREFIX = "ticket:stock:";
    private static final String USER_TICKET_LOCK_PREFIX = "reservation:user:";
    private static final int LOCK_WAIT_TIME = 3;
    private static final int LOCK_LEASE_TIME = 5;

    /**
     * 티켓 예약 - 분산 락 기반 동시성 제어
     */
    @Transactional
    public ReservationResponse reserveTicket(ReservationRequest request) {

        Long ticketId = request.getTicketId();
        Long userId = request.getUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        // 1. 먼저 Redis에서 원자적 재고 차감 시도 (락 외부에서 수행)
        String stockKey = STOCK_KEY_PREFIX + ticketId;
        Long remaining = decrementStockAtomic(stockKey, ticket);

        if (remaining == null || remaining < 0) {
            // 재고 부족 - 차감된 경우 복구
            if (remaining != null && remaining < 0) {
                incrementStock(stockKey);
            }
            log.warn("Out of stock: ticketId={}, remaining={}", ticketId, remaining);
            throw new OutOfStockException();
        }

        log.debug("Stock decremented in Redis: ticketId={}, remaining={}", ticketId, remaining);

        // 2. 사용자+티켓 단위 락으로 중복 예약만 방지
        String lockKey = USER_TICKET_LOCK_PREFIX + userId + ":ticket:" + ticketId;

        try {
            return lockExecutor.executeWithLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, () -> {
                // 중복 예약 확인
                List<Reservation> activeReservations =
                        reservationRepository.findActiveReservations(userId, ticketId);

                if (!activeReservations.isEmpty()) {
                    // 중복 예약 - Redis 재고 복구
                    incrementStock(stockKey);
                    log.warn("Duplicate reservation attempt: userId={}, ticketId={}, count={}",
                            userId, ticketId, activeReservations.size());
                    throw new DuplicateReservationException();
                }

                // 3. 예약 생성
                Reservation reservation = Reservation.builder()
                        .ticket(ticket)
                        .user(user)
                        .status(ReservationStatus.PENDING)
                        .build();

                reservation = reservationRepository.save(reservation);

                // 4. DB 재고 동기화
                try {
                    ticket.decreaseStock();
                    ticketRepository.save(ticket);
                } catch (Exception e) {
                    log.error("Failed to decrease DB stock, rolling back Redis stock: ticketId={}", ticketId, e);
                    incrementStock(stockKey);
                    throw new RuntimeException("재고 차감 중 오류가 발생했습니다", e);
                }

                // 5. Kafka 이벤트 발행
                publishReservationEvent(reservation, "CREATED");

                log.info("Reservation created successfully: id={}, userId={}, ticketId={}, remaining={}",
                        reservation.getId(), userId, ticketId, remaining);

                return convertToResponse(reservation);
            });
        } catch (DuplicateReservationException e) {
            // 중복 예약 예외는 그대로 전파 (이미 재고 복구됨)
            throw e;
        } catch (Exception e) {
            // 락 획득 실패 등 다른 예외 발생 시 재고 복구
            if (!(e instanceof DuplicateReservationException)) {
                incrementStock(stockKey);
                log.error("Reservation failed, stock restored: userId={}, ticketId={}, error={}",
                        userId, ticketId, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * redis 재고 조회
     */
    private Long getStockFromRedis(String stockKey, Ticket ticket) {

        try {
            Object value = redisTemplate.opsForValue().get(stockKey);

            // 캐시 미스
            if (value == null) {
                Long stock = ticket.getStock();
                redisTemplate.opsForValue().set(stockKey, stock, Duration.ofMinutes(30));
//                log.info("Stock loaded from DB to Redis: ticketId={}, stock={}", ticket.getId(), stock);
                return stock;
            }

            // 타입 변환
            if (value instanceof Number) {
                Long stock = ((Number) value).longValue(); // number type일 경우 long으로 캐스팅
//                log.debug("Stock retrieved from Redis: ticketId={}, stock={}, type={}",
//                        ticket.getId(), stock, value.getClass().getSimpleName());
                return stock;
            }

            if (value instanceof String) {
                return Long.parseLong((String) value);
            }

            log.warn("Unexpected type for stock: {}, ticketId={}, falling back to DB",
                    value.getClass(), ticket.getId());
            return ticket.getStock();

        } catch (Exception e) {
            log.error("Failed to get stock from Redis: ticketId={}, error={}",
                    ticket.getId(), e.getMessage(), e);
            return ticket.getStock();
        }
    }

    /**
     * Redis 재고 차감
     */
    private Long decrementStock(String stockKey) {

        try {
            return redisTemplate.opsForValue().decrement(stockKey);
        } catch (Exception e) {
            log.error("Failed to decrement stock in Redis: key={}, error={}",
                    stockKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Redis 원자적 재고 차감 (캐시 미스 시 DB에서 로드 후 차감)
     */
    private Long decrementStockAtomic(String stockKey, Ticket ticket) {
        try {
            // 캐시 미스 확인 및 초기화
            Object value = redisTemplate.opsForValue().get(stockKey);
            if (value == null) {
                Long stock = ticket.getStock();
                redisTemplate.opsForValue().set(stockKey, stock, Duration.ofMinutes(30));
                log.debug("Stock loaded from DB to Redis: ticketId={}, stock={}", ticket.getId(), stock);
            }

            // 원자적 차감
            return redisTemplate.opsForValue().decrement(stockKey);

        } catch (Exception e) {
            log.error("Failed to decrement stock in Redis: key={}, error={}", stockKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Redis 재고 증가 (복구용)
     */
    private void incrementStock(String stockKey) {

        try {
            redisTemplate.opsForValue().increment(stockKey);
            log.debug("Stock incremented in Redis: key={}", stockKey);
        } catch (Exception e) {
            log.error("Failed to increment stock in Redis: key={}, error={}",
                    stockKey, e.getMessage(), e);
        }
    }

    /**
     * 예약 확정
     */
    @Transactional
    public ReservationResponse confirmReservation(Long reservationId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        reservation.confirm();
        reservationRepository.save(reservation);

        publishReservationEvent(reservation, "CONFIRMED");

        log.info("Reservation confirmed: id={}", reservationId);
        return convertToResponse(reservation);
    }

    /**
     * 예약 취소 (재고 복구)
     */
    @Transactional
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        reservation.cancel();
        reservationRepository.save(reservation);

        // 재고 복구
        Ticket ticket = reservation.getTicket();
        ticket.increaseStock();
        ticketRepository.save(ticket);

        // Redis 재고도 복구
        String stockKey = STOCK_KEY_PREFIX + ticket.getId();
        incrementStock(stockKey);

        publishReservationEvent(reservation, "CANCELLED");

        log.info("Reservation cancelled: id={}, stockRestored={}",
                reservationId, ticket.getStock());

        return convertToResponse(reservation);
    }

    /**
     * 사용자 예약 조회
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getUserReservations(Long userId, Pageable pageable) {

        return reservationRepository.findByUserId(userId, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 예약 조회
     */
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long reservationId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        return convertToResponse(reservation);
    }

    /**
     * 만료된 예약 자동 취소 (스케줄러용)
     */
    @Transactional
    public void cancelExpiredReservations() {

        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(5);
        List<Reservation> expiredReservations =
                reservationRepository.findExpiredReservations(expiryTime);

        int cancelledCount = 0;
        for (Reservation reservation : expiredReservations) {
            try {
                reservation.cancel();

                // 재고 복구
                Ticket ticket = reservation.getTicket();
                ticket.increaseStock();

                // Redis 재고 복구
                String stockKey = STOCK_KEY_PREFIX + ticket.getId();
                incrementStock(stockKey);

                cancelledCount++;
                log.info("Expired reservation cancelled: id={}", reservation.getId());
            } catch (Exception e) {
                log.error("Failed to cancel expired reservation: id={}", reservation.getId(), e);
            }
        }

        if (cancelledCount > 0) {
            reservationRepository.saveAll(expiredReservations);
            log.info("Cancelled {} expired reservations", cancelledCount);
        }
    }

    /**
     * Kafka 이벤트 발행
     */
    private void publishReservationEvent(Reservation reservation, String eventType) {

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("reservationId", reservation.getId());
            event.put("userId", reservation.getUser().getId());
            event.put("ticketId", reservation.getTicket().getId());
            event.put("eventType", eventType);
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send("reservation-events", event);
            log.debug("Kafka event published: type={}, reservationId={}", eventType, reservation.getId());
        } catch (Exception e) {
            log.error("Failed to publish Kafka event: reservationId={}", reservation.getId(), e);
        }
    }

    /**
     * Entity -> Response 변환
     */
    private ReservationResponse convertToResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .ticketId(reservation.getTicket().getId())
                .ticketName(reservation.getTicket().getName())
                .userId(reservation.getUser().getId())
                .userEmail(reservation.getUser().getEmail())
                .status(reservation.getStatus())
                .price(reservation.getTicket().getPrice())
                .createdAt(reservation.getCreatedAt())
                .confirmedAt(reservation.getConfirmedAt())
                .cancelledAt(reservation.getCancelledAt())
                .expired(reservation.isExpired())
                .build();
    }
}