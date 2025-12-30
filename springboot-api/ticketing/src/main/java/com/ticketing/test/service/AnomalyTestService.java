package com.ticketing.test.service;

import com.ticketing.domain.reservation.entity.Reservation;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.domain.ticket.entity.Ticket;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.domain.user.entity.User;
import com.ticketing.domain.user.repository.UserRepository;
import com.ticketing.global.enums.ReservationStatus;
import com.ticketing.global.snowflake.Snowflake;
import com.ticketing.test.dto.test.AnomalyTestRequest;
import com.ticketing.test.dto.test.AnomalyTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyTestService {

    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final Snowflake snowflake;
    private final Random random = new Random();

    @Transactional
    public AnomalyTestResponse generateNormalReservations(AnomalyTestRequest request) {
        log.info("정상 패턴 예약 생성: {}건", request.getCount());

        if (request.getCount() <= 0) {
            return AnomalyTestResponse.builder()
                    .pattern("NORMAL")
                    .count(0)
                    .message("생성할 개수가 0입니다")
                    .build();
        }

        List<Reservation> reservations = new ArrayList<>();
        List<Ticket> tickets = getRandomTickets(request.getCount());
        List<User> users = getRandomUsers(request.getCount());

        if (tickets.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("NORMAL")
                    .count(0)
                    .message("티켓이 존재하지 않습니다")
                    .build();
        }

        if (users.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("NORMAL")
                    .count(0)
                    .message("사용자가 존재하지 없습니다")
                    .build();
        }

        for (int i = 0; i < request.getCount(); i++) {
            Ticket ticket = tickets.get(i % tickets.size());
            User user = users.get(i % users.size());

            LocalDateTime createdAt = LocalDateTime.now()
                    .minusDays(random.nextInt(30))
                    .withHour(9 + random.nextInt(14))
                    .withMinute(random.nextInt(60))
                    .withSecond(random.nextInt(60));

            Reservation reservation = Reservation.builder()
                    .id(snowflake.nextId())
                    .ticket(ticket)
                    .user(user)
                    .status(ReservationStatus.CONFIRMED)
                    .createdAt(createdAt)
                    .confirmedAt(createdAt.plusMinutes(5))
                    .build();

            reservations.add(reservation);
        }

        reservationRepository.saveAll(reservations);

        return AnomalyTestResponse.builder()
                .pattern("NORMAL")
                .count(reservations.size())
                .message("정상 패턴 예약 생성 완료")
                .build();
    }

    @Transactional
    public AnomalyTestResponse generateNightTimeAnomaly(AnomalyTestRequest request) {
        log.info("심야 시간대 이상 패턴 생성: {}건", request.getCount());

        if (request.getCount() <= 0) {
            return AnomalyTestResponse.builder()
                    .pattern("NIGHT_TIME")
                    .count(0)
                    .message("생성할 개수가 0입니다")
                    .build();
        }

        List<Reservation> reservations = new ArrayList<>();
        List<Ticket> tickets = getRandomTickets(request.getCount());
        List<User> users = getRandomUsers(1);

        if (tickets.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("NIGHT_TIME")
                    .count(0)
                    .message("티켓이 존재하지 않습니다")
                    .build();
        }

        if (users.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("NIGHT_TIME")
                    .count(0)
                    .message("사용자가 존재하지 없습니다")
                    .build();
        }
        User suspiciousUser = users.get(0);

        for (int i = 0; i < request.getCount(); i++) {
            Ticket ticket = tickets.get(i % tickets.size());

            LocalDateTime createdAt = LocalDateTime.now()
                    .minusDays(random.nextInt(7))
                    .withHour(1 + random.nextInt(4))
                    .withMinute(random.nextInt(60))
                    .withSecond(random.nextInt(60));

            Reservation reservation = Reservation.builder()
                    .id(snowflake.nextId())
                    .ticket(ticket)
                    .user(suspiciousUser)
                    .status(ReservationStatus.PENDING)
                    .createdAt(createdAt)
                    .build();

            reservations.add(reservation);
        }

        reservationRepository.saveAll(reservations);

        return AnomalyTestResponse.builder()
                .pattern("NIGHT_TIME")
                .count(reservations.size())
                .message("심야 시간대 이상 패턴 생성 완료")
                .suspiciousUserId(suspiciousUser.getId())
                .build();
    }

    @Transactional
    public AnomalyTestResponse generateBulkReservationAnomaly(AnomalyTestRequest request) {
        log.info("단시간 대량 예약 패턴 생성: {}건", request.getCount());

        if (request.getCount() <= 0) {
            return AnomalyTestResponse.builder()
                    .pattern("BULK_RESERVATION")
                    .count(0)
                    .message("생성할 개수가 0입니다")
                    .build();
        }

        List<Reservation> reservations = new ArrayList<>();
        List<Ticket> tickets = getRandomTickets(request.getCount());
        List<User> users = getRandomUsers(1);

        if (tickets.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("BULK_RESERVATION")
                    .count(0)
                    .message("티켓이 존재하지 않습니다")
                    .build();
        }

        if (users.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("BULK_RESERVATION")
                    .count(0)
                    .message("사용자가 존재하지 없습니다")
                    .build();
        }

        User suspiciousUser = users.get(0);
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1);

        for (int i = 0; i < request.getCount(); i++) {
            Ticket ticket = tickets.get(i % tickets.size());

            LocalDateTime createdAt = baseTime.plusSeconds(i * 2); // 2초 단위

            Reservation reservation = Reservation.builder()
                    .id(snowflake.nextId())
                    .ticket(ticket)
                    .user(suspiciousUser)
                    .status(ReservationStatus.CONFIRMED)
                    .createdAt(createdAt)
                    .confirmedAt(createdAt.plusSeconds(1))
                    .build();

            reservations.add(reservation);
        }

        reservationRepository.saveAll(reservations);

        return AnomalyTestResponse.builder()
                .pattern("BULK_RESERVATION")
                .count(reservations.size())
                .message("단시간 대량 예약 패턴 생성 완료")
                .suspiciousUserId(suspiciousUser.getId())
                .build();
    }

