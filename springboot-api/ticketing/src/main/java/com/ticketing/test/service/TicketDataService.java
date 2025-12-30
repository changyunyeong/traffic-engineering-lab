package com.ticketing.test.service;

import com.ticketing.domain.event.entity.Event;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.domain.ticket.entity.Ticket;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.global.snowflake.Snowflake;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.InitProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketDataService {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final Snowflake snowflake;
    private final Random random = new Random();

    private static final String[] TICKET_TYPES = {
            "VIP석", "R석", "S석", "A석", "B석"
    };

    public InitProgress generateTickets(DataInitRequest request, InitProgress progress) {

        if (request.getClearExisting()) {
            ticketRepository.deleteAll();
        }

        List<Event> events = eventRepository.findAll();
        if (events.isEmpty()) {
            log.error("이벤트가 존재하지 않습니다");
            progress.fail();
            return progress;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(request.getThreadCount());
        CountDownLatch latch = new CountDownLatch(events.size());

        try {
            for (Event event : events) {
                executorService.submit(() -> {
                    try {
                        createTicketsForEvent(event, progress);
                    } catch (Exception e) {
                        log.error("티켓 생성 오류: eventId={}", event.getId(), e);
                        progress.incrementError(5);
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
            log.error("티켓 생성 중단됨", e);
        } finally {
            executorService.shutdown();
        }

        return progress;
    }

    @Transactional
    public void createTicketsForEvent(Event event, InitProgress progress) {
        Instant start = Instant.now();

        List<Ticket> tickets = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Ticket ticket = Ticket.builder()
                    .id(snowflake.nextId())
                    .event(event)
                    .name(TICKET_TYPES[i])
                    .price((long) ((5 - i) * 20000 + random.nextInt(10000)))
                    .stock((long) (50 + i * 50 + random.nextInt(100)))
                    .build();
            tickets.add(ticket);
        }

        ticketRepository.saveAll(tickets);
        progress.incrementCompleted(5);
        progress.addTime(java.time.Duration.between(start, Instant.now()).toMillis());
    }
}