package com.ticketing.domain.reservation.service;

import com.ticketing.domain.reservation.domain.Reservation;
import com.ticketing.domain.reservation.dto.ReservationRequest;
import com.ticketing.domain.reservation.dto.ReservationResponse;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.domain.ticket.domain.Ticket;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.domain.user.domain.User;
import com.ticketing.domain.user.repository.UserRepository;
import com.ticketing.global.enums.ReservationStatus;
import com.ticketing.global.exception.domain.DuplicateReservationException;
import com.ticketing.global.exception.domain.OutOfStockException;
import com.ticketing.global.exception.domain.ReservationNotFoundException;
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
    private static final String LOCK_KEY_PREFIX = "ticket:lock:";
    private static final int LOCK_WAIT_TIME = 10;
    private static final int LOCK_LEASE_TIME = 10;

    /**
     * 티켓 예약 - 분산 락 기반 동시성 제어
     */
    @Transactional
    public ReservationResponse reserveTicket(ReservationRequest request) {
        Long ticketId = request.getTicketId();
        Long userId = request.getUserId();

        log.info("Reservation attempt: userId={}, ticketId={}", userId, ticketId);

        // 1. 사용자 및 티켓 존재 여부 사전 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다: " + ticketId));

        // 2. 분산 락으로 동시성 제어
        String lockKey = LOCK_KEY_PREFIX + ticketId;

        try {
            return lockExecutor.executeWithLock(lockKey, LOCK_WAIT_TIME, LOCK_LEASE_TIME, () -> {
                // 3. 중복 예약 확인 (락 내부에서 체크)
                List<Reservation> activeReservations =
                        reservationRepository.findActiveReservations(userId, ticketId);

                if (!activeReservations.isEmpty()) {
                    log.warn("Duplicate reservation attempt: userId={}, ticketId={}, count={}",
                            userId, ticketId, activeReservations.size());
                    throw new DuplicateReservationException();
                }

                // 4. Redis에서 재고 확인 및 차감
                String stockKey = STOCK_KEY_PREFIX + ticketId;
                Long stock = getStockFromRedis(stockKey, ticket);

                // 재고 확인
                if (stock == null || stock <= 0) {
                    log.warn("Out of stock: ticketId={}, stock={}", ticketId, stock);
                    throw new OutOfStockException();
                }

                // 5. Redis 재고 차감 (원자적 연산)
                Long remaining = decrementStock(stockKey);

                if (remaining == null || remaining < 0) {
                    // 재고 복구
                    incrementStock(stockKey);
                    log.warn("Stock decrement failed: ticketId={}, remaining={}", ticketId, remaining);
                    throw new OutOfStockException();
                }

                log.info("Stock decremented in Redis: ticketId={}, remaining={}", ticketId, remaining);

                // 6. 예약 생성
                Reservation reservation = Reservation.builder()
                        .ticket(ticket)
                        .user(user)
                        .status(ReservationStatus.PENDING)
                        .build();

                reservation = reservationRepository.save(reservation);

                // 7. DB 재고도 차감 (Redis와 동기화)
                try {
                    ticket.decreaseStock();
                    ticketRepository.save(ticket);
                } catch (Exception e) {
                    log.error("Failed to decrease DB stock, rolling back Redis stock: ticketId={}", ticketId, e);
                    // Redis 재고 복구
                    incrementStock(stockKey);
                    throw new RuntimeException("재고 차감 중 오류가 발생했습니다", e);
                }

                // 8. Kafka 이벤트 발행
                publishReservationEvent(reservation, "CREATED");

                log.info("Reservation created successfully: id={}, userId={}, ticketId={}",
                        reservation.getId(), userId, ticketId);

                return convertToResponse(reservation);
            });
        } catch (Exception e) {
            log.error("Reservation failed: userId={}, ticketId={}, error={}",
                    userId, ticketId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Redis에서 재고 조회 - 타입 안전 처리
     */
    private Long getStockFromRedis(String stockKey, Ticket ticket) {
        try {
            Object value = redisTemplate.opsForValue().get(stockKey);

            // 캐시 미스
            if (value == null) {
                Long stock = ticket.getStock();
                redisTemplate.opsForValue().set(stockKey, stock, Duration.ofMinutes(30));
                log.info("Stock loaded from DB to Redis: ticketId={}, stock={}", ticket.getId(), stock);
                return stock;
            }

            // 타입 안전 변환
            if (value instanceof Number) {
                Long stock = ((Number) value).longValue();
                log.debug("Stock retrieved from Redis: ticketId={}, stock={}, type={}",
                        ticket.getId(), stock, value.getClass().getSimpleName());
                return stock;
            }

            // 문자열인 경우
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

//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class ReservationService {
//
//    private final ReservationRepository reservationRepository;
//    private final TicketRepository ticketRepository;
//    private final UserRepository userRepository;
//    private final RedisTemplate<String, Object> redisTemplate;
//    private final DistributedLockExecutor lockExecutor;
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//
//    private static final String STOCK_KEY_PREFIX = "ticket:stock:";
//    private static final String LOCK_KEY_PREFIX = "ticket:lock:";
//
//    /**
//     * 티켓 예약 - 분산 락 기반 동시성 제어
//     */
//    @Transactional
//    public ReservationResponse reserveTicket(ReservationRequest request) {
//        Long ticketId = request.getTicketId();
//        Long userId = request.getUserId();
//
//        // 1. 중복 예약 확인
//        reservationRepository.findActiveReservation(userId, ticketId)
//                .ifPresent(r -> {
//                    throw new DuplicateReservationException();
//                });
//
//        // 2. 분산 락으로 동시성 제어
//        String lockKey = LOCK_KEY_PREFIX + ticketId;
//
//        return lockExecutor.executeWithLock(lockKey, 3, 5, () -> {
//            // 3. Redis에서 재고 확인 및 차감
//            String stockKey = STOCK_KEY_PREFIX + ticketId;
//            Long stock = (Long) redisTemplate.opsForValue().get(stockKey);
//
//            // 캐시 미스 시 DB 조회
//            if (stock == null) {
//                Ticket ticket = ticketRepository.findById(ticketId)
//                        .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다"));
//                stock = ticket.getStock();
//                redisTemplate.opsForValue().set(stockKey, stock, Duration.ofMinutes(30));
//            }
//
//            // 재고 확인
//            if (stock <= 0) {
//                throw new OutOfStockException();
//            }
//
//            // 4. Redis 재고 차감 (원자적 연산)
//            Long remaining = redisTemplate.opsForValue().decrement(stockKey);
//            log.info("Stock decremented in Redis: ticketId={}, remaining={}", ticketId, remaining);
//
//            // 5. DB에서 티켓 및 사용자 조회
//            Ticket ticket = ticketRepository.findById(ticketId)
//                    .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다"));
//
//            User user = userRepository.findById(userId)
//                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
//
//            // 6. 예약 생성
//            Reservation reservation = Reservation.builder()
//                    .ticket(ticket)
//                    .user(user)
//                    .status(ReservationStatus.PENDING)
//                    .build();
//
//            reservation = reservationRepository.save(reservation);
//
//            // 7. DB 재고도 차감 (Redis와 동기화)
//            ticket.decreaseStock();
//            ticketRepository.save(ticket);
//
//            // 8. Kafka 이벤트 발행 (비동기 처리용)
//            publishReservationEvent(reservation, "CREATED");
//
//            log.info("Reservation created: id={}, userId={}, ticketId={}",
//                    reservation.getId(), userId, ticketId);
//
//            return convertToResponse(reservation);
//        });
//    }
//
//    /**
//     * 예약 확정
//     */
//    @Transactional
//    public ReservationResponse confirmReservation(Long reservationId) {
//        Reservation reservation = reservationRepository.findById(reservationId)
//                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
//
//        reservation.confirm();
//        reservationRepository.save(reservation);
//
//        publishReservationEvent(reservation, "CONFIRMED");
//
//        log.info("Reservation confirmed: id={}", reservationId);
//        return convertToResponse(reservation);
//    }
//
//    /**
//     * 예약 취소 (재고 복구)
//     */
//    @Transactional
//    public ReservationResponse cancelReservation(Long reservationId) {
//        Reservation reservation = reservationRepository.findById(reservationId)
//                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
//
//        reservation.cancel();
//        reservationRepository.save(reservation);
//
//        // 재고 복구
//        Ticket ticket = reservation.getTicket();
//        ticket.increaseStock();
//        ticketRepository.save(ticket);
//
//        // Redis 재고도 복구
//        String stockKey = STOCK_KEY_PREFIX + ticket.getId();
//        redisTemplate.opsForValue().increment(stockKey);
//
//        publishReservationEvent(reservation, "CANCELLED");
//
//        log.info("Reservation cancelled: id={}, stockRestored={}",
//                reservationId, ticket.getStock());
//
//        return convertToResponse(reservation);
//    }
//
//    /**
//     * 사용자 예약 조회
//     */
//    @Transactional(readOnly = true)
//    public Page<ReservationResponse> getUserReservations(Long userId, Pageable pageable) {
//        return reservationRepository.findByUserId(userId, pageable)
//                .map(this::convertToResponse);
//    }
//
//    /**
//     * 예약 조회
//     */
//    @Transactional(readOnly = true)
//    public ReservationResponse getReservation(Long reservationId) {
//        Reservation reservation = reservationRepository.findById(reservationId)
//                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
//
//        return convertToResponse(reservation);
//    }
//
//    /**
//     * 만료된 예약 자동 취소 (스케줄러용)
//     */
//    @Transactional
//    public void cancelExpiredReservations() {
//        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(5);
//        List<Reservation> expiredReservations =
//                reservationRepository.findExpiredReservations(expiryTime);
//
//        expiredReservations.forEach(reservation -> {
//            reservation.cancel();
//
//            // 재고 복구
//            Ticket ticket = reservation.getTicket();
//            ticket.increaseStock();
//
//            // Redis 재고 복구
//            String stockKey = STOCK_KEY_PREFIX + ticket.getId();
//            redisTemplate.opsForValue().increment(stockKey);
//
//            log.info("Expired reservation cancelled: id={}", reservation.getId());
//        });
//
//        reservationRepository.saveAll(expiredReservations);
//    }
//
//    /**
//     * Kafka 이벤트 발행
//     */
//    private void publishReservationEvent(Reservation reservation, String eventType) {
//        Map<String, Object> event = new HashMap<>();
//        event.put("reservationId", reservation.getId());
//        event.put("userId", reservation.getUser().getId());
//        event.put("ticketId", reservation.getTicket().getId());
//        event.put("eventType", eventType);
//        event.put("timestamp", LocalDateTime.now());
//
//        kafkaTemplate.send("reservation-events", event);
//    }
//
//    /**
//     * Entity -> Response 변환
//     */
//    private ReservationResponse convertToResponse(Reservation reservation) {
//        return ReservationResponse.builder()
//                .id(reservation.getId())
//                .ticketId(reservation.getTicket().getId())
//                .ticketName(reservation.getTicket().getName())
//                .userId(reservation.getUser().getId())
//                .userEmail(reservation.getUser().getEmail())
//                .status(reservation.getStatus())
//                .price(reservation.getTicket().getPrice())
//                .createdAt(reservation.getCreatedAt())
//                .confirmedAt(reservation.getConfirmedAt())
//                .cancelledAt(reservation.getCancelledAt())
//                .expired(reservation.isExpired())
//                .build();
//    }
//}