//    @Transactional
//    public AnomalyTestResponse generateHighPriceAnomaly(AnomalyTestRequest request) {
//        log.info("고가 티켓 반복 구매 패턴 생성: {}건", request.getCount());
//
//        if (request.getCount() <= 0) {
//            return AnomalyTestResponse.builder()
//                    .pattern("HIGH_PRICE")
//                    .count(0)
//                    .message("생성할 개수가 0입니다")
//                    .build();
//        }
//
//        List<Reservation> reservations = new ArrayList<>();
//        List<Ticket> expensiveTickets = ticketRepository.findAll().stream()
//                .filter(t -> t.getPrice() > 150000)
//                .limit(10)
//                .toList();
//
//        if (expensiveTickets.isEmpty()) {
//            return AnomalyTestResponse.builder()
//                    .pattern("HIGH_PRICE")
//                    .count(0)
//                    .message("고가 티켓이 없습니다")
//                    .build();
//        }
//
//        List<User> users = getRandomUsers(1);
//        if (users.isEmpty()) {
//            return AnomalyTestResponse.builder()
//                    .pattern("HIGH_PRICE")
//                    .count(0)
//                    .message("사용자가 없습니다")
//                    .build();
//        }
//
//        User suspiciousUser = users.get(0);
//
//        for (int i = 0; i < request.getCount(); i++) {
//            Ticket ticket = expensiveTickets.get(i % expensiveTickets.size());
//
//            LocalDateTime createdAt = LocalDateTime.now()
//                    .minusDays(random.nextInt(7))
//                    .withHour(random.nextInt(24))
//                    .withMinute(random.nextInt(60))
//                    .withSecond(random.nextInt(60));
//
//            Reservation reservation = Reservation.builder()
//                    .id(snowflake.nextId())
//                    .ticket(ticket)
//                    .user(suspiciousUser)
//                    .status(ReservationStatus.CONFIRMED)
//                    .createdAt(createdAt)
//                    .confirmedAt(createdAt.plusMinutes(1))
//                    .build();
//
//            reservations.add(reservation);
//        }
//
//        reservationRepository.saveAll(reservations);
//
//        return AnomalyTestResponse.builder()
//                .pattern("HIGH_PRICE")
//                .count(reservations.size())
//                .message("고가 티켓 반복 구매 패턴 생성 완료")
//                .suspiciousUserId(suspiciousUser.getId())
//                .build();
//    }

    @Transactional
    public AnomalyTestResponse generateSameIpAnomaly(AnomalyTestRequest request) {
        log.info("동일 IP 다중 계정 패턴 생성: {}건", request.getCount());

        if (request.getCount() <= 0) {
            return AnomalyTestResponse.builder()
                    .pattern("SAME_IP")
                    .count(0)
                    .message("생성할 개수가 0입니다")
                    .build();
        }

        List<Reservation> reservations = new ArrayList<>();
        List<Ticket> tickets = getRandomTickets(request.getCount());
        int userCount = Math.min(request.getCount(), 20);
        List<User> users = getRandomUsers(userCount);

        if (tickets.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("SAME_IP")
                    .count(0)
                    .message("티켓이 존재하지 않습니다")
                    .build();
        }

        if (users.isEmpty()) {
            return AnomalyTestResponse.builder()
                    .pattern("SAME_IP")
                    .count(0)
                    .message("사용자가 존재하지 없습니다")
                    .build();
        }

        String suspiciousIp = "203.123.45.67";

        for (int i = 0; i < request.getCount(); i++) {
            Ticket ticket = tickets.get(i % tickets.size());
            User user = users.get(i % users.size());

            LocalDateTime createdAt = LocalDateTime.now()
                    .minusHours(random.nextInt(24))
                    .withMinute(random.nextInt(60))
                    .withSecond(random.nextInt(60));

            Reservation reservation = Reservation.builder()
                    .id(snowflake.nextId())
                    .ticket(ticket)
                    .user(user)
                    .status(ReservationStatus.CONFIRMED)
                    .createdAt(createdAt)
                    .confirmedAt(createdAt.plusMinutes(2))
                    .build();

            reservations.add(reservation);
        }

        reservationRepository.saveAll(reservations);

        return AnomalyTestResponse.builder()
                .pattern("SAME_IP")
                .count(reservations.size())
                .message("동일 IP 다중 계정 패턴 생성 완료")
                .suspiciousIp(suspiciousIp)
                .build();
    }

    @Transactional
    public AnomalyTestResponse generateMixedPatterns(AnomalyTestRequest request) {
        log.info("혼합 패턴 생성: {}건", request.getCount());

        int normalCount = request.getCount() * 8 / 10;
        int anomalyCount = request.getCount() * 2 / 10;

        // 최소값 보장 (0이 되지 않도록)
        int perPattern = Math.max(1, anomalyCount / 4);

        generateNormalReservations(
                AnomalyTestRequest.builder().count(normalCount).build());

        generateNightTimeAnomaly(
                AnomalyTestRequest.builder().count(perPattern).build());

        generateBulkReservationAnomaly(
                AnomalyTestRequest.builder().count(perPattern).build());

//        generateHighPriceAnomaly(
//                AnomalyTestRequest.builder().count(perPattern).build());

        generateSameIpAnomaly(
                AnomalyTestRequest.builder().count(perPattern).build());

        return AnomalyTestResponse.builder()
                .pattern("MIXED")
                .count(request.getCount())
                .message(String.format("혼합 패턴 생성 완료 (정상 %d건 + 이상 %d건)",
                        normalCount, perPattern * 4))
                .build();
    }

    // 헬퍼 메소드
    private List<Ticket> getRandomTickets(int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        try {
            long totalTickets = ticketRepository.count();
            if (totalTickets == 0) {
                log.warn("티켓이 없습니다");
                return new ArrayList<>();
            }

            int fetchSize = Math.min(count, 100);
            int maxPage = Math.max(0, (int) ((totalTickets - 1) / fetchSize));
            int randomPage = maxPage > 0 ? random.nextInt(maxPage + 1) : 0;

            return ticketRepository.findAll(
                    PageRequest.of(randomPage, fetchSize)
            ).getContent();

        } catch (Exception e) {
            log.error("티켓 조회 실패", e);
            return new ArrayList<>();
        }
    }

    private List<User> getRandomUsers(int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        try {
            long totalUsers = userRepository.count();
            if (totalUsers == 0) {
                log.warn("사용자가 없습니다");
                return new ArrayList<>();
            }

            // 안전한 페이지 계산
            int fetchSize = Math.min(count, 100);
            int maxPage = Math.max(0, (int) ((totalUsers - 1) / fetchSize));
            int randomPage = maxPage > 0 ? random.nextInt(maxPage + 1) : 0;

            return userRepository.findAll(
                    PageRequest.of(randomPage, fetchSize)
            ).getContent();

        } catch (Exception e) {
            log.error("사용자 조회 실패", e);
            return new ArrayList<>();
        }
    }
}