package com.ticketing.test.service;

import com.ticketing.domain.reservation.entity.Reservation;
import com.ticketing.domain.reservation.repository.ReservationRepository;
import com.ticketing.domain.ticket.entity.Ticket;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.domain.user.entity.User;
import com.ticketing.domain.user.repository.UserRepository;
import com.ticketing.global.enums.ReservationStatus;
import com.ticketing.global.snowflake.Snowflake;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.InitProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationDataService {

    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final Snowflake snowflake;
    private final Random random = new Random();

    public InitProgress generateReservations(DataInitRequest request, InitProgress progress) {

        if (request.getClearExisting()) {
            reservationRepository.deleteAll();
        }

        long ticketCount = ticketRepository.count();
        long userCount = userRepository.count();

        if (ticketCount == 0) {
            log.error("티켓이 존재하지 않습니다");
            progress.fail();
            return progress;
        }

        if (userCount == 0) {
            log.error("사용자가 존재하지 않습니다");
            progress.fail();
            return progress;
        }

        int threadCount = request.getThreadCount();
        int reservationsPerThread = request.getCount() / threadCount;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int threadId = 0; threadId < threadCount; threadId++) {
                final int id = threadId;
                executorService.submit(() -> {
                    try {
                        insertReservations(reservationsPerThread, request.getBatchSize(), progress);
                    } catch (Exception e) {
                        log.error("스레드 {} 오류", id, e);
                        progress.incrementError(reservationsPerThread);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            progress.complete();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            progress.fail();
            log.error("예약 생성 중단됨", e);
        } finally {
            executorService.shutdown();
        }

        return progress;
    }

    @Transactional
    public void insertReservations(int totalCount, int batchSize, InitProgress progress) {
        int batchCount = totalCount / batchSize;
        long ticketCount = ticketRepository.count();
        long userCount = userRepository.count();

        for (int batch = 0; batch < batchCount; batch++) {
            Instant batchStart = Instant.now();

            List<Reservation> reservations = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                long ticketOffset = random.nextLong(Math.max(1, ticketCount - 1000));
                long userOffset = random.nextLong(Math.max(1, userCount - 10000));

                List<Ticket> tickets = ticketRepository.findAll(PageRequest.of((int) ticketOffset, 1)).getContent();
                List<User> users = userRepository.findAll(PageRequest.of((int) userOffset, 1)).getContent();

                if (tickets.isEmpty() || users.isEmpty()) continue;

                ReservationStatus status = getRandomStatus();
                LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(90));

                Reservation reservation = Reservation.builder()
                        .id(snowflake.nextId())
                        .ticket(tickets.get(0))
                        .user(users.get(0))
                        .status(status)
                        .createdAt(createdAt)
                        .confirmedAt(status == ReservationStatus.CONFIRMED ?
                                createdAt.plusMinutes(random.nextInt(30)) : null)
                        .build();

                reservations.add(reservation);
            }

            if (!reservations.isEmpty()) {
                reservationRepository.saveAll(reservations);
                progress.incrementCompleted(reservations.size());
                progress.addTime(java.time.Duration.between(batchStart, Instant.now()).toMillis());
            }
        }
    }

    private ReservationStatus getRandomStatus() {
        int rand = random.nextInt(100);
        if (rand < 70) return ReservationStatus.CONFIRMED;
        if (rand < 90) return ReservationStatus.PENDING;
        return ReservationStatus.CANCELLED;
    }
